# Color Scheme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each category a two-color scheme (dark saturated primary for icon tint, light neutral background for container) derived from its existing color ID via `ColorRepository.schemeFor()`.

**Architecture:** `ColorScheme` is a new domain type in `zero-api`. `ColorRepository` gains a synchronous `schemeFor()` lookup. `CategoriesQueryUseCase.Category` replaces `color: ColorValue` with `colorScheme: ColorScheme`, cascading to all view models and UI. `ImageLoader.View()` gains a `tint: Color?` param; `CoilImageLoader` converts it to a `ColorFilter`. `CategoryIconView` gains a `ColorScheme` overload whose content lambda receives `iconTint: Color` — eliminating repetition across all 4 screens.

**Tech Stack:** Kotlin, Jetpack Compose, Coil, Dagger

---

## File Map

| Action | File |
|--------|------|
| Create | `zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorScheme.kt` |
| Modify | `zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorRepository.kt` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/colors/PredefinedMaterialColorRepository.kt` |
| Modify | `zero-image-loading/src/main/java/com/hluhovskyi/zero/ImageLoader.kt` |
| Modify | `zero-image-loading/src/main/java/com/hluhovskyi/zero/CoilImageLoader.kt` |
| Modify | `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditCategory.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt` |
| Modify | `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt` |
| Modify | `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt` |

---

### Task 1: `ColorScheme` + `ColorRepository.schemeFor()` + predefined schemes

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorScheme.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorRepository.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/colors/PredefinedMaterialColorRepository.kt`

- [ ] **Step 1: Create `ColorScheme`**

```kotlin
// zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorScheme.kt
package com.hluhovskyi.zero.colors

data class ColorScheme(
    val primary: Color,
    val background: Color,
)
```

- [ ] **Step 2: Add `schemeFor()` to `ColorRepository`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

interface ColorRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    fun schemeFor(colorId: Id.Known): ColorScheme

    sealed interface Criteria<T> {
        class All : Criteria<List<Color>>
        data class ById(val id: Id.Known) : Criteria<Color>
    }

    companion object {

        fun unknownCategoryColorId(): Id.Known = Id("unknown_category_color")
    }
}
```

- [ ] **Step 3: Implement `schemeFor()` and add orange in `PredefinedMaterialColorRepository`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.graphics.Color as ComposeColor

internal class PredefinedMaterialColorRepository : ColorRepository {

    private val colors = mapOf(
        color(
            id = ColorRepository.unknownCategoryColorId().value,
            hex = 0xFF999999UL,
        ),
        color(id = "blue", hex = 0xFF1E88E5UL),
        color(id = "red", hex = ComposeColor.Red.value),
        color(id = "orange", hex = 0xFFFF9800UL),
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
    )

    private val fallbackScheme = ColorScheme(
        primary = Color(id = Id("fallback_primary"), value = ColorValue(0xFF424242UL)),
        background = Color(id = Id("fallback_background"), value = ColorValue(0xFFF5F5F5UL)),
    )

    override fun <T> query(criteria: ColorRepository.Criteria<T>): Flow<T> =
        when (criteria) {
            is ColorRepository.Criteria.All -> castingFlowOf(colors.values.toList())
            is ColorRepository.Criteria.ById -> castingFlowOfNonNull(colors[criteria.id])
        }

    override fun schemeFor(colorId: Id.Known): ColorScheme =
        schemes[colorId] ?: fallbackScheme

    private fun color(id: String, hex: ULong) = Id(id).let { knownId ->
        knownId to Color(
            id = knownId,
            value = ColorValue(hex = hex)
        )
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorScheme.kt \
        zero-api/src/main/java/com/hluhovskyi/zero/colors/ColorRepository.kt \
        app/src/main/java/com/hluhovskyi/zero/colors/PredefinedMaterialColorRepository.kt
git commit -m "feat: add ColorScheme and schemeFor() to ColorRepository"
```

---

### Task 2: `ImageLoader` tinting

**Files:**
- Modify: `zero-image-loading/src/main/java/com/hluhovskyi/zero/ImageLoader.kt`
- Modify: `zero-image-loading/src/main/java/com/hluhovskyi/zero/CoilImageLoader.kt`

