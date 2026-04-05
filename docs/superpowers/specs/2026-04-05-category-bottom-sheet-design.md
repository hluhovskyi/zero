# Category Bottom Sheet — Design Spec

## Problem

The `CategoryScrollRow` horizontal list shows ranked categories but has no way to browse all categories. The "All" button (`ShowAllItem`) exists but its `onShowAll` callback is a no-op. Users need a way to see every category and select one without leaving the transaction edit screen.

## Solution

A **modal bottom sheet** that opens when the "All" button is tapped. It displays all categories in a grid, starts half-expanded (half screen), and can be scrolled up to full screen. Tapping a category selects it on the transaction edit screen and dismisses the sheet.

## Design Decisions

### Bottom sheet approach: Compose-local state, not ViewModel/Navigation

The bottom sheet is purely UI — it doesn't need its own ViewModel, UseCase, or Navigation destination. The category data already exists in the expense/income ViewModel state. The sheet is just a different view of the same data.

- **Show/hide state**: `rememberModalBottomSheetState()` in the Compose layer
- **Categories data**: passed from existing ViewModel state (same `List<TransactionEditCategory>`)
- **Selection callback**: reuses existing `onCategorySelected` / `SelectCategory` action

### API: Material 1 `ModalBottomSheetLayout`

The project uses `androidx.compose.material:material:1.5.0` (Material 1). This provides `ModalBottomSheetLayout` with `ModalBottomSheetState`. Material 3's `ModalBottomSheet` is not available.

Key properties:
- `sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)`
- `sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`
- `sheetContent` = the category grid composable

### Grid layout

- `LazyVerticalGrid` with `GridCells.Fixed(4)` — 4 columns
- Each cell reuses existing `CategoryIconView` + name label (same pattern as `CategoryItem` in `CategoryScrollRow.kt`)
- Grid sorted alphabetically (not ranked) since user is browsing all

### Half-open behavior

`ModalBottomSheetLayout` with `ModalBottomSheetValue.HalfExpanded` as initial reveal state. User drags up to see more. The `skipHalfExpanded` parameter defaults to false, so half-expansion is supported out of the box.

### Where to place the ModalBottomSheetLayout

The `ModalBottomSheetLayout` must wrap the content that it overlays. Two options:

1. **In each ViewProvider** (expense + income) — duplicates the wrapping
2. **In `TransactionEditViewProvider`** — wraps once, but needs category data + callbacks piped up

**Choice: Option 1 — in each ViewProvider.** Reasons:
- Category data and selection actions already live at the expense/income ViewModel level
- No changes to `TransactionEditViewModel` or `TransactionEditUseCase` needed
- Minimal blast radius — only touching ViewProvider files and one shared composable

### ShowAll action flow

1. User taps "All" in `CategoryScrollRow`
2. `onShowAll` callback triggers `coroutineScope.launch { sheetState.show() }`
3. Bottom sheet reveals with grid of all categories (alphabetically sorted)
4. User taps a category
5. `onCategorySelected(category)` fires (same as inline selection)
6. `coroutineScope.launch { sheetState.hide() }` dismisses the sheet

## Components

### New: `CategoryBottomSheetGrid` composable (in `transactions/edit/common/`)

```
CategoryBottomSheetGrid(
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
)
```

A `LazyVerticalGrid` displaying all categories. Each cell is a `Column(icon + name)` using `CategoryIconView`.

### Modified: `TransactionEditExpenseViewProvider` and `TransactionEditIncomeViewProvider`

Wrap existing content in `ModalBottomSheetLayout`. Wire `onShowAll` to show the sheet. Wire grid selection to hide sheet + dispatch `SelectCategory`.

## Files Changed

| File | Change |
|------|--------|
| `zero-core/.../transactions/edit/common/CategoryBottomSheetGrid.kt` | **New** — Grid composable for bottom sheet content |
| `zero-core/.../transactions/edit/expense/TransactionEditExpenseViewProvider.kt` | Wrap in `ModalBottomSheetLayout`, wire `onShowAll` |
| `zero-core/.../transactions/edit/income/TransactionEditIncomeViewProvider.kt` | Same as expense |

## Out of Scope

- Search/filter within the bottom sheet
- Category editing from the bottom sheet
- New ViewModel/UseCase/Component for the sheet
