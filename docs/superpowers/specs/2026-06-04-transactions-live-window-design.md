# Transactions: Live Window (fix empty list after import)

**Date:** 2026-06-04
**Status:** Implemented
**Supersedes:** the reactive invalidation-token approach previously on this branch (PR #271).

## Problem

Imported transactions don't appear on the Transactions screen until it's recreated; in the
common case (fresh app → restore backup) the list stays empty.

The list (`DefaultTransactionViewModel.allTransactionsFlow`) was stitched from two reads:

1. `Criteria.After(screenOpenTime)` — reactive (`selectAfter`), filtered on `updatedDateTime`.
2. `Criteria.All()` + load-more trigger — a **one-shot** paginated backlog (`selectFirstPage`),
   re-fetched only on scroll or delete.

Imports write through `RoomTransactionSyncSink` preserving the **historical** `updatedDateTime`,
so imported rows are "older than screen-open" (miss #1) and the one-shot backlog never re-runs
(miss #2). They fall through both seams.

`updatedDateTime` can't be set to "now" at import time: it is the sync clock
(`TransactionSyncDao.selectSince` / `selectLastModifiedAt`, last-write-wins). The fix is on the
**read** side.

## Design — one reactive window query

A load-more list that only grows from the top equals
`ORDER BY enteredDateTime DESC LIMIT (pages × PAGE_SIZE)`. Driving that LIMIT from a **reactive**
Room `Flow` means Room re-runs the query on any insert/update/delete to `TransactionEntity` —
imports, edits, and deletes all surface live, with no hand-rolled accumulation.

### DAO (`TransactionRoom`)
`selectWindow(userId, limit): Flow<List<TransactionEntity>>` — replaces the dead
`selectFirstPage` / `selectNextPage` / `selectRemainingOnDay` / `selectAfter`. Sorts on the **raw**
column (`ORDER BY enteredDateTime DESC`, not `datetime(...)`) so the index applies.

### Repository (`RoomTransactionRepository.paginatedFlow`)
```
trigger.map { Unit }
    .scan(PAGE_SIZE) { size, _ -> size + PAGE_SIZE }   // PAGE_SIZE first; +PAGE_SIZE per load-more
    .flatMapLatest { size -> selectWindow(userId, size) }
    .map { it.mapNotNull(::toRepository) }
```
Deletes `deletionEvents`, `PageEvent`, `loadDayPadding`, and the `Criteria.After` branch.
Soft-delete already invalidates the table, so the window re-emits with the row gone.

### ViewModel (`DefaultTransactionViewModel`)
`allTransactionsFlow` collapses to the paginated query alone (drops the overlay combine and the
unused `clock`/`zoneProvider`).

### API (`TransactionRepository`)
Removes the now-unused `Criteria.After`.

## Performance — the composite index

The window's cost at scale is the `ORDER BY`, not the LIMIT. With only an index on `userId`,
SQLite full-sorts the user's rows on every emit. Because `enteredDateTime` is stored as canonical
ISO-8601 (lexicographic == chronological — the variable second/nano suffix preserves order via the
prefix property), we:

- sort on the **raw** column, and
- replace `Index("userId")` with `Index("userId", "enteredDateTime")` (covers userId-only lookups
  via leftmost prefix).

`ORDER BY enteredDateTime DESC LIMIT N` then becomes an **index range scan that stops after N** —
O(N), not O(table). Each live re-emit touches only the loaded rows, at 10k or a million.

Migration `MIGRATION_8_9` (DB v8 → v9): drop `index_TransactionEntity_userId`, create the composite.
No storage migration (format already sortable). Expression indexes were not an option (need SQLite
≥ 3.9 / API 24; minSdk is 23), hence raw-column sort + index.

## Behaviour notes

- **Update anywhere (incl. middle):** the whole `LIMIT N` query re-runs, so position/values/
  membership recompute; deletes backfill the freed slot.
- **Memory:** the window holds N = scroll depth (× PAGE_SIZE), identical to the old accumulation;
  `LazyColumn` composes only visible rows.
- **Boundary day:** a raw LIMIT can split the oldest visible day's summary total until the next
  load-more. Accepted (oldest loaded day, self-heals).

## Testing

- `RoomTransactionRepositoryPaginationTest`: first window, window grows on trigger, and **live
  re-emit on table change** (repo-level import regression).
- e2e `importedHistoricalTransactionAppearsLive`: historical-dated insert while attached appears live.
- `testDebugUnitTest` + `lintDebug` green; e2e on emulator.
