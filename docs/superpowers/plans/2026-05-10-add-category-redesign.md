# Add Category Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the add/edit category screen from a full-screen form to a compact bottom sheet with an inline icon+color picker overlay.

**Architecture:** The screen becomes a `PartiallyVisible.BottomSheet` navigation destination. The icon tile tap reveals an `IconAndColorPicker` overlay directly within the sheet (no navigation away). Icon sections and color schemes are loaded by `DefaultCategoryEditViewModel` and exposed via `State`.

**Tech Stack:** Jetpack Compose, Dagger, existing `IconAndColorPicker` composable, `ModalHeader`, `DragHandle`, `FormCard` pattern from `AccountEditViewProvider`.

**Reference design:** `AddCategorySheet` + `IdentityPicker` in `/tmp/design-LD0UEO2NiRF9PIjnGcrDww/zero-design-system/project/ui_kits/zero/index.html`

---

## File Map

| Action | File |
|--------|------|
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt` |
| Create | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/OnDiscardHandler.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditComponent.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt` |

---

### Task 1: Expand CategoryEditViewModel interface

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconPickerSection

interface CategoryEditViewModel : AttachableActionStateModel<CategoryEditViewModel.Action, CategoryEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        object TogglePicker : Action
        data class PickIcon(val icon: Icon) : Action
        data class PickColorScheme(val colorScheme: ColorScheme) : Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val icon: Image = Image.empty(),
        val colorScheme: ColorScheme = ColorScheme(
            swatch = Color.empty(),
            primary = Color.empty(),
            background = Color.empty(),
        ),
        val pickerVisible: Boolean = false,
        val iconSections: List<IconPickerSection> = emptyList(),
        val colorSchemes: List<ColorScheme> = emptyList(),
        val selectedIcon: Icon? = null,
    )
}
```

- [ ] **Step 2: Compile to catch downstream breakage**

```
./gradlew :zero-core:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -30
```

Expected: errors in `DefaultCategoryEditViewModel` and `CategoriesEditViewProvider` about removed actions — fix in next tasks.

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt
git commit -m "refactor: expand CategoryEditViewModel with inline picker state"
```

---

### Task 2: Create OnDiscardHandler

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/OnDiscardHandler.kt`

- [ ] **Step 1: Create the file** (mirrors `accounts.edit.OnCloseHandler`)

```kotlin
package com.hluhovskyi.zero.categories.edit

fun interface OnDiscardHandler {

    fun onDiscard()

    object Noop : OnDiscardHandler {
        override fun onDiscard() = Unit
    }
}
```

- [ ] **Step 2: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/OnDiscardHandler.kt
git commit -m "feat: add OnDiscardHandler for category edit close button"
```

---

