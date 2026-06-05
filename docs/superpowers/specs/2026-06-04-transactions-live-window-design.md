# Transactions: Live Window (fix empty list after import)

**Date:** 2026-06-04 · **Status:** Implemented · Supersedes the invalidation-token approach (PR #271).

## Problem
Imported transactions don't show until the screen is recreated (fresh-app restore → empty list).
The list combined a one-shot paginated backlog (`selectFirstPage`) with a reactive `selectAfter`
overlay keyed on `updatedDateTime`. Imports keep a **historical** `updatedDateTime` (it's the sync
clock — can't change), so imported rows miss the overlay, and the one-shot backlog never re-runs.

## Design
One reactive window query instead of two stitched reads:
- `TransactionRoom.selectWindow(userId, limit): Flow<…>` — newest `:limit` alive rows; Room
  re-emits on any table write, so imports/edits/deletes surface live.
- `RoomTransactionRepository.paginatedFlow` = `scan(PAGE_SIZE) + flatMapLatest(selectWindow)`: the
  load-more trigger grows the window; one window is live at a time.
- Drops `deletionEvents`/`PageEvent`/`loadDayPadding`, the `Criteria.After` overlay, and the unused
  `clock`/`zoneProvider`.

## Performance
`(userId, enteredDateTime)` composite index + raw-column sort (safe — `enteredDateTime` is canonical
ISO-8601) turns `ORDER BY … LIMIT N` into an O(N) range scan instead of a full-table sort on every
re-emit. `MIGRATION_8_9` swaps the index (DB v8 → v9); no storage migration. (Expression index not
viable: needs SQLite ≥ 3.9 / API 24; minSdk is 23.)

## Tradeoff
A raw `LIMIT` can split the oldest visible day's summary total until the next load-more — accepted
(oldest loaded day, self-heals).

## Testing
Pagination unit tests (first window, grows on trigger, live re-emit on table change) +
`ZeroE2eTest.importedHistoricalTransactionAppearsLive`.
