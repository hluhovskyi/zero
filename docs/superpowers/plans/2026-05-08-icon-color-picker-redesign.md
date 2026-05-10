# Icon + Color Picker Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing `IconAndColorPicker` composable into `IconPickerComponent` so the picker shows a search bar, color swatches, and a sectioned icon grid — matching the new "IdentityPicker" design.

**Architecture:** Update `IconPickerViewModel` + `DefaultIconPickerViewModel` to expose sections and color schemes, update `IconPickerViewProvider` to delegate to `IconAndColorPicker`, add `OnColorSelectedHandler` to `IconPickerComponent.Builder`, and wire the color pick in the navigation entry via a new `PickWithoutNavigation` action on `CategoryEditColorUseCase` (so the icon picker can update the category color without triggering back-navigation).

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Flow

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewModel.kt` | Modify | Add `sections`, `colorSchemes`, `selectedColorScheme` to `State`; add `SelectColorScheme` action |
| `zero-core/src/main/java/com/hluhovskyi/zero/icons/DefaultIconPickerViewModel.kt` | Modify | Load color schemes from repo, group icons into sections, handle `SelectColorScheme` |
| `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewProvider.kt` | Modify | Replace grid with `IconAndColorPicker`, convert domain→UI types |
| `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerComponent.kt` | Modify | Add `OnColorSelectedHandler` to Builder and Module |
| `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditColorUseCase.kt` | Modify | Add `PickWithoutNavigation` action (picks color without triggering back-nav) |
| `app/src/main/java/com/hluhovskyi/zero/activity/screens/DefaultCategoryEditColorUseCase.kt` | Modify | Handle `PickWithoutNavigation` — emit state but skip `navigator.back()` |
| `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt` | Modify | Wire `onColorSelectedHandler` in `iconPickerNavigationEntry` |
| `app/src/main/java/com/hluhovskyi/zero/colors/PredefinedMaterialColorRepository.kt` | Modify | Add 5 missing color schemes to match design palette (green, purple, teal, pink, grey) |

---

## Task 1: Expand `CategoryEditColorUseCase` with a navigation-free pick action

The icon picker needs to update the category color without closing itself (the user picks color then icon — two separate taps). The existing `Pick` action triggers `navigator.back()`. A new `PickWithoutNavigation` action emits the same state but skips back navigation.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditColorUseCase.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/DefaultCategoryEditColorUseCase.kt`

- [ ] **Step 1: Add `PickWithoutNavigation` to the interface**

Replace:
```kotlin
sealed interface Action {
    object Request : Action
    data class Pick(val color: Color) : Action
}
```
With:
```kotlin
sealed interface Action {
    object Request : Action
    data class Pick(val color: Color) : Action
    data class PickWithoutNavigation(val color: Color) : Action
}
```

- [ ] **Step 2: Handle `PickWithoutNavigation` in `DefaultCategoryEditColorUseCase`**

The implementation currently uses one `pickAction` shared flow for `Pick`, and the state observes only when the `Color.Picker` route is current. We need a second shared flow `inlinePickAction` for `PickWithoutNavigation`, which emits regardless of the current route.

Replace the entire `DefaultCategoryEditColorUseCase` with:

```kotlin
package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.observeArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.categories.edit.CategoryEditColorUseCase
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DefaultCategoryEditColorUseCase"

internal class DefaultCategoryEditColorUseCase(
    private val navigator: Navigator,
    private val requestIdGenerator: IdGenerator,
    inputLogger: Logger,
) : CategoryEditColorUseCase {

    private val logger = inputLogger.withTag(TAG)

    private var requestId = AtomicReference<Id>(Id.Unknown)
    private val pickAction = MutableSharedFlow<CategoryEditColorUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val inlinePickAction = MutableSharedFlow<CategoryEditColorUseCase.Action.PickWithoutNavigation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: CategoryEditColorUseCase.Action) {
        when (action) {
            is CategoryEditColorUseCase.Action.Request -> {
                val id = requestIdGenerator()
                requestId.set(id)
                logger.d("perform, requestId=$requestId")
                navigator.navigateTo(
                    destination = Destinations.Color.Picker,
                    Destinations.Color.Picker.RequestId.withValue(id),
                )
            }
            is CategoryEditColorUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
            is CategoryEditColorUseCase.Action.PickWithoutNavigation -> {
                inlinePickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<CategoryEditColorUseCase.State> = merge(
        // Picks from the dedicated Color.Picker bottom sheet → navigate back after pick
        navigator.observeArgumentValue(
            destination = Destinations.Color.Picker,
            argument = Destinations.Color.Picker.RequestId,
        )
            .flatMapLatest { requestId ->
                pickAction.map { pick ->
                    logger.d("state (picker), requestId=$requestId, pick=$pick")
                    requestId to pick.color
                }
            }
            .filter { (requestId, _) -> requestId.value == this.requestId.get() }
            .mapNotNull { (_, color) -> CategoryEditColorUseCase.State.Picked(color) }
            .onEach { navigator.back() },

        // Inline picks from Icon.Picker (no back navigation — user still needs to pick an icon)
        inlinePickAction
            .filter { this.requestId.get() is Id.Known }
            .mapNotNull { action -> CategoryEditColorUseCase.State.Picked(action.color) },
    )
}
```

