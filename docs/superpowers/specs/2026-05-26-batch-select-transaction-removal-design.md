# Batch-select transactions for removal — Design

Issue #58: "Batch select transaction with contextual actions (like removal)". Scope here is
**batch selection + removal only** — duplicate / other contextual actions are out of scope.

## Problem

The transaction list lets you act on one transaction at a time: tap a row to open the edit
screen, long-press to open a per-item dropdown with **Duplicate** and **Delete**. There is no
way to remove several transactions in one gesture.

## Interaction model

Standard Android multi-select (contextual action bar / CAB):

- **Enter selection mode** — long-press any transaction row. The pressed row becomes selected.
  This *replaces* the existing per-item dropdown. The dropdown's two actions are not lost:
  tapping a row opens the edit screen, which already exposes **Delete** and **Duplicate**.
- **In selection mode**
  - Tapping a row toggles its selection (it no longer navigates).
  - Long-pressing a row also toggles selection.
  - A **contextual action bar** replaces the search/filter header region: a leading close (✕)
    button, a "N selected" count, and a trailing **delete** (trash) action.
  - The add-transaction FAB is hidden.
  - System back exits selection mode (clears the selection) without leaving the screen.
- **Delete** — removes every selected transaction, then exits selection mode.
- Deselecting the last row exits selection mode automatically (empty selection = normal mode).
- Summary (date-header) rows are never selectable.

This applies everywhere `TransactionComponent` is embedded — the Home tab, Account detail, and
Category detail — because the behaviour lives in the shared component.

## State & actions (`TransactionViewModel`)

Add to `State` an ephemeral selection set; selection is UI state and must **not** flow back
through the DB-resolve pipeline (toggling must never re-run `resolve`):

```kotlin
val selectedIds: Set<Id.Known> = emptySet()
// derived, cached per instance per architecture.md "prefer body val"
val inSelectionMode: Boolean = selectedIds.isNotEmpty()
val selectionCount: Int = selectedIds.size
```

The view reads `item.id in state.selectedIds` to render a row's selected state — a membership
lookup, not a derivation, so it stays within the `ViewProviderDerivation` lint rules (no
`.filter`/`.any`/`.sortedBy`/`sumOf`). If lint flags it, expose a `fun isSelected(id): Boolean`
on `State` instead.

Replace the two single-item dropdown actions with selection actions:

- remove `Action.DeleteTransaction(id)` and `Action.DuplicateTransaction(id)`
- add `Action.ToggleSelection(id: Id.Known)` — add/remove `id`; emptying the set exits the mode
- add `Action.ExitSelection` — clear `selectedIds`
- add `Action.DeleteSelected` — `transactionRepository.delete(id)` for each selected id (reusing
  the existing per-id delete), then clear `selectedIds`

`Action.SelectTransaction` is unchanged (navigation); the view only dispatches it when not in
selection mode.

## DI / wiring cleanup

Removing the dropdown makes the duplicate-from-list path dead. Remove it rather than leave dead
code:

- delete `OnDuplicateTransactionHandler.kt`
- drop `onDuplicateTransactionHandler` from `TransactionComponent.Builder`, its default, and the
  `viewModel` `@Provides` params
- drop the handler param + its `perform` branch from `DefaultTransactionViewModel`
- in `MainActivityScreenComponent`, remove the `@ForMainActivity transactionComponentBuilderForMainActivity`
  provider (it only set the duplicate handler) and have the Home nav entry consume the plain
  `TransactionComponent.Builder`

Duplicate remains reachable from the transaction edit screen (`TransactionEditViewModel.Action.Duplicate`).

## View

`TransactionViewProvider` / `TransactionView`:

- Header region: when `state.inSelectionMode`, render the contextual action bar; otherwise the
  existing search/filter row (subject to `DisplayConfig`). The bar shows regardless of
  `DisplayConfig` so it works in detail screens that hide search/filter.
- Hide the filter-chips row while in selection mode.
- `TransactionRow` gains a `selected: Boolean` param. Selected rows use a distinct card
  background (`ZeroTheme.colors.primaryContainer`) plus a check badge overlaid at the row's
  top-end. The inner `TransactionExpenseView`/`IncomeView`/`TransferView` composables are not
  modified.
- Row clicks: `onClick` → toggle when `inSelectionMode`, else select; `onLongClick` → toggle.
- Delete the `DropdownMenu` block.
- Hide the FAB while in selection mode.
- `BackHandler(enabled = state.inSelectionMode)` → `Action.ExitSelection` (per project rule:
  in-screen overlays need an explicit `BackHandler`).

## Strings (`zero-core`)

- add plural `transaction_selection_count` → "%d selected"
- add `transaction_selection_exit_description` (close button content description)
- add `transaction_selection_delete_description` (delete button content description)
- remove `transaction_delete` if it becomes unused after the dropdown is deleted
  (`transaction_duplicate` stays — the edit screen still uses it)

## Testing

Extend the existing `DefaultTransactionViewModel` test (zero-core `transactions` test source):

- `ToggleSelection` adds then removes an id; emptying exits selection mode (`inSelectionMode` false)
- `DeleteSelected` calls `transactionRepository.delete` once per selected id and clears the set
- `ExitSelection` clears the set
- toggling selection does not re-query the repository (selection is pure UI state)

## Out of scope

Duplicate-as-batch-action, select-all, partial-selection toolbars, and any contextual action
other than removal.
