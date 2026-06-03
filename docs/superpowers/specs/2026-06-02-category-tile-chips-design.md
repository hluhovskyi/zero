# Category selector redesign — tile + one-time quick chips

**Status:** Spec (planning-only session; implementation runs fresh)
**Design source:** Claude Design `9VXXZmkUQIU2T5fswCsifA`, `ui_kits/zero/category-exploration.jsx`
(the ★-starred "Combined — field row + one-time quick chips" approach).

## Problem

On the transaction-edit screen the category selector is a horizontal scroll of
squircle **tiles** (`CategoryScrollRow`) with a leading "All" tile. It doesn't sit
well in the screen's vertical stack of boxed field rows (Date / Account / Notes): it
reads as a different visual language and the always-present tile strip is heavy.

## Goal

Replace the tile strip with the design's landed pattern:

1. A **boxed "Category" field row** that matches Date / Account / Notes (rounded
   `surfaceContainerLow` card, uppercase `CATEGORY` label, value, trailing chevron),
   with a leading category squircle. Tapping the row **always opens the existing
   category picker** (the `ShowAllCategories` action / bottom sheet).
   - Selected: squircle of the chosen category + its name (primary, bold).
   - Empty: neutral grid-icon placeholder squircle + "Choose category" (variant color).
2. A **quick-chip row** under the field: the top frequent categories as pill chips
   (icon + name, **no "All" chip**). Tapping a chip selects that category.

   **Implementation note:** the design's "chips vanish until the first pick" empty state
   assumes no default selection, but the app auto-selects the first ranked category
   (`TransactionEditMapping`), so `selectedCategory` is never null. The shipped behavior
   therefore keeps the chips **always visible with a selected-state highlight** (accent
   border + label on the selected chip) — design approach **A4** (field row + tile
   drawer with a selected state), the working realization of "tile and chips" given the
   auto-selection. To open the long tail, tap the field row → picker.

The category **picker bottom sheet itself is out of scope** — it already exists
(`CategoryPickerViewProvider`) and is what the row opens. List rows in
Transactions/elsewhere keep the squircle as the category's face (unchanged).

## Components

- **New** `CategoryField` composable (in `transactions/edit/common/`, replacing
  `CategoryScrollRow.kt`): renders the boxed field row + conditional quick-chip
  `LazyRow`. Structural analogs: `SelectorCard` / `DatePickerCard` (boxed field) and
  `CategoryScrollRow` (chip/icon rendering with `CategoryIconView`, `ImageLoader`).
- **Edit** `ExpenseIncomeForm` — swap `CategoryScrollRow(...)` for `CategoryField(...)`.

No ViewModel/UseCase/data-flow changes:
- `Form.ExpenseIncome.categories` (already ranked, type-filtered) feeds the chips; the
  composable shows the first **6** as quick chips.
- `Form.ExpenseIncome.selectedCategory == null` drives chip visibility (it's already
  null for a fresh "New transaction", non-null when editing).
- Row tap → existing `Action.ShowAllCategories`; chip tap → existing
  `Action.SelectCategory`.

## Strings

- Reuse `transaction_edit_category_label` ("Category").
- Add `transaction_edit_choose_category` ("Choose category") placeholder.
- The "All categories" / show-all-description strings used only by the old strip become
  unreferenced — remove if no other usage.

## Visual tokens (design → Zero)

- Field card bg `surfaceLow` → `surfaceContainerLow`; radius 16dp; label 10sp bold
  `onSurfaceVariant`; value `primary` (selected) / `onSurfaceVariant` (placeholder).
- Leading squircle: `CategoryIconView(colorScheme = …, size = 38.dp)`; placeholder uses
  the neutral `surfaceContainer` overload with the `Icons.Filled.Apps` grid icon.
- Quick chip: pill `surfaceContainerLow`, ~999dp radius, bare category icon tinted with
  the category's scheme (theme-aware: light → primary, dark → background, mirroring
  `CategoryIconView`), name `onSurface`. No selected/checked state.
- Trailing chevron: `Icons.Filled.ArrowDropDown` (matches the other field rows).

## Out of scope

Picker bottom-sheet redesign; Transfer form (no category); category list rows; the
amount keypad and other screen chrome.
