# Color Scheme Design

## Goal

Give each category icon a two-color scheme: a dark, saturated primary color for the icon tint and a light, neutral background color for the icon container. Both colors share the same hue. The result matches the style shown in the reference mockup — a pale rounded-square background with a deep-colored icon on top.

---

## New Domain Types

### `ColorScheme` (zero-api)

```kotlin
data class ColorScheme(
    val primary: Color,    // com.hluhovskyi.zero.colors.Color — dark, saturated — used as icon tint
    val background: Color  // com.hluhovskyi.zero.colors.Color — light, neutral — used as container background
)
```

---

## `ColorRepository` Extension

A new synchronous method is added to the existing `ColorRepository` interface (no new repository needed):

```kotlin
interface ColorRepository {
    fun <T> query(criteria: Criteria<T>): Flow<T>
    fun schemeFor(colorId: Id.Known): ColorScheme  // new
}
```

Synchronous because `ColorScheme` is a pure lookup with no async work.

### Predefined Schemes (`PredefinedMaterialColorRepository`)

An `orange` color entry is added alongside the existing `blue` and `red`. Schemes:

Scheme `Color` objects use synthetic IDs (e.g. `"blue_primary"`, `"blue_background"`) — they are not independently queryable via `ColorRepository.query()`.

| Color ID  | `primary.value` | `background.value` |
|-----------|-----------------|---------------------|
| `blue`    | `#1565C0`       | `#E3F2FD`           |
| `red`     | `#B71C1C`       | `#FFEBEE`           |
| `orange`  | `#E65100`       | `#FFF3E0`           |
| fallback  | `#424242`       | `#F5F5F5`           |

The fallback is used when `colorId` is unknown.

---

## Data Flow

`CategoriesQueryUseCase.Category` replaces `color: ColorValue` with `colorScheme: ColorScheme`.

`DefaultCategoriesQueryUseCase.resolve()` already has `colorRepository` injected. It calls `colorRepository.schemeFor(color.id)` instead of extracting `color.value`. No new dependencies needed.

This single change cascades through all consumers:

| Type | Old field | New field |
|------|-----------|-----------|
| `CategoriesQueryUseCase.Category` | `color: ColorValue` | `colorScheme: ColorScheme` |
| `CategoryViewModel.CategoryItem` | `color: ColorValue` | `colorScheme: ColorScheme` |
| `TransactionViewModel.Item.Transaction.Expense` | `categoryColor: ColorValue` | `categoryColorScheme: ColorScheme` |
| `TransactionViewModel.Item.Transaction.Income` | `categoryColor: ColorValue` | `categoryColorScheme: ColorScheme` |
| `TransactionEditCategory` | `color: ColorValue` | `colorScheme: ColorScheme` |

---

## `ImageLoader` Tinting

`ImageLoader.View()` gains a `tint: Color? = null` parameter using the app's domain `Color` type (`com.hluhovskyi.zero.colors.Color`):

```kotlin
fun View(
    image: Image,
    modifier: Modifier = Modifier,
    scale: Scale = Scale.Fit,
    tint: Color? = null,    // new — com.hluhovskyi.zero.colors.Color, not Compose Color
)
```

`CoilImageLoader` converts and passes it to `AsyncImage`:
```kotlin
colorFilter = tint?.let { ColorFilter.tint(it.value.toCompose()) }
```

`EmptyImageLoader` accepts and ignores the param.

Coil applies this as a `Paint.colorFilter` during normal draw — no off-screen buffer, no extra GPU pass.

---

## `CategoryIconView` Scheme Overload

A new overload is added to `CategoryIconView` (zero-ui) that accepts `ColorScheme` and provides `iconTint` to its content lambda:

```kotlin
@Composable
fun CategoryIconView(
    colorScheme: ColorScheme,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable (iconTint: Color) -> Unit,  // com.hluhovskyi.zero.colors.Color, no conversion at call site
)
```

Internally delegates to the existing `CategoryIconView(color: ComposeColor, ...)` overload, passing `colorScheme.background.value.toCompose()` as background and `colorScheme.primary` (as `com.hluhovskyi.zero.colors.Color`) to the content lambda.

The existing single-color overload is kept unchanged for non-scheme use cases.

---

## UI Call Sites (all 4 screens)

All four screens use the same pattern — no color conversion at call sites:

```kotlin
CategoryIconView(colorScheme = scheme) { tint ->
    imageLoader.View(image = icon, tint = tint)  // tint: com.hluhovskyi.zero.colors.Color, passed straight through
}
```

Specific sizes and padding per screen:

| Screen | `size` | `contentPadding` |
|--------|--------|-----------------|
| Categories list | `40.dp` (default) | `8.dp` (default) |
| Transaction list | `40.dp` (default) | `8.dp` (default) |
| Category edit | `64.dp` | `12.dp` |
| Category dropdown (menu items) | `32.dp` | `6.dp` |
| Category dropdown (selected) | `32.dp` | `6.dp` |

---

## Files Changed

| Action | File |
|--------|------|
| **Create** | `zero-api/.../colors/ColorScheme.kt` |
| **Modify** | `zero-api/.../colors/ColorRepository.kt` — add `schemeFor()` |
| **Modify** | `app/.../colors/PredefinedMaterialColorRepository.kt` — add `orange`, implement `schemeFor()` |
| **Modify** | `zero-api/.../categories/CategoriesQueryUseCase.kt` — `color` → `colorScheme` |
| **Modify** | `zero-core/.../categories/DefaultCategoriesQueryUseCase.kt` — resolve via `schemeFor()` |
| **Modify** | `zero-core/.../categories/CategoryViewModel.kt` — `color` → `colorScheme` |
| **Modify** | `zero-core/.../categories/DefaultCategoryViewModel.kt` — map `colorScheme` |
| **Modify** | `zero-core/.../transactions/TransactionViewModel.kt` — `categoryColor` → `categoryColorScheme` |
| **Modify** | `zero-core/.../transactions/DefaultTransactionViewModel.kt` — map `colorScheme` |
| **Modify** | `zero-core/.../transactions/edit/TransactionEditCategory.kt` — `color` → `colorScheme` |
| **Modify** | `zero-image-loading/.../ImageLoader.kt` — add `tint` param |
| **Modify** | `zero-image-loading/.../CoilImageLoader.kt` — pass `colorFilter` to `AsyncImage` |
| **Modify** | `zero-image-loading/.../EmptyImageLoader.kt` — accept no-op `tint` param |
| **Modify** | `zero-ui/.../ui/CategoryIconView.kt` — add `ColorScheme` overload |
| **Modify** | `zero-core/.../categories/CategoryViewProvider.kt` — use scheme overload |
| **Modify** | `zero-core/.../transactions/TransactionViewProvider.kt` — use scheme overload |
| **Modify** | `zero-core/.../categories/edit/CategoriesEditViewProvider.kt` — use scheme overload |
| **Modify** | `zero-core/.../transactions/edit/common/TransactionEditCategorySelect.kt` — use scheme overload |
