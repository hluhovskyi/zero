# Transaction Deletion — Design Spec
_Date: 2026-04-23 | Issue: #48_

## Goal

Allow users to delete a transaction directly from the transaction list without navigating to an edit screen. Entry point is a long-press on any transaction item, which opens a contextual popup with actions. The initial action set is just "Delete", styled in red because it is destructive.

## User-Facing Behaviour

1. User long-presses any transaction card in the list.
2. A `DropdownMenu` popup appears anchored to that card.
3. The popup contains one item: **Delete** — trash icon on the left, red label on the right.
4. Tapping "Delete" immediately removes the transaction from the list. No confirmation dialog.
5. Tapping outside the popup dismisses it without taking any action.

## Architecture

### Data Layer (`zero-database`, `zero-api`)

**Soft-delete via existing `deletedAt` field.**
`TransactionEntity` already has `deletedAt: LocalDateTime? = null`. The sync layer (`SyncTransaction`) already serialises this field. No migration is needed.

**New DAO method** in `TransactionRoom`:
```
UPDATE TransactionEntity
SET deletedAt = :deletedAt, updatedDateTime = :updatedDateTime
WHERE id = :id AND userId = :userId
```
Parameters are ISO strings matching Room's `LocalDateTimeConverter`.

**Filter `deletedAt IS NULL`** added to every read query in `TransactionRoom` that feeds the transaction list:
- `selectFirstPage`, `selectNextPage`, `selectRemainingOnDay`, `search`

`selectAfter` is intentionally left without the filter so that a deletion event propagates to the ViewModel's live stream (see ViewModel section).

**New `TransactionRepository` API:**
- Add `val deletedAt: LocalDateTime?` (default `null`) to the `Transaction` sealed interface and each data class (`Expense`, `Income`, `Transfer`).
- Add `suspend fun delete(id: Id.Known)` to the interface and `Noop`.
- `RoomTransactionRepository.delete()` calls `softDelete` DAO, stamps both `deletedAt` and `updatedDateTime` with the current wall-clock time.
- `toRepository()` maps `deletedAt` through (returns a non-null item even when `deletedAt != null`, so the live stream can carry the deletion event to the ViewModel).

### ViewModel Layer (`zero-core`)

**New action:**
```kotlin
data class DeleteTransaction(val id: Id.Known) : Action
```

**`DefaultTransactionViewModel.perform()`** handles `DeleteTransaction` by launching a coroutine on `coroutineScope` that calls `transactionRepository.delete(id)`.

**Reactive removal from list:**
Room's `selectAfter` re-emits the just-deleted row (because `updatedDateTime > initialTimestamp`). `toRepository()` returns it with `deletedAt != null`. The ViewModel's merge logic replaces the stale paginated version with this fresh (deleted) copy. The `resolve()` call is guarded with an early return on `transaction.deletedAt != null`, which causes `mapNotNull` to drop it — so the deleted item disappears from the rendered list within one recomposition cycle.

### UI Layer (`zero-core` — `TransactionViewProvider`)

- Replace `Modifier.clickable { ... }` on each transaction card with `Modifier.combinedClickable(onClick = ..., onLongClick = { expandedItemId = transaction.id })`.
- Add `var expandedItemId: Id.Known? by remember { mutableStateOf(null) }` outside the `LazyColumn` (one shared state for the whole list — only one popup open at a time).
- Inside each transaction card's `Box`, append a `DropdownMenu` that is `expanded = (expandedItemId == transaction.id)`.
- The single `DropdownMenuItem` inside shows:
  - A `Row` with `Icons.Outlined.Delete` (red) + `Spacer(8.dp)` + `Text("Delete", red)`.
  - `onClick`: dispatch `DeleteTransaction(transaction.id)`, set `expandedItemId = null`.
- `onDismissRequest`: set `expandedItemId = null`.

Red color: `Color(0xFFBA1A1A)` (Material3 error default).

## Tests

- `DefaultTransactionViewModelTest`: add `DeleteTransaction action calls repository delete`.
- `RoomTransactionRepositoryPaginationTest`: add `delete soft-deletes the transaction and filters it from subsequent queries` (not strictly needed for runtime correctness, but validates the DAO layer).

## Out of Scope

- Confirmation dialog (intentionally omitted per spec).
- Undo / snackbar.
- Additional actions (edit, share) — popup is designed to be extensible but starts with only Delete.
