# CategoryIconView — Reusable Icon Component

## Overview

Extract the updated rounded-square icon style from the transaction screen into a reusable `CategoryIconView` composable and apply it consistently across all category-related screens. Also fixes the transaction screen to use the actual category color instead of the hardcoded `#DDE3FF`.

## Component

**Location:** `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt`

```kotlin
@Composable
fun CategoryIconView(
    color: Color,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
)
```

- Shape: `RoundedCornerShape(percent = 30)` (rounded square, not circle)
- Background: caller-supplied `Color` (category's own color)
- Default size: `40.dp`, default padding: `8.dp`
- Content: inner icon image as composable lambda

## Files Updated

| File | Change |
|------|--------|
| `zero-ui/.../ui/CategoryIconView.kt` | New file — the reusable component |
| `zero-ui/.../transaction/TransactionExpenseView.kt` | Add `iconColor: Color?` param to `TransactionView`/`TransactionExpenseView`/`TransactionIncomeView`; use `CategoryIconView` internally |
| `zero-core/.../transactions/TransactionViewProvider.kt` | Pass `transaction.categoryColor.toCompose()` as `iconColor` |
| `zero-core/.../categories/CategoryViewProvider.kt` | Replace inline `Box + CircleShape` with `CategoryIconView` |
| `zero-core/.../categories/edit/CategoriesEditViewProvider.kt` | Replace inline image+background with `CategoryIconView(size=64.dp, contentPadding=12.dp)` |
| `zero-core/.../transactions/edit/common/TransactionEditCategorySelect.kt` | Replace both `Box + CircleShape` usages (menu item + selected item) with `CategoryIconView` |

## Sizes

- Categories list, transaction list, category dropdown: `40.dp` (default), `contentPadding = 8.dp`
- Category edit screen: `64.dp`, `contentPadding = 12.dp`
- Category dropdown menu items: `32.dp`, `contentPadding = 6.dp`
