# Import Categories Review Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the import categories review screen (step 1 of the import flow) to display categories in a grid with icon-based selection state and per-category transaction counts, and fix content:// URI resolution for the file picker.

**Architecture:** The import flow has a layered architecture: `ImportUseCase` (domain) → `CategoriesReviewViewModel` (view model) → `CategoriesReviewViewProvider` (Compose UI). The `ImportCategory` display model needs richer data (color scheme, icon, transaction count). Category selection state is managed in the view model and passed as excluded IDs to the use case on confirmation. The content:// URI bug is a one-line fix in `Uri.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger 2 (manual DI via component builders), Kotlinx Coroutines

---

## File Map

| Action | File |
|--------|------|
| Modify | `zero-api/src/main/java/com/hluhovskyi/zero/common/Uri.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/DefaultCategoriesReviewViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewComponent.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt` |
| Modify | `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt` |

---

## Task 1: Fix content:// URI resolution

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/common/Uri.kt`

The Android file picker (`ActivityResultContracts.OpenDocument`) returns `content://` URIs. `Uri.invoke()` currently only handles `android://` and `file://` — `content://` falls to `else -> AnyUri` which is neither `isFile` nor `isAndroid`, so `UriResourceResolver` silently drops it.

- [ ] **Step 1: Update Uri.invoke to handle content:// URIs**

In `Uri.kt`, change the `startsWith("android")` branch to also match `content://`:

```kotlin
// Before
value.startsWith("android") -> AnyAndroidUri(value)

// After
value.startsWith("android") || value.startsWith("content") -> AnyAndroidUri(value)
```

Full updated companion object:
```kotlin
companion object {

    operator fun invoke(value: String?): Uri = when {
        value == null -> Empty
        value.isEmpty() -> Empty
        value.startsWith("android") || value.startsWith("content") -> AnyAndroidUri(value)
        value.startsWith("file") -> AnyFileUri(value)
        else -> AnyUri(value)
    }
}
```

- [ ] **Step 2: Write a failing test for content:// URI**

Add to `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt` — or if a dedicated `UriTest.kt` exists, place it there. For now, add inline verification:

Create `zero-api/src/test/java/com/hluhovskyi/zero/common/UriTest.kt`:
```kotlin
package com.hluhovskyi.zero.common

import org.junit.Assert.assertTrue
import org.junit.Test

class UriTest {

    @Test
    fun `content scheme URI is treated as Android URI`() {
        val uri = Uri("content://com.android.providers.media/document/123")
        assertTrue("Expected isAndroid to be true", uri.isAndroid)
    }
}
```

Run: `./gradlew :zero-api:testDebugUnitTest --tests "com.hluhovskyi.zero.common.UriTest"`

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/common/Uri.kt
git add zero-api/src/test/java/com/hluhovskyi/zero/common/UriTest.kt
git commit -m "fix: resolve content:// URIs from file picker via content resolver"
```

---

## Task 2: Enrich ImportCategory with colorScheme, icon, transactionCount

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt`

`ImportCategory` currently has `iconId: Id?` and `colorId: Id?` as raw IDs. The view needs resolved color and icon to render the category grid item. Transaction count per category is needed for the subtitle.

- [ ] **Step 1: Update ImportCategory data class**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.datetime.LocalDateTime

data class ImportCategory(
    val id: Id.Known,
    val name: String,
    val colorScheme: ColorScheme,
    val icon: Image,
    val transactionCount: Int,
)

data class ImportAccount(
    val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
    val transactionCount: Int,
)

// ... (ImportTransaction unchanged)
```

- [ ] **Step 2: Verify compilation fails (expected — DefaultImportUseCase still uses old constructor)**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: compile errors in `DefaultImportUseCase.kt` about missing fields.

- [ ] **Step 3: Commit ImportDisplayModels change alone**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt
git commit -m "refactor: enrich ImportCategory with colorScheme, icon, transactionCount"
```

---

## Task 3: Update DefaultImportUseCase to populate new ImportCategory fields

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

`DefaultImportUseCase` creates `ImportCategory` objects in two places: when processing a parsed file (`SelectFile`) and when going back from accounts review (`Back`). Both need to resolve color scheme from `ColorRepository` and icon from `IconRepository`. Transaction count comes from grouping transactions by `categoryId`.