`Color` here is `com.hluhovskyi.zero.colors.Color` (domain type). `zero-image-loading` already depends on `zero-api` (it imports `com.hluhovskyi.zero.common.Uri`), so this import is available. `CoilImageLoader` converts using `ComposeColor(tint.value.hex)` — no dependency on `zero-ui` needed.

- [ ] **Step 1: Add `tint` param to `ImageLoader.View(image:)`**

Replace the entire `ImageLoader.kt`:

```kotlin
package com.hluhovskyi.zero

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Uri

interface ImageLoader {

    @Composable
    fun View(
        uri: Uri,
        contentDescription: String?,
        modifier: Modifier,
        scale: Scale
    )

    @Composable
    fun View(
        image: Image,
        modifier: Modifier = Modifier,
        scale: Scale = Scale.Fit,
        tint: Color? = null,
    ) {
        View(
            uri = image.uri,
            contentDescription = image.description,
            modifier = modifier,
            scale = scale
        )
    }

    enum class Scale {
        Fit,
        Crop
    }

    interface Factory {

        fun create(): ImageLoader
    }

    companion object {

        fun empty(): ImageLoader = EmptyImageLoader

        fun factory(
            context: Context
        ): Factory = CoilImageLoaderFactory(context)
    }
}

@Composable
fun ImageLoader.View(
    uri: Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scale: ImageLoader.Scale = ImageLoader.Scale.Fit
) {
    View(
        uri = uri,
        contentDescription = contentDescription,
        modifier = modifier,
        scale = scale
    )
}
```

Note: the default `View(image:)` implementation ignores `tint` — `CoilImageLoader` overrides it to apply the color filter.

- [ ] **Step 2: Override `View(image:)` in `CoilImageLoader` with tint support**

Replace the entire `CoilImageLoader.kt`:

```kotlin
package com.hluhovskyi.zero

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Uri
import androidx.compose.ui.graphics.Color as ComposeColor

internal class CoilImageLoader(
    private val context: Context,
    private val imageLoader: coil.ImageLoader,
) : ImageLoader {

    @Composable
    override fun View(
        uri: Uri,
        contentDescription: String?,
        modifier: Modifier,
        scale: ImageLoader.Scale
    ) {
        val contentScale = when (scale) {
            ImageLoader.Scale.Fit -> ContentScale.Fit
            ImageLoader.Scale.Crop -> ContentScale.Crop
        }

        when (uri) {
            is Uri.NonEmpty -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri.value)
                        .build(),
                    contentDescription = contentDescription,
                    imageLoader = imageLoader,
                    modifier = modifier,
                    contentScale = contentScale
                )
            }
            else -> {
                Spacer(modifier = modifier)
            }
        }
    }

    @Composable
    override fun View(
        image: Image,
        modifier: Modifier,
        scale: ImageLoader.Scale,
        tint: Color?,
    ) {
        val contentScale = when (scale) {
            ImageLoader.Scale.Fit -> ContentScale.Fit
            ImageLoader.Scale.Crop -> ContentScale.Crop
        }

        when (val uri = image.uri) {
            is Uri.NonEmpty -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri.value)
                        .build(),
                    contentDescription = image.description,
                    imageLoader = imageLoader,
                    modifier = modifier,
                    contentScale = contentScale,
                    colorFilter = tint?.let { ColorFilter.tint(ComposeColor(it.value.hex)) },
                )
            }
            else -> {
                Spacer(modifier = modifier)
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-image-loading/src/main/java/com/hluhovskyi/zero/ImageLoader.kt \
        zero-image-loading/src/main/java/com/hluhovskyi/zero/CoilImageLoader.kt
git commit -m "feat: add tint param to ImageLoader.View()"
```

---

### Task 3: `CategoriesQueryUseCase` + `DefaultCategoriesQueryUseCase`

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt`

- [ ] **Step 1: Replace `color: ColorValue` with `colorScheme: ColorScheme` in `CategoriesQueryUseCase.Category`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow

interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>

    fun queryAll(): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    ) : Identifiable
}
```

- [ ] **Step 2: Update `DefaultCategoriesQueryUseCase` to resolve `ColorScheme`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull

internal class DefaultCategoriesQueryUseCase(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
) : CategoriesQueryUseCase {

    private val queryAll = combine(
        categoryRepository.query(CategoryRepository.Criteria.All()),
        iconRepository.query(IconRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
        colorRepository.query(ColorRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
    ) { categories, idToIcons, idToColors ->
        categories.map { category ->
            resolve(
                category = category,
                idToIcons = idToIcons,
                idToColors = idToColors
            )
        }
    }

    override fun queryAll(): Flow<List<CategoriesQueryUseCase.Category>> = queryAll

    override fun queryById(id: Id.Known): Flow<CategoriesQueryUseCase.Category> = queryAll
        .mapNotNull { categories -> categories.firstOrNull { it.id == id } }

    private fun resolve(
        category: CategoryRepository.Category,
        idToIcons: Map<Id.Known, Icon>,
        idToColors: Map<Id.Known, Color>
    ): CategoriesQueryUseCase.Category {
        val icon = idToIcons[category.iconId]
            ?: idToIcons[IconRepository.unknownCategoryIconId()]
            ?: Icon.empty()

        val color = idToColors[category.colorId]
            ?: idToColors[ColorRepository.unknownCategoryColorId()]

        val colorScheme = color?.let { colorRepository.schemeFor(it.id) }
            ?: colorRepository.schemeFor(ColorRepository.unknownCategoryColorId())

        return CategoriesQueryUseCase.Category(
            id = category.id,
            name = category.name,
            icon = icon.image,
            colorScheme = colorScheme,
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt
git commit -m "feat: propagate ColorScheme through CategoriesQueryUseCase"
```

---

### Task 4: `CategoryViewModel` + `DefaultCategoryViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt`

- [ ] **Step 1: Replace `color: ColorValue` with `colorScheme: ColorScheme` in `CategoryViewModel.CategoryItem`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryViewModel
    : AttachableActionStateModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryItem) : Action
    }

    data class State(
        val categories: List<CategoryItem> = emptyList()
    )

    data class CategoryItem(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    )
}
```

- [ ] **Step 2: Update `DefaultCategoryViewModel` to map `colorScheme`**

Replace the `map` block in `attach()` (the `.map { categories -> categories.map { category -> CategoryViewModel.CategoryItem(...) } }` section):

```kotlin
.map { categories ->
    categories.map { category ->
        CategoryViewModel.CategoryItem(
            id = category.id,
            name = category.name,
            icon = category.icon,
            colorScheme = category.colorScheme,
        )
    }
}
```

Full updated file:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CategoryViewModel {

    private val mutableState = MutableStateFlow(CategoryViewModel.State())
    override val state: Flow<CategoryViewModel.State> = mutableState

    override fun perform(action: CategoryViewModel.Action) {
        when (action) {
            is CategoryViewModel.Action.SelectCategory -> coroutineScope.launch(context = Dispatchers.Main) {
                onCategorySelectedHandler.onSelected(action.category.id)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            categoriesQueryUseCase.queryAll()
                .map { categories ->
                    categories.map { category ->
                        CategoryViewModel.CategoryItem(
                            id = category.id,
                            name = category.name,
                            icon = category.icon,
                            colorScheme = category.colorScheme,
                        )
                    }
                }
                .collectLatest { categories ->
                    mutableState.update { state ->
                        state.copy(categories = categories)
                    }
                }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt
git commit -m "feat: propagate ColorScheme through CategoryViewModel"
```

---

### Task 5: `TransactionViewModel` + `DefaultTransactionViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`

- [ ] **Step 1: Replace `categoryColor: ColorValue` with `categoryColorScheme: ColorScheme` in `TransactionViewModel`**

In `TransactionViewModel.kt`, replace the `Expense` and `Income` data classes. Full updated file:

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import java.time.LocalDate
import java.time.LocalDateTime

interface TransactionViewModel
    : AttachableActionStateModel<TransactionViewModel.Action, TransactionViewModel.State> {

    sealed interface Action {
        data class SelectTransaction(val item: Item.Transaction) : Action
    }

    data class State(
        val transactions: List<Item> = emptyList()
    )

    sealed interface Item {

        data class Summary(
            val date: LocalDate,
            val total: Amount,
            val currencySymbol: String,
        ) : Item

        sealed interface Transaction : Item {

            val id: Id.Known
            val date: LocalDateTime

            data class Expense(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val accountName: String,
                val accountIcon: Image,
                val categoryName: String,
                val categoryColorScheme: ColorScheme,
                val categoryIcon: Image,
                val conversion: Conversion
            ) : Transaction

            data class Income(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val accountName: String,
                val accountIcon: Image,
                val categoryName: String,
                val categoryColorScheme: ColorScheme,
                val categoryIcon: Image,
                val conversion: Conversion,
            ) : Transaction

            data class Transfer(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val accountName: String,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val targetAccountName: String,
                val targetAmount: Amount,
                val targetCurrencyId: Id.Known,
                val targetCurrencySymbol: String,
            ) : Transaction
        }
    }

    sealed interface Conversion {

        data class WithAmount(
            val amount: Amount,
            val currencyId: Id.Known,
            val currencySymbol: String,
        ) : Conversion

        object None : Conversion
    }
}
```

- [ ] **Step 2: Update `DefaultTransactionViewModel.resolve()` to use `colorScheme`**

In `DefaultTransactionViewModel.kt`, in the `resolve()` function, change both `Expense` and `Income` branches:

For `Expense` (line ~170), replace:
```kotlin
categoryColor = category.color,
```
with:
```kotlin
categoryColorScheme = category.colorScheme,
```

For `Income` (line ~189), replace:
```kotlin
categoryColor = category.color,
```
with:
```kotlin
categoryColorScheme = category.colorScheme,
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
git commit -m "feat: propagate ColorScheme through TransactionViewModel"
```

---

### Task 6: `TransactionEditCategory` + `DefaultTransactionEditUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditCategory.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

- [ ] **Step 1: Replace `color: ColorValue` with `colorScheme: ColorScheme` in `TransactionEditCategory`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image

data class TransactionEditCategory(
    override val id: Id.Known,
    val name: String,
    val colorScheme: ColorScheme,
    val icon: Image
) : Identifiable
```

- [ ] **Step 2: Update `DefaultTransactionEditUseCase` to map `colorScheme`**

In `DefaultTransactionEditUseCase.kt`, find the `categories.map { category -> TransactionEditCategory(...) }` block (around line 296–303) and replace:

```kotlin
color = category.color,
```
with:
```kotlin
colorScheme = category.colorScheme,
```

Full updated mapping:
```kotlin
categories.map { category ->
    TransactionEditCategory(
        id = category.id,
        name = category.name,
        colorScheme = category.colorScheme,
        icon = category.icon,
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditCategory.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
git commit -m "feat: propagate ColorScheme through TransactionEditCategory"
```

---

### Task 7: `CategoryEditViewModel` + `DefaultCategoryEditViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt`

- [ ] **Step 1: Replace `color: ColorValue` with `colorScheme: ColorScheme` in `CategoryEditViewModel.State`**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image

interface CategoryEditViewModel
    : AttachableActionStateModel<CategoryEditViewModel.Action, CategoryEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        object SelectIcon : Action
        object SelectColor : Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val icon: Image = Image.empty(),
        val colorScheme: ColorScheme = ColorScheme(
            primary = Color.empty(),
            background = Color.empty(),
        ),
    )
}
```

- [ ] **Step 2: Update `DefaultCategoryEditViewModel` to resolve `ColorScheme`**

Three changes in `DefaultCategoryEditViewModel.kt`:

**a) Update `CompositeState`** — replace `color: ColorValue` with `colorScheme: ColorScheme`:

```kotlin
private data class CompositeState(
    val name: String = "",
    val iconId: Id = Id.Unknown,
    val icon: Image = Image.empty(),
    val colorId: Id = Id.Unknown,
    val colorScheme: ColorScheme = ColorScheme(
        primary = Color.empty(),
        background = Color.empty(),
    ),
)
```

**b) Update the `state` flow mapping** — replace `color = state.color` with `colorScheme = state.colorScheme`:

```kotlin
override val state: Flow<CategoryEditViewModel.State> = mutableState
    .map { state ->
        CategoryEditViewModel.State(
            name = state.name,
            icon = state.icon,
            colorScheme = state.colorScheme,
        )
    }
```

**c) Update both places in `attach()` where color is resolved:**

First place (existing category loaded, line ~88–98), replace `color = color.value` with `colorScheme = colorRepository.schemeFor(color.id)`:

```kotlin
mutableState.update { state ->
    state.copy(
        name = category.name,
        iconId = icon.id,
        icon = icon.image,
        colorId = color.id,
        colorScheme = colorRepository.schemeFor(color.id),
    )
}
```

Second place (new category default color loaded, line ~114–123), replace `color = color.value` with `colorScheme = colorRepository.schemeFor(color.id)`:

```kotlin
mutableState.update { state ->
    state.copy(
        colorId = color.id,
        colorScheme = colorRepository.schemeFor(color.id),
    )
}
```

Third place (color picked by user, line ~143–150), replace `color = colorState.color.color` with `colorScheme = colorRepository.schemeFor(colorState.color.id)`:

```kotlin
mutableState.update { state ->
    state.copy(
        colorId = colorState.color.id,
        colorScheme = colorRepository.schemeFor(colorState.color.id),
    )
}
```

Add import at top of file:
```kotlin
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
```

Remove now-unused import:
```kotlin
import com.hluhovskyi.zero.common.ColorValue
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt
git commit -m "feat: propagate ColorScheme through CategoryEditViewModel"
```

---

### Task 8: Update `TransactionExpenseView` for scheme-aware icon lambda

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt`

The `icon` lambda type changes from `(@Composable () -> Unit)?` to `(@Composable (tint: DomainColor) -> Unit)?` and `iconColor` changes to `iconColorScheme: ColorScheme?`. `CategoryIconView(colorScheme)` is used internally, with the tint provided to the lambda.

- [ ] **Step 1: Replace the entire file**

```kotlin
package com.hluhovskyi.zero.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.Color as DomainColor
import com.hluhovskyi.zero.ui.CategoryIconView

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColorScheme: ColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: DomainColor) -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amount = "-$amount",
        accountName = accountName,
        iconColorScheme = iconColorScheme,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColorScheme: ColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: DomainColor) -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amount = "+$amount",
        accountName = accountName,
        iconColorScheme = iconColorScheme,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColorScheme: ColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: DomainColor) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconColorScheme != null) {
            CategoryIconView(colorScheme = iconColorScheme) { tint ->
                icon(tint)
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = categoryName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    text = amount,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                accountIcon?.invoke()
                Text(
                    text = accountName,
                    fontSize = 13.sp,
                    color = Color(0xFF44464F),
                    modifier = Modifier.weight(1f),
                )
                convertedAmount?.let {
                    Text(
                        fontSize = 12.sp,
                        color = Color(0xFF44464F),
                        text = convertedAmount,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt
git commit -m "feat: update TransactionExpenseView for ColorScheme-aware icon"
```

---

### Task 9: `CategoryIconView` scheme overload

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt`

- [ ] **Step 1: Add the scheme overload**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.Color as DomainColor
import com.hluhovskyi.zero.common.toCompose

@Composable
fun CategoryIconView(
    color: Color,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(color, shape = RoundedCornerShape(percent = 30))
            .size(size)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun CategoryIconView(
    colorScheme: ColorScheme,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable (iconTint: DomainColor) -> Unit,
) {
    CategoryIconView(
        color = colorScheme.background.value.toCompose(),
        size = size,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        content(colorScheme.primary)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt
git commit -m "feat: add ColorScheme overload to CategoryIconView"
```

---

### Task 10: Update all 4 view providers

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt`

- [ ] **Step 1: Update `CategoryViewProvider`**

Replace the icon `Box` block with `CategoryIconView(colorScheme)`. Full updated `CategoryView` composable:

```kotlin
@Composable
private fun CategoryView(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryViewModel.State())
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(state.categories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconView(colorScheme = category.colorScheme) { tint ->
                    imageLoader.View(
                        image = category.icon,
                        modifier = Modifier
                            .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                            .aspectRatio(1f),
                        scale = ImageLoader.Scale.Crop,
                        tint = tint,
                    )
                }
                Text(
                    text = category.name,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
```

Updated imports (remove unused, add new):
- Remove: `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.size`
- Keep all others
- Add: `com.hluhovskyi.zero.ui.CategoryIconView` (already present)

- [ ] **Step 2: Update `TransactionViewProvider`**

Update `toComposable` to return a tint-accepting lambda, and update `TransactionExpenseView`/`TransactionIncomeView` calls.

Replace `private fun Image.toComposable(...)`:

```kotlin
private fun Image.toComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
): @Composable (tint: com.hluhovskyi.zero.colors.Color) -> Unit = { tint ->
    imageLoader.View(
        image = this,
        modifier = modifier,
        tint = tint,
    )
}
```

Update the `Expense` call to use `iconColorScheme` instead of `iconColor`:

```kotlin
is TransactionViewModel.Item.Transaction.Expense ->
    TransactionExpenseView(
        modifier = contentModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol
        ),
        accountName = transaction.accountName,
        iconColorScheme = transaction.categoryColorScheme,
        accountIcon = transaction.accountIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier
                .alpha(ContentAlpha.medium)
                .padding(end = 6.dp)
                .size(20.dp),
        ),
        convertedAmount = transaction.conversion.format(amountFormatter),
        icon = transaction.categoryIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier.size(24.dp),
        ),
    )
```

Note: `accountIcon` still uses the old `toComposable` that returns `@Composable () -> Unit`. Keep a second overload or rename. The cleanest approach: rename the new `toComposable` to `toTintedComposable` for the icon, and keep the existing one (unchanged) for the account icon. Update calls accordingly.

Rename the tinted version `toTintedComposable`:

```kotlin
private fun Image.toTintedComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
): @Composable (tint: com.hluhovskyi.zero.colors.Color) -> Unit = { tint ->
    imageLoader.View(
        image = this,
        modifier = modifier,
        tint = tint,
    )
}
```

Keep the existing untinted `toComposable` for `accountIcon` unchanged.

Full updated `Expense` call:

```kotlin
is TransactionViewModel.Item.Transaction.Expense ->
    TransactionExpenseView(
        modifier = contentModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol
        ),
        accountName = transaction.accountName,
        iconColorScheme = transaction.categoryColorScheme,
        accountIcon = transaction.accountIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier
                .alpha(ContentAlpha.medium)
                .padding(end = 6.dp)
                .size(20.dp),
        ),
        convertedAmount = transaction.conversion.format(amountFormatter),
        icon = transaction.categoryIcon.toTintedComposable(
            imageLoader = imageLoader,
            modifier = Modifier.size(24.dp),
        ),
    )
```

Full updated `Income` call:

```kotlin
is TransactionViewModel.Item.Transaction.Income -> {
    TransactionIncomeView(
        modifier = contentModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol,
        ),
        accountName = transaction.accountName,
        iconColorScheme = transaction.categoryColorScheme,
        convertedAmount = transaction.conversion.format(amountFormatter),
        icon = transaction.categoryIcon.toTintedComposable(
            imageLoader = imageLoader,
            modifier = Modifier.size(24.dp),
        ),
    )
}
```

Remove now-unused import: `com.hluhovskyi.zero.common.toCompose`

- [ ] **Step 3: Update `CategoriesEditViewProvider`**

Replace the `CategoryIconView(...)` block (currently using `state.color`):

```kotlin
CategoryIconView(
    colorScheme = state.colorScheme,
    size = 64.dp,
    contentPadding = 12.dp,
) { tint ->
    imageLoader.View(image = state.icon, tint = tint)
}
```

The surrounding `Box` stays unchanged. Remove now-unused import `com.hluhovskyi.zero.common.toCompose`.

- [ ] **Step 4: Update `TransactionEditCategorySelect`**

Replace both `CategoryIconView` calls:

**Menu item:**
```kotlin
CategoryIconView(
    colorScheme = category.colorScheme,
    size = 32.dp,
    contentPadding = 6.dp,
    modifier = Modifier.padding(end = 12.dp),
) { tint ->
    imageLoader.View(
        modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
        image = category.icon,
        tint = tint,
    )
}
```

**Selected item:**
```kotlin
CategoryIconView(
    colorScheme = category.colorScheme,
    size = 32.dp,
    contentPadding = 6.dp,
    modifier = Modifier.padding(start = 8.dp),
) { tint ->
    imageLoader.View(image = category.icon, tint = tint)
}
```

Remove now-unused import `com.hluhovskyi.zero.common.toCompose`.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt
git commit -m "feat: apply ColorScheme icon rendering across all screens"
```