- [ ] **Step 3: Build to check no compilation errors**

```bash
./gradlew :zero-core:compileDebugKotlin :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditColorUseCase.kt \
        app/src/main/java/com/hluhovskyi/zero/activity/screens/DefaultCategoryEditColorUseCase.kt
git commit -m "feat: add PickWithoutNavigation to CategoryEditColorUseCase for inline color picks"
```

---

## Task 2: Add missing color schemes to `PredefinedMaterialColorRepository`

The design palette has 8 colors. The repository only has 3 (blue, red, orange). Add green, purple, teal, pink, and grey.

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/colors/PredefinedMaterialColorRepository.kt`

- [ ] **Step 1: Update `PredefinedMaterialColorRepository` with the full design palette**

Replace the file contents with:

```kotlin
package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow

internal class PredefinedMaterialColorRepository : ColorRepository {

    private val colors = mapOf(
        color(
            id = ColorRepository.unknownCategoryColorId().value,
            hex = 0xFF999999UL,
        ),
        color(id = "blue",   hex = 0xFF1E88E5UL),
        color(id = "red",    hex = 0xFFE53935UL),
        color(id = "orange", hex = 0xFFFF9800UL),
        color(id = "green",  hex = 0xFF2E7D32UL),
        color(id = "purple", hex = 0xFF6A1B9AUL),
        color(id = "teal",   hex = 0xFF00695CUL),
        color(id = "pink",   hex = 0xFFAD1457UL),
        color(id = "grey",   hex = 0xFF424242UL),
    )

    private val schemes = mapOf(
        Id("blue") to ColorScheme(
            primary = Color(id = Id("blue_primary"), value = ColorValue(0xFF1565C0UL)),
            background = Color(id = Id("blue_background"), value = ColorValue(0xFFE3F2FDUL)),
        ),
        Id("red") to ColorScheme(
            primary = Color(id = Id("red_primary"), value = ColorValue(0xFFB71C1CUL)),
            background = Color(id = Id("red_background"), value = ColorValue(0xFFFFEBEEUL)),
        ),
        Id("orange") to ColorScheme(
            primary = Color(id = Id("orange_primary"), value = ColorValue(0xFFE65100UL)),
            background = Color(id = Id("orange_background"), value = ColorValue(0xFFFFF3E0UL)),
        ),
        Id("green") to ColorScheme(
            primary = Color(id = Id("green_primary"), value = ColorValue(0xFF1B5E20UL)),
            background = Color(id = Id("green_background"), value = ColorValue(0xFFE8F5E9UL)),
        ),
        Id("purple") to ColorScheme(
            primary = Color(id = Id("purple_primary"), value = ColorValue(0xFF4A148CUL)),
            background = Color(id = Id("purple_background"), value = ColorValue(0xFFF3E5F5UL)),
        ),
        Id("teal") to ColorScheme(
            primary = Color(id = Id("teal_primary"), value = ColorValue(0xFF006064UL)),
            background = Color(id = Id("teal_background"), value = ColorValue(0xFFE0F7FAUL)),
        ),
        Id("pink") to ColorScheme(
            primary = Color(id = Id("pink_primary"), value = ColorValue(0xFFAD1457UL)),
            background = Color(id = Id("pink_background"), value = ColorValue(0xFFFCE4ECUL)),
        ),
        Id("grey") to ColorScheme(
            primary = Color(id = Id("grey_primary"), value = ColorValue(0xFF424242UL)),
            background = Color(id = Id("grey_background"), value = ColorValue(0xFFF5F5F5UL)),
        ),
    )