- [ ] **Step 1: Add IconRepository and ColorRepository to DefaultImportUseCase constructor**

```kotlin
internal class DefaultImportUseCase(
    private val parsers: List<SnapshotParser>,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onImportFinishedHandler: OnImportFinishedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ImportUseCase {
```

- [ ] **Step 2: Add storedCategories to InternalState to avoid re-resolving on Back**

```kotlin
private data class InternalState(
    val selectedSource: Source? = null,
    val storedDelta: SyncSnapshot? = null,
    val storedCategories: List<ImportCategory>? = null,
    val screen: ImportUseCase.State,
)
```

- [ ] **Step 3: Extract a suspend helper that builds ImportCategory list**

Add this private suspend function to `DefaultImportUseCase`:

```kotlin
private suspend fun buildCategories(delta: SyncSnapshot): List<ImportCategory> {
    val idToIcons = iconRepository.query(IconRepository.Criteria.All())
        .first()
        .associateBy { it.id }
    val txByCategoryId = delta.transactions
        .filter { it.categoryId != null }
        .groupBy { it.categoryId!! }
    return delta.categories.map { syncCategory ->
        val iconId = syncCategory.iconId?.let { Id.Known(it) }
        val colorId = syncCategory.colorId?.let { Id.Known(it) }
        ImportCategory(
            id = syncCategory.id,
            name = syncCategory.name,
            colorScheme = colorId?.let { colorRepository.schemeFor(it) } ?: ColorScheme.Grey,
            icon = iconId?.let { idToIcons[it]?.image } ?: Image.empty(),
            transactionCount = txByCategoryId[syncCategory.id.value]?.size ?: 0,
        )
    }
}
```

- [ ] **Step 4: Update SelectFile handler to use buildCategories**

Replace the `CategoriesReview` state construction in `SelectFile`:

```kotlin
is ImportUseCase.Action.SelectFile -> coroutineScope.launch {
    mutableState.update { it.copy(screen = ImportUseCase.State.Loading) }
    val source = mutableState.value.selectedSource ?: return@launch
    val parser = parsers.first { it.source.key == source.key }
    val userId = currentUserRepository.query().first().id
    try {
        val snapshot = parser.parse(action.uri)
        val delta = syncEngine.delta(snapshot, userId)
        val categories = buildCategories(delta)
        mutableState.update { current ->
            current.copy(
                storedDelta = delta,
                storedCategories = categories,
                screen = ImportUseCase.State.CategoriesReview(categories = categories),
            )
        }
    } catch (e: Exception) {
        mutableState.update { current ->
            InternalState(
                selectedSource = current.selectedSource,
                screen = ImportUseCase.State.SourceSelection(
                    sources = parsers.map { it.source },
                    error = "Couldn't read file. Check the format and try again.",
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Update Back handler for AccountsReview to reuse storedCategories**

```kotlin
is ImportUseCase.State.AccountsReview -> {
    current.copy(
        screen = ImportUseCase.State.CategoriesReview(
            categories = current.storedCategories ?: emptyList(),
        ),
    )
}
```

- [ ] **Step 6: Add required imports to DefaultImportUseCase**

```kotlin
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.flow.first
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: compile errors in `ImportComponent.kt` (DefaultImportUseCase constructor changed) and in tests.

---

## Task 4: Update ImportUseCase.ConfirmCategories to accept excluded IDs

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

The user can deselect categories they don't want to import. The excluded IDs must reach the use case so the delta can be filtered before import.

- [ ] **Step 1: Update ConfirmCategories action**

In `ImportUseCase.kt`:

```kotlin
sealed interface Action {
    data class SelectSource(val source: Source) : Action
    data class SelectFile(val uri: Uri.NonEmpty) : Action
    data class ConfirmCategories(val excludedIds: Set<Id.Known> = emptySet()) : Action
    object ConfirmAccounts : Action
    object Confirm : Action
    object Back : Action
    object DismissError : Action
    object Retry : Action
}
```

- [ ] **Step 2: Handle excludedIds in DefaultImportUseCase.ConfirmCategories**

In `DefaultImportUseCase.kt`, update the `ConfirmCategories` handler:

```kotlin
is ImportUseCase.Action.ConfirmCategories -> mutableState.update { current ->
    val delta = current.storedDelta ?: return@update current
    val filteredDelta = if (action.excludedIds.isEmpty()) {
        delta
    } else {
        delta.copy(categories = delta.categories.filter { it.id !in action.excludedIds })
    }
    val txByAccountId = filteredDelta.transactions.groupBy { it.accountId }
    current.copy(
        storedDelta = filteredDelta,
        screen = ImportUseCase.State.AccountsReview(
            accounts = filteredDelta.accounts.map { syncAccount ->
                ImportAccount(
                    id = syncAccount.id,
                    name = syncAccount.name,
                    currencyId = syncAccount.currencyId,
                    transactionCount = txByAccountId[syncAccount.id]?.size ?: 0,
                )
            },
        ),
    )
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: errors only in `ImportComponent.kt` (constructor mismatch) and `DefaultImportUseCaseTest.kt`.

---

## Task 5: Wire IconRepository and ColorRepository into ImportComponent

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt`

`ImportComponent.Dependencies` (implemented by `ApplicationComponent`) must expose `iconRepository` and `colorRepository`. `ApplicationComponent` already provides both types in its DI graph.

- [ ] **Step 1: Add iconRepository and colorRepository to ImportComponent.Dependencies**

```kotlin
interface Dependencies {
    val syncEngine: SyncEngine
    val currentUserRepository: CurrentUserRepository
    val amountFormatter: AmountFormatter
    val dateFormatter: DateFormatter
    val iconRepository: IconRepository
    val colorRepository: ColorRepository
}
```

- [ ] **Step 2: Pass them to DefaultImportUseCase in ImportComponent.Module**

```kotlin
@Provides
@ImportScope
fun useCase(
    parsers: List<SnapshotParser>,
    syncEngine: SyncEngine,
    currentUserRepository: CurrentUserRepository,
    iconRepository: IconRepository,
    colorRepository: ColorRepository,
    onImportFinishedHandler: OnImportFinishedHandler,
): ImportUseCase = DefaultImportUseCase(
    parsers = parsers,
    syncEngine = syncEngine,
    currentUserRepository = currentUserRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    onImportFinishedHandler = onImportFinishedHandler,
)
```

- [ ] **Step 3: Add imports to ImportComponent.kt**

```kotlin
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.icons.IconRepository
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: compile errors only in `DefaultImportUseCaseTest.kt`.

- [ ] **Step 5: Update DefaultImportUseCaseTest to include new constructor params**

In `DefaultImportUseCaseTest.kt`, add mocks and pass them to `createUseCase`:

```kotlin
@Mock private lateinit var iconRepository: IconRepository
@Mock private lateinit var colorRepository: ColorRepository

@Before
fun setUp() {
    whenever(parser.source).thenReturn(source)
    whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
    whenever(iconRepository.query(any())).thenReturn(flowOf(emptyList()))
    whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)
}

private fun createUseCase(scope: CoroutineScope) = DefaultImportUseCase(
    parsers = listOf(parser),
    syncEngine = syncEngine,
    currentUserRepository = currentUserRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    onImportFinishedHandler = OnImportFinishedHandler.Noop,
    coroutineScope = scope,
)
```

Add required imports to the test file:
```kotlin
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.icons.IconRepository
import org.mockito.kotlin.any
```

- [ ] **Step 6: Run existing tests**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.imports.DefaultImportUseCaseTest"`

Expected: all 3 existing tests PASS.

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt
git commit -m "feat: enrich ImportCategory with resolved colorScheme, icon and transactionCount"
```

---

## Task 6: Add toggle-selection to CategoriesReviewViewModel

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/DefaultCategoriesReviewViewModel.kt`

The categories review screen allows the user to deselect categories they don't want to import. Selection state lives in the view model; the view model passes excluded IDs to the use case on `Next`.

- [ ] **Step 1: Update CategoriesReviewViewModel interface**

```kotlin
package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.imports.ImportCategory

interface CategoriesReviewViewModel : ActionStateModel<CategoriesReviewViewModel.Action, CategoriesReviewViewModel.State> {

    data class State(
        val categories: List<ImportCategory> = emptyList(),
        val excludedIds: Set<Id.Known> = emptySet(),
    )

    sealed interface Action {
        object Next : Action
        object Back : Action
        data class ToggleCategory(val id: Id.Known) : Action
    }
}
```