### Task 3: Update DefaultCategoryEditViewModel

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt`

- [ ] **Step 1: Replace the file**

Remove `categoryEditIconUseCase`/`categoryEditColorUseCase` from constructor and subscriptions. Add icon section + color scheme loading. Handle new actions.

```kotlin
package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconCategory
import com.hluhovskyi.zero.icons.IconPickerSection
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryEditViewModel(
    private val categoryId: Id,
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onCategorySavedHandler: OnCategorySavedHandler,
    private val ioCoroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : CategoryEditViewModel {

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<CategoryEditViewModel.State> = mutableState.map { state ->
        CategoryEditViewModel.State(
            name = state.name,
            icon = state.icon,
            colorScheme = state.colorScheme,
            pickerVisible = state.pickerVisible,
            iconSections = state.iconSections,
            colorSchemes = state.colorSchemes,
            selectedIcon = state.iconSections.flatMap { it.icons }.find { it.id == state.iconId },
        )
    }

    override fun perform(action: CategoryEditViewModel.Action) {
        when (action) {
            is CategoryEditViewModel.Action.ChangeName ->
                mutableState.update { it.copy(name = action.name) }
            is CategoryEditViewModel.Action.TogglePicker ->
                mutableState.update { it.copy(pickerVisible = !it.pickerVisible) }
            is CategoryEditViewModel.Action.PickIcon ->
                mutableState.update { it.copy(iconId = action.icon.id, icon = action.icon.image) }
            is CategoryEditViewModel.Action.PickColorScheme ->
                mutableState.update { it.copy(colorId = action.colorScheme.swatch.id, colorScheme = action.colorScheme) }
            is CategoryEditViewModel.Action.Save -> ioCoroutineScope.launch {
                val state = mutableState.value
                categoryRepository.insert(
                    CategoryRepository.CategoryInsert(
                        id = categoryId,
                        parentCategoryId = Id.Unknown,
                        name = state.name,
                        iconId = state.iconId,
                        colorId = state.colorId,
                    ),
                )
                launch(context = Dispatchers.Main) { onCategorySavedHandler.onSaved() }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        ioCoroutineScope.launch {
            launch { loadIconSections() }
            launch { loadColorSchemes() }
            if (categoryId is Id.Known) {
                launch { loadExistingCategory() }
            } else {
                launch { loadDefaults() }
            }
        }
    }

    private suspend fun loadIconSections() {
        iconRepository.query(IconRepository.Criteria.All())
            .collectLatest { icons ->
                val sections = icons
                    .groupBy { it.category }
                    .filter { (category, _) -> category != IconCategory.system() }
                    .map { (category, categoryIcons) -> IconPickerSection(category, categoryIcons) }
                mutableState.update { it.copy(iconSections = sections) }
            }
    }

    private suspend fun loadColorSchemes() {
        colorRepository.query(ColorRepository.Criteria.AllSchemes())
            .collectLatest { schemes ->
                mutableState.update { it.copy(colorSchemes = schemes) }
            }
    }

    private suspend fun loadExistingCategory() {
        val category = categoryRepository.query(
            CategoryRepository.Criteria.ById(categoryId as Id.Known)
        ).firstOrNull() ?: return
        val colorId = (category.colorId as? Id.Known) ?: ColorRepository.unknownCategoryColorId()
        val iconId = (category.iconId as? Id.Known) ?: IconRepository.unknownCategoryIconId()
        combine(
            colorRepository.query(ColorRepository.Criteria.ById(colorId)),
            iconRepository.query(IconRepository.Criteria.ById(iconId)),
        ) { color, icon -> color to icon }
            .firstOrNull()?.let { (color, icon) ->
                mutableState.update { state ->
                    state.copy(
                        name = category.name,
                        iconId = icon.id,
                        icon = icon.image,
                        colorId = color.id,
                        colorScheme = colorRepository.schemeFor(color.id),
                    )
                }
            }
    }

    private suspend fun loadDefaults() {
        launch {
            iconRepository.query(IconRepository.Criteria.ById(IconRepository.unknownCategoryIconId()))
                .firstOrNull()?.let { icon ->
                    mutableState.update { it.copy(iconId = icon.id, icon = icon.image) }
                }
        }
        launch {
            colorRepository.query(ColorRepository.Criteria.ById(ColorRepository.unknownCategoryColorId()))
                .firstOrNull()?.let { color ->
                    mutableState.update { it.copy(colorId = color.id, colorScheme = colorRepository.schemeFor(color.id)) }
                }
        }
    }

    private data class CompositeState(
        val name: String = "",
        val iconId: Id = Id.Unknown,
        val icon: Image = Image.empty(),
        val colorId: Id = Id.Unknown,
        val colorScheme: ColorScheme = ColorScheme(
            swatch = Color.empty(),
            primary = Color.empty(),
            background = Color.empty(),
        ),
        val pickerVisible: Boolean = false,
        val iconSections: List<IconPickerSection> = emptyList(),
        val colorSchemes: List<ColorScheme> = emptyList(),
    )
}
```

- [ ] **Step 2: Compile**

```
./gradlew :zero-core:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: errors in `CategoryEditComponent` (constructor mismatch) — fixed in Task 4.

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt
git commit -m "feat: inline icon/color picker in DefaultCategoryEditViewModel"
```

---

### Task 4: Update CategoryEditComponent

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditComponent.kt`

- [ ] **Step 1: Remove use-case builder params; add OnDiscardHandler; update Module**

Replace the file with:

```kotlin
package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.icons.IconRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditId

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditScope

private const val TAG = "CategoryEditComponent"

@CategoryEditScope
@dagger.Component(
    dependencies = [CategoryEditComponent.Dependencies::class],
    modules = [CategoryEditComponent.Module::class],
)
abstract class CategoryEditComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryEditViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoryRepository: CategoryRepository
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCategoryEditComponent.builder()
            .dependencies(dependencies)
            .onCategorySavedHandler(OnCategorySavedHandler.Noop)
            .onDiscardHandler(OnDiscardHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryId(@CategoryEditId categoryId: Id): Builder

        @BindsInstance
        fun onCategorySavedHandler(handler: OnCategorySavedHandler): Builder

        @BindsInstance
        fun onDiscardHandler(handler: OnDiscardHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryEditScope
        fun viewModel(
            @CategoryEditId categoryId: Id,
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            onCategorySavedHandler: OnCategorySavedHandler,
        ): CategoryEditViewModel = DefaultCategoryEditViewModel(
            categoryId = categoryId,
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            onCategorySavedHandler = onCategorySavedHandler,
        )

        @Provides
        @CategoryEditScope
        fun viewProvider(
            viewModel: CategoryEditViewModel,
            onDiscardHandler: OnDiscardHandler,
            imageLoader: ImageLoader,
        ): ViewProvider = CategoriesEditViewProvider(
            viewModel = viewModel,
            onDiscard = onDiscardHandler,
            imageLoader = imageLoader,
        )
    }
}
```

- [ ] **Step 2: Compile**

```
./gradlew :zero-core:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditComponent.kt
git commit -m "refactor: remove use-case deps from CategoryEditComponent; add OnDiscardHandler"
```

---

### Task 5: Redesign CategoriesEditViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`

The new layout (per design):
- `DragHandle` at the top
- `ModalHeader(title = "Add Category", onClose = { onDiscard.onDiscard() })`
- `Row(height = IntrinsicSize.Min)`: `IconTile(64dp, colored bg, icon + arrow-down)` + `FormCard(name input)`  
- `SaveButton` (full-width, `PrimaryContainer` bg, rounded 16dp, white bold text)
- Overlay `Box` (scrim + picker sheet) when `state.pickerVisible`; picker sheet uses `IconAndColorPicker`

- [ ] **Step 1: Replace the file**

```kotlin
package com.hluhovskyi.zero.categories.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.icons.IconAndColorPicker
import com.hluhovskyi.zero.ui.DragHandle
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class CategoriesEditViewProvider(
    private val viewModel: CategoryEditViewModel,
    private val onDiscard: OnDiscardHandler,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryEditView(
            viewModel = viewModel,
            onDiscard = onDiscard,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun CategoryEditView(
    viewModel: CategoryEditViewModel,
    onDiscard: OnDiscardHandler,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryEditViewModel.State())

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            DragHandle()
            ModalHeader(
                title = "Add Category",
                onClose = { onDiscard.onDiscard() },
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategoryIconTile(
                        modifier = Modifier.fillMaxHeight(),
                        colorScheme = state.colorScheme.toUi(),
                        imageLoader = imageLoader,
                        icon = state.icon,
                        onClick = { viewModel.perform(CategoryEditViewModel.Action.TogglePicker) },
                    )
                    NameFormCard(
                        modifier = Modifier.weight(1f),
                        value = state.name,
                        onValueChange = { viewModel.perform(CategoryEditViewModel.Action.ChangeName(it)) },
                    )
                }
                SaveButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 32.dp),
                    onClick = { viewModel.perform(CategoryEditViewModel.Action.Save) },
                )
            }
        }

        if (state.pickerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { viewModel.perform(CategoryEditViewModel.Action.TogglePicker) },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.78f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Surface),
                ) {
                    Column {
                        DragHandle()
                        IconAndColorPicker(
                            sections = state.iconSections,
                            colorSchemes = state.colorSchemes,
                            selectedIcon = state.selectedIcon,
                            selectedColorScheme = state.colorScheme.takeIf { it.swatch.id.value.isNotEmpty() },
                            imageLoader = imageLoader,
                            onIconSelected = { icon ->
                                viewModel.perform(CategoryEditViewModel.Action.PickIcon(icon))
                            },
                            onColorSchemeSelected = { scheme ->
                                viewModel.perform(CategoryEditViewModel.Action.PickColorScheme(scheme))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryIconTile(
    modifier: Modifier = Modifier,
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    icon: com.hluhovskyi.zero.common.Image,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .background(colorScheme.background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        imageLoader.View(
            image = icon,
            modifier = Modifier
                .align(Alignment.Center)
                .size(26.dp),
            tint = colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(14.dp),
            tint = colorScheme.primary,
        )
    }
}

@Composable
private fun NameFormCard(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "CATEGORY NAME",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "e.g. Groceries",
                        fontSize = 16.sp,
                        color = OnSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun SaveButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(PrimaryContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Save Category",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 2: Compile**

```
./gradlew :zero-core:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt
git commit -m "feat: redesign add category screen — compact bottom sheet with inline picker"
```

---

### Task 6: Update navigation wiring in MainActivityScreenComponent

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Update `categoryEditNavigationEntry`**

Find (lines ~366-378):
```kotlin
fun categoryEditNavigationEntry(
    componentBuilder: CategoryEditComponent.Builder,
    navigatorScope: NavigatorScope,
    categoryEditIconUseCase: CategoryEditIconUseCase,
    categoryEditColorUseCase: CategoryEditColorUseCase,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Edit) {
    componentBuilder
        .categoryId(Id.Unknown)
        .categoryEditIconUseCase(categoryEditIconUseCase)
        .categoryEditColorUseCase(categoryEditColorUseCase)
        .onCategorySavedHandler { navigator.back() }
        .logging(logger)
}
```

Replace with:
```kotlin
fun categoryEditNavigationEntry(
    componentBuilder: CategoryEditComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(
    destination = Destinations.Category.Edit,
    displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
) {
    componentBuilder
        .categoryId(Id.Unknown)
        .onCategorySavedHandler { navigator.back() }
        .onDiscardHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 2: Update `categoryEditItemNavigationEntry`**

Find (lines ~383-396):
```kotlin
fun categoryEditItemNavigationEntry(
    componentBuilder: CategoryEditComponent.Builder,
    navigatorScope: NavigatorScope,
    categoryEditIconUseCase: CategoryEditIconUseCase,
    categoryEditColorUseCase: CategoryEditColorUseCase,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Item.Edit) {
    componentBuilder
        .categoryId(arguments.getValue(Destinations.Category.Item.CategoryId))
        .categoryEditIconUseCase(categoryEditIconUseCase)
        .categoryEditColorUseCase(categoryEditColorUseCase)
        .onCategorySavedHandler { navigator.back() }
        .logging(logger)
}
```

Replace with:
```kotlin
fun categoryEditItemNavigationEntry(
    componentBuilder: CategoryEditComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(
    destination = Destinations.Category.Item.Edit,
    displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
) {
    componentBuilder
        .categoryId(arguments.getValue(Destinations.Category.Item.CategoryId))
        .onCategorySavedHandler { navigator.back() }
        .onDiscardHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 3: Remove now-unused `categoryEditIconUseCase` and `categoryEditColorUseCase` provider methods if nothing else uses them**

Check: `./gradlew :app:compileDebugKotlin 2>&1 | grep -i "categoryEditIconUseCase\|categoryEditColorUseCase"`. If the compiler warns about unused providers, remove the `@Provides` methods for `categoryEditIconUseCase` and `categoryEditColorUseCase` from the Module. Also remove their `DefaultCategoryEditIconUseCase` and `DefaultCategoryEditColorUseCase` instantiation in the Module.

**Note:** `iconPickerNavigationEntry` still needs `categoryEditIconUseCase` and `categoryEditColorUseCase` for routing back from the icon picker (used by accounts). Remove only if they appear unused — check grep before removing.

- [ ] **Step 4: Full build**

```
./gradlew assembleDebug 2>&1 | grep -E "error:" | head -30
```

Expected: clean build.

- [ ] **Step 5: Lint**

```
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: show category edit as bottom sheet; wire onDiscardHandler"
```

---

### Task 7: UI Verification

- [ ] **Step 1: Install on device**

```
./gradlew installDebug
```

- [ ] **Step 2: Navigate to Categories → tap + FAB → verify bottom sheet opens**

Use `android-ui-inspector` skill or `./scripts/dump-ui.sh` to confirm:
- Sheet appears at bottom (not full screen)
- `DragHandle` visible at top
- Header shows "Add Category" + close X button
- Icon tile (64dp, colored background) left of name field
- "Save Category" button at bottom

- [ ] **Step 3: Tap icon tile → verify picker overlay appears**

Confirm:
- Scrim covers the sheet behind the picker
- Picker sheet slides up from bottom (78% height)
- Color swatches row visible
- Icon grid visible in sections
- Tapping a color swatch updates the icon tile color live (no navigation away)
- Tapping an icon updates the icon tile live
- Tapping the scrim dismisses the picker (returns to main form)

- [ ] **Step 4: Save category**

Verify the category is saved and the sheet dismisses.

- [ ] **Step 5: Open existing category → tap edit → verify same sheet opens pre-populated**
