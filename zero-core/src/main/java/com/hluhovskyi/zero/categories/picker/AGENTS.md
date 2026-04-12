# Category Picker

## Purpose
Bottom sheet screen for selecting a category. Used by the transaction edit flow when the user taps "show all categories".

## Inputs
- `OnCategorySelectedHandler` — callback invoked with the chosen `Id.Known`
- `selectedCategoryId: Id` — optional; when `Id.Known`, the matching category is highlighted with a selection ring on open

## Outputs
- Calls `OnCategorySelectedHandler.onSelected(categoryId)` on selection (dispatched on `Dispatchers.Main`)

## Invariants
- Stateless with respect to the caller — no knowledge of which screen opened it
- Categories sorted alphabetically

## Key Flow
1. `attach()` → queries all categories via `CategoriesQueryUseCase`, maps to `CategoryPickerItem`, sorts by name
2. User taps a category → `perform(SelectCategory)` → calls `onCategorySelectedHandler`
3. Caller is responsible for navigating back after receiving the result

## Dependencies
- `CategoriesQueryUseCase` — live category list
- `ImageLoader` — renders category icons

## Integration (how callers open this screen)
Navigate to `Destinations.Category.Picker` using a `XxxUseCase` that implements the Request / Pick / Picked pattern. The use case wires `onCategorySelectedHandler` to `perform(Pick(...))` and calls `navigator.back()` on result. See `TransactionEditCategoryUseCase` + `DefaultTransactionEditCategoryUseCase` for a concrete example, and `docs/agents/navigation.md` for the pattern.