- [ ] **Step 2: Update DefaultCategoriesReviewViewModel to handle toggle and pass excludedIds**

```kotlin
package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update

internal class DefaultCategoriesReviewViewModel(
    private val importUseCase: ImportUseCase,
) : CategoriesReviewViewModel {

    private val excludedIds = MutableStateFlow(emptySet<Id.Known>())

    override val state: Flow<CategoriesReviewViewModel.State> = combine(
        importUseCase.state.filterIsInstance<ImportUseCase.State.CategoriesReview>(),
        excludedIds,
    ) { reviewState, excluded ->
        CategoriesReviewViewModel.State(
            categories = reviewState.categories,
            excludedIds = excluded,
        )
    }

    override fun perform(action: CategoriesReviewViewModel.Action) {
        when (action) {
            is CategoriesReviewViewModel.Action.Next ->
                importUseCase.perform(
                    ImportUseCase.Action.ConfirmCategories(excludedIds = excludedIds.value)
                )
            is CategoriesReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
            is CategoriesReviewViewModel.Action.ToggleCategory ->
                excludedIds.update { excluded ->
                    if (action.id in excluded) excluded - action.id else excluded + action.id
                }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: PASS (CategoriesReviewViewProvider may have warnings about unused state fields, but no errors).

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/DefaultCategoriesReviewViewModel.kt
git commit -m "feat: add category toggle selection to categories review view model"
```

---

## Task 7: Wire ImageLoader into CategoriesReviewComponent

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt`

`CategoriesReviewViewProvider` needs an `ImageLoader` to render category icons. `ImageLoader` is already in `ApplicationComponent`'s DI graph. It propagates through `ImportComponent.Dependencies` → `CategoriesReviewComponent.Dependencies`.

- [ ] **Step 1: Add imageLoader to CategoriesReviewComponent.Dependencies**

```kotlin
interface Dependencies {
    val imageLoader: ImageLoader
}
```

- [ ] **Step 2: Pass imageLoader to CategoriesReviewViewProvider in the Module**

```kotlin
@dagger.Module
object Module {

    @Provides
    @CategoriesReviewScope
    fun viewModel(importUseCase: ImportUseCase): CategoriesReviewViewModel =
        DefaultCategoriesReviewViewModel(importUseCase = importUseCase)

    @Provides
    @CategoriesReviewScope
    fun viewProvider(
        viewModel: CategoriesReviewViewModel,
        imageLoader: ImageLoader,
    ): ViewProvider = CategoriesReviewViewProvider(viewModel = viewModel, imageLoader = imageLoader)
}
```

- [ ] **Step 3: Add imports to CategoriesReviewComponent.kt**

```kotlin
import com.hluhovskyi.zero.ImageLoader
```

- [ ] **Step 4: Add imageLoader to ImportComponent.Dependencies**

In `ImportComponent.kt`, add `val imageLoader: ImageLoader` to the `Dependencies` interface:

```kotlin
interface Dependencies {
    val syncEngine: SyncEngine
    val currentUserRepository: CurrentUserRepository
    val amountFormatter: AmountFormatter
    val dateFormatter: DateFormatter
    val iconRepository: IconRepository
    val colorRepository: ColorRepository
    val imageLoader: ImageLoader
}
```

Also add the import:
```kotlin
import com.hluhovskyi.zero.ImageLoader
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: compile error in `CategoriesReviewViewProvider.kt` — constructor doesn't take `imageLoader` yet.

---

## Task 8: Redesign CategoriesReviewViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt`

Replace the flat list layout with a 4-column grid. Each cell shows a `CategoryIconView` with selection state (double border when selected, no border when deselected), category name, and transaction count subtitle. All categories start selected (excluded set is empty). Tapping toggles inclusion.

- [ ] **Step 1: Rewrite CategoriesReviewViewProvider.kt**