    private val fallbackScheme = ColorScheme(
        primary = Color(id = Id("fallback_primary"), value = ColorValue(0xFF424242UL)),
        background = Color(id = Id("fallback_background"), value = ColorValue(0xFFF5F5F5UL)),
    )

    override fun <T> query(criteria: ColorRepository.Criteria<T>): Flow<T> = when (criteria) {
        is ColorRepository.Criteria.All -> castingFlowOf(colors.values.toList())
        is ColorRepository.Criteria.ById -> castingFlowOfNonNull(colors[criteria.id])
    }

    override fun schemeFor(colorId: Id.Known): ColorScheme = schemes[colorId] ?: fallbackScheme

    private fun color(id: String, hex: ULong) = Id(id).let { knownId ->
        knownId to Color(
            id = knownId,
            value = ColorValue(hex = hex),
        )
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/colors/PredefinedMaterialColorRepository.kt
git commit -m "feat: add full 8-color design palette to PredefinedMaterialColorRepository"
```

---

## Task 3: Update `IconPickerViewModel` interface

The current `State` has a flat `icons: List<Icon>`. Replace with `sections: List<IconPickerSection>` and add color scheme fields. Add the `SelectColorScheme` action.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewModel.kt`

- [ ] **Step 1: Rewrite the interface**

```kotlin
package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel

interface IconPickerViewModel : AttachableActionStateModel<IconPickerViewModel.Action, IconPickerViewModel.State> {

    sealed interface Action {
        data class SelectIcon(val icon: Icon) : Action
        data class SelectColorScheme(val colorScheme: ColorScheme) : Action
    }

    data class State(
        val sections: List<IconPickerSection> = emptyList(),
        val colorSchemes: List<ColorScheme> = emptyList(),
        val selectedIcon: Icon? = null,
        val selectedColorScheme: ColorScheme? = null,
    )
}
```

- [ ] **Step 2: Build zero-core (will fail — `DefaultIconPickerViewModel` not updated yet, that's fine)**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -30
```
Expected: FAIL (unresolved reference `icons`, `colorScheme` in `DefaultIconPickerViewModel`)

- [ ] **Step 3: Commit the interface change**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewModel.kt
git commit -m "feat: update IconPickerViewModel interface with sections and color schemes"
```

---

## Task 4: Update `DefaultIconPickerViewModel` to load sections and colors

Group the flat icon list into `IconPickerSection` lists using static section definitions, load the full color palette from `ColorRepository`, and handle the new `SelectColorScheme` action.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/icons/DefaultIconPickerViewModel.kt`

- [ ] **Step 1: Rewrite `DefaultIconPickerViewModel`**

```kotlin
package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.OnColorSelectedHandler
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

// Maps icon id values to their section title. Icons not listed end up in "Other".
private val SECTION_DEFINITIONS: List<Pair<String, List<String>>> = listOf(
    "Money & Banking" to listOf("cash", "bank", "credit_card"),
    "Food & Drink"    to listOf("flowers", "grocery", "fastfood"),
    "Travel"          to listOf("car", "car_repair", "beach"),
    "Shopping"        to listOf("diamond"),
    "Entertainment"   to listOf("game_controller", "movie"),
    "Education"       to listOf("book"),
)

internal class DefaultIconPickerViewModel(
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onIconSelectedHandler: OnIconSelectedHandler,
    private val onColorSelectedHandler: OnColorSelectedHandler,
    private val colorId: Id = Id.Unknown,
    private val selectedIconId: Id = Id.Unknown,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : IconPickerViewModel {

    // Internal mapping ColorScheme → Color so we can call onColorSelectedHandler with the
    // domain Color object when the user taps a swatch.
    private var colorSchemeToColor: Map<ColorScheme, com.hluhovskyi.zero.colors.Color> = emptyMap()

    private val mutableState = MutableStateFlow(IconPickerViewModel.State())
    override val state: Flow<IconPickerViewModel.State> = mutableState

    override fun perform(action: IconPickerViewModel.Action) {
        when (action) {
            is IconPickerViewModel.Action.SelectIcon -> {
                onIconSelectedHandler.onIconSelected(action.icon)
            }
            is IconPickerViewModel.Action.SelectColorScheme -> {
                mutableState.update { it.copy(selectedColorScheme = action.colorScheme) }
                colorSchemeToColor[action.colorScheme]?.let { color ->
                    onColorSelectedHandler.onColorSelected(color)
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch { loadColors() }
        coroutineScope.launch { loadIcons() }
    }

    private suspend fun loadColors() {
        colorRepository.query(ColorRepository.Criteria.All())
            .collectLatest { colors ->
                val mapping = colors.associate { color ->
                    colorRepository.schemeFor(color.id) to color
                }
                colorSchemeToColor = mapping

                val selectedScheme = (colorId as? Id.Known)
                    ?.let { colorRepository.schemeFor(it) }
                    ?: ColorScheme.Grey

                mutableState.update { state ->
                    state.copy(
                        colorSchemes = mapping.keys.toList(),
                        selectedColorScheme = selectedScheme,
                    )
                }
            }
    }

    private suspend fun loadIcons() {
        iconRepository.query(IconRepository.Criteria.All())
            .collectLatest { icons ->
                val sections = buildSections(icons)
                val selectedIcon = (selectedIconId as? Id.Known)
                    ?.let { id -> icons.find { it.id == id } }

                mutableState.update { state ->
                    state.copy(
                        sections = sections,
                        selectedIcon = selectedIcon,
                    )
                }
            }
    }

    private fun buildSections(icons: List<Icon>): List<IconPickerSection> {
        val iconById = icons.associateBy { it.id.value }
        val sections = SECTION_DEFINITIONS.mapNotNull { (title, ids) ->
            val sectionIcons = ids.mapNotNull { iconById[it] }
            if (sectionIcons.isEmpty()) null else IconPickerSection(title, sectionIcons)
        }
        val assignedIds = SECTION_DEFINITIONS.flatMap { (_, ids) -> ids }.toSet()
        val other = icons.filter { it.id.value !in assignedIds }
        return if (other.isEmpty()) sections else sections + IconPickerSection("Other", other)
    }
}
```

- [ ] **Step 2: Build zero-core**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -30
```
Expected: FAIL — `IconPickerViewProvider` still uses old `state.icons`, `DefaultIconPickerViewModel` constructor changed in `IconPickerComponent`. Those are fixed in the next tasks.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/icons/DefaultIconPickerViewModel.kt
git commit -m "feat: load sections and color schemes in DefaultIconPickerViewModel"
```

---

## Task 5: Update `IconPickerViewProvider` to use `IconAndColorPicker`

Replace the manual `LazyVerticalGrid` with the existing `IconAndColorPicker` composable. The domain `ColorScheme` → `UiColorScheme` conversion happens here, and reverse-mapping uses equality on `UiColorScheme` (it's a data class).

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewProvider.kt`

- [ ] **Step 1: Rewrite the view provider**

```kotlin
package com.hluhovskyi.zero.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.common.toUi

internal class IconPickerViewProvider(
    private val viewModel: IconPickerViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        IconPickerView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun IconPickerView(
    viewModel: IconPickerViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = IconPickerViewModel.State())

    // Pre-compute UI color scheme list once per state recomposition to enable stable equality checks.
    val uiColorSchemes = state.colorSchemes.map { it.toUi() }
    val uiSelectedColorScheme = state.selectedColorScheme?.toUi()

    IconAndColorPicker(
        sections = state.sections,
        colorSchemes = uiColorSchemes,
        selectedIcon = state.selectedIcon,
        selectedColorScheme = uiSelectedColorScheme,
        imageLoader = imageLoader,
        onIconSelected = { icon ->
            viewModel.perform(IconPickerViewModel.Action.SelectIcon(icon))
        },
        onColorSchemeSelected = { uiScheme ->
            // Reverse-map UiColorScheme back to domain ColorScheme via index position
            // (both lists are built from the same source in the same order).
            val index = uiColorSchemes.indexOf(uiScheme)
            if (index != -1) {
                state.colorSchemes.getOrNull(index)?.let { domainScheme ->
                    viewModel.perform(IconPickerViewModel.Action.SelectColorScheme(domainScheme))
                }
            }
        },
    )
}
```

- [ ] **Step 2: Build zero-core**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -30
```
Expected: FAIL — `IconPickerComponent` still passes old constructor to `DefaultIconPickerViewModel`.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewProvider.kt
git commit -m "feat: wire IconAndColorPicker composable into IconPickerViewProvider"
```

---

## Task 6: Update `IconPickerComponent` to expose `OnColorSelectedHandler`

Add `onColorSelectedHandler` to the Dagger component's builder and module so callers can wire it.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerComponent.kt`

- [ ] **Step 1: Add `OnColorSelectedHandler` qualifier and builder binding**

Note: `OnColorSelectedHandler` is in `com.hluhovskyi.zero.colors`. Add a `@Qualifier` to distinguish it from `OnIconSelectedHandler` in Dagger.

```kotlin
package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.OnColorSelectedHandler
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class ColorId

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class SelectedIconId

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class IconPickerScope

private const val TAG = "IconPickerComponent"

@IconPickerScope
@dagger.Component(
    dependencies = [IconPickerComponent.Dependencies::class],
    modules = [IconPickerComponent.Module::class],
)
abstract class IconPickerComponent : AttachableViewComponent {

    internal abstract val viewModel: IconPickerViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerIconPickerComponent.builder()
            .dependencies(dependencies)
            .onIconSelectedHandler(OnIconSelectedHandler.Noop)
            .onColorSelectedHandler(OnColorSelectedHandler.Noop)
            .colorId(Id.Unknown)
            .selectedIconId(Id.Unknown)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<IconPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onIconSelectedHandler(handler: OnIconSelectedHandler): Builder

        @BindsInstance
        fun onColorSelectedHandler(handler: OnColorSelectedHandler): Builder

        @BindsInstance
        fun colorId(@ColorId colorId: Id): Builder

        @BindsInstance
        fun selectedIconId(@SelectedIconId selectedIconId: Id): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @IconPickerScope
        fun viewModel(
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            onIconSelectedHandler: OnIconSelectedHandler,
            onColorSelectedHandler: OnColorSelectedHandler,
            @ColorId colorId: Id,
            @SelectedIconId selectedIconId: Id,
        ): IconPickerViewModel = DefaultIconPickerViewModel(
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            onIconSelectedHandler = onIconSelectedHandler,
            onColorSelectedHandler = onColorSelectedHandler,
            colorId = colorId,
            selectedIconId = selectedIconId,
        )

        @Provides
        @IconPickerScope
        fun viewProvider(
            viewModel: IconPickerViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = IconPickerViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
```

- [ ] **Step 2: Build zero-core**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL (zero-core should compile cleanly now)

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerComponent.kt
git commit -m "feat: add OnColorSelectedHandler to IconPickerComponent builder"
```

---

## Task 7: Wire `onColorSelectedHandler` in the navigation entry

In `MainActivityScreenComponent`, the `iconPickerNavigationEntry` already dispatches icon picks to both account and category use cases. Add color dispatch to `categoryEditColorUseCase` using the new `PickWithoutNavigation` action (no back navigation from the icon picker when color changes).

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Update `iconPickerNavigationEntry` provider**

Find the `iconPickerNavigationEntry` function (around line 547 in `MainActivityScreenComponent.kt`) and add `categoryEditColorUseCase` as a parameter and `onColorSelectedHandler` to the builder:

Replace:
```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun iconPickerNavigationEntry(
    componentBuilder: IconPickerComponent.Builder,
    navigatorScope: NavigatorScope,
    categoryEditIconUseCase: CategoryEditIconUseCase,
    accountEditIconUseCase: AccountEditIconUseCase,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(
    destination = Destinations.Icon.Picker,
    displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
) {
    componentBuilder
        .colorId(arguments.getValue(Destinations.Icon.Picker.ColorId))
        .selectedIconId(arguments.getValue(Destinations.Icon.Picker.SelectedIconId))
        .onIconSelectedHandler { icon ->
            accountEditIconUseCase.perform(
                AccountEditIconUseCase.Action.Pick(
                    icon = AccountEditIconUseCase.Icon(
                        id = icon.id,
                        image = icon.image,
                    ),
                ),
            )
            categoryEditIconUseCase.perform(
                CategoryEditIconUseCase.Action.Pick(
                    icon = CategoryEditIconUseCase.Icon(
                        id = icon.id,
                        image = icon.image,
                    ),
                ),
            )
        }
        .logging(logger)
}
```

With:
```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun iconPickerNavigationEntry(
    componentBuilder: IconPickerComponent.Builder,
    navigatorScope: NavigatorScope,
    categoryEditIconUseCase: CategoryEditIconUseCase,
    categoryEditColorUseCase: CategoryEditColorUseCase,
    accountEditIconUseCase: AccountEditIconUseCase,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(
    destination = Destinations.Icon.Picker,
    displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
) {
    componentBuilder
        .colorId(arguments.getValue(Destinations.Icon.Picker.ColorId))
        .selectedIconId(arguments.getValue(Destinations.Icon.Picker.SelectedIconId))
        .onIconSelectedHandler { icon ->
            accountEditIconUseCase.perform(
                AccountEditIconUseCase.Action.Pick(
                    icon = AccountEditIconUseCase.Icon(
                        id = icon.id,
                        image = icon.image,
                    ),
                ),
            )
            categoryEditIconUseCase.perform(
                CategoryEditIconUseCase.Action.Pick(
                    icon = CategoryEditIconUseCase.Icon(
                        id = icon.id,
                        image = icon.image,
                    ),
                ),
            )
        }
        .onColorSelectedHandler { color ->
            categoryEditColorUseCase.perform(
                CategoryEditColorUseCase.Action.PickWithoutNavigation(
                    color = CategoryEditColorUseCase.Color(
                        id = color.id,
                        color = color.value,
                    ),
                ),
            )
        }
        .logging(logger)
}
```

- [ ] **Step 2: Build the full project**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build the debug APK to catch Dagger wiring issues**

```bash
./gradlew assembleDebug 2>&1 | tail -40
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: wire onColorSelectedHandler in iconPickerNavigationEntry"
```

---

## Task 8: Install and validate with android-ui-inspector

Build, install, open the app, navigate to the icon picker (via category edit or account edit), and visually verify the new design.

**Files:** None — validation only.

- [ ] **Step 1: Build and install debug APK**

```bash
./gradlew installDebug
```
Expected: BUILD SUCCESSFUL, app installed on connected device

- [ ] **Step 2: Run android-ui-inspector to verify layout**

Use the `zero-project:android-ui-inspector` skill to capture the icon picker screen and verify:
- Search bar is visible at the top
- 8 color swatches appear in a row below the search bar
- Icons appear in a 5-column sectioned grid with section titles ("Money & Banking", "Food & Drink", etc.)
- Tapping a color swatch updates the selected icon preview color
- Tapping an icon closes the picker and applies the icon in the category/account edit screen

- [ ] **Step 3: If layout issues found, fix and rebuild**

Common issues to check:
- Grid cells not rendering (check `IconAndColorPicker` imports are all resolved)
- Color swatches showing wrong colors (verify `PredefinedMaterialColorRepository` hex values)
- Section titles missing (verify icon IDs in `SECTION_DEFINITIONS` match `KnownIconIds`)

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Search bar at top of picker
- ✅ Color swatches row (8 colors matching design palette)
- ✅ Sectioned icon grid (5 columns, section headers)
- ✅ Selected icon shows colored bg + border
- ✅ Selected color swatch shows ring
- ✅ Live commit — color change immediately updates icon preview and emits to category edit
- ✅ Icon selection closes picker and persists
- ✅ Color selection updates category without closing picker

**Type consistency check:**
- `IconPickerViewModel.Action.SelectColorScheme(colorScheme: ColorScheme)` — used in Task 3, handled in Task 4, dispatched in Task 5 ✅
- `DefaultIconPickerViewModel.colorSchemeToColor: Map<ColorScheme, Color>` — ColorScheme from `colors` pkg, Color from `colors` pkg ✅
- `OnColorSelectedHandler.onColorSelected(color: Color)` — `colors.Color`, same type used throughout ✅
- `CategoryEditColorUseCase.Action.PickWithoutNavigation(color: Color)` — `CategoryEditColorUseCase.Color` inner class, used correctly in Task 7 ✅
- `IconPickerSection(title, icons)` — `zero-core` data class, populated in `buildSections()` in Task 4 ✅

**Placeholder scan:** No TBD or TODO patterns found in plan. All code steps are complete.