```kotlin
package com.hluhovskyi.zero.imports.categoriesreview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant

private const val GRID_COLUMNS = 4

internal class CategoriesReviewViewProvider(
    private val viewModel: CategoriesReviewViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoriesReviewView(viewModel = viewModel, imageLoader = imageLoader)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoriesReviewView(
    viewModel: CategoriesReviewViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoriesReviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Review Categories",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "STEP 2 OF 4",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.categories, key = { it.id.value }) { category ->
                CategoryGridItem(
                    imageLoader = imageLoader,
                    category = category,
                    isSelected = category.id !in state.excludedIds,
                    onClick = {
                        viewModel.perform(CategoriesReviewViewModel.Action.ToggleCategory(category.id))
                    },
                )
            }
        }
        Button(
            onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Next) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(text = "Next →")
        }
    }
}

@Composable
private fun CategoryGridItem(
    imageLoader: ImageLoader,
    category: ImportCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            size = 48.dp,
            contentPadding = 12.dp,
            isSelected = isSelected,
        ) { iconTint ->
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                image = category.icon,
                tint = iconTint,
            )
        }
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = category.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${category.transactionCount} transactions",
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
```

- [ ] **Step 2: Check that OnSurfaceVariant exists in theme**

Run: `grep -r "OnSurfaceVariant" zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/`

If not found, open `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Color.kt` and add:
```kotlin
val OnSurfaceVariant = Color(0xFF8E8E93)
```

Then update the import in `CategoriesReviewViewProvider.kt` accordingly (it's already in the `import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant` line above).

- [ ] **Step 3: Verify full compilation**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS — `ApplicationComponent` already provides `imageLoader`, `iconRepository`, and `colorRepository`, so `ImportComponent.Dependencies` is satisfied without any changes to the app module.

- [ ] **Step 4: Run all import-related tests**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.imports.*"`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewComponent.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt
git commit -m "feat: redesign categories review as selection grid with icon state and transaction count"
```

---

## Task 9: Run all tests and open PR

- [ ] **Step 1: Run full test suite**

Run: `./gradlew testDebugUnitTest`

Expected: all tests PASS.

- [ ] **Step 2: Build debug APK to catch Dagger code-gen issues**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL. If Dagger errors appear, they point to unsatisfied dependencies — fix by tracing the component hierarchy.

- [ ] **Step 3: Create feature branch and push**

```bash
git checkout -b feat/import-categories-redesign
git push -u origin feat/import-categories-redesign
```

- [ ] **Step 4: Open pull request**

```bash
gh pr create \
  --title "feat: redesign import categories review — grid with icon selection & tx count" \
  --body "$(cat <<'EOF'
## Summary
- Fix content:// URI resolution: file picker URIs now correctly open via ContentResolver
- Enrich ImportCategory with resolved ColorScheme, icon Image, and per-category transaction count
- Redesign categories review as a 4-column grid — icon selection state replaces checkboxes; tapping an icon toggles inclusion; transaction count shown below each category name
- Excluded categories are filtered from the SyncSnapshot delta before proceeding to accounts review

## Test plan
- [ ] Open ZenMoney import, select a .csv export via the file picker — file should load (content:// fix)
- [ ] Categories review shows a 4-column grid with category icons, names, and transaction counts
- [ ] All categories start with the selected double-border state
- [ ] Tapping a category deselects it (border disappears); tapping again re-selects it
- [ ] Pressing Next proceeds to accounts review; deselected categories are absent from subsequent steps
- [ ] Pressing Back from accounts review returns to categories review with previous selection intact

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- ✅ content:// URI fix — Task 1
- ✅ Category icon selection state instead of checkbox — Tasks 6, 7, 8
- ✅ Transaction count per category — Tasks 2, 3, 8
- ✅ Focus on categories review only (accounts/transactions screens untouched)
- ✅ Color scheme rendered via `ColorRepository.schemeFor()` — Task 3
- ✅ Icon rendered via `IconRepository` — Task 3

**Placeholder scan:** No TBD, TODO, or vague steps found.

**Type consistency:**
- `ImportCategory.colorScheme: ColorScheme` (domain) — converted to `UiColorScheme` in view via `.toUi()` extension in `Colors.kt`
- `CategoriesReviewViewModel.Action.ConfirmCategories` updated to `ImportUseCase.Action.ConfirmCategories(excludedIds)` everywhere
- `excludedIds: Set<Id.Known>` used consistently in ViewModel and UseCase
