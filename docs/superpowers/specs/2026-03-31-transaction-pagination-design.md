# Transaction Pagination Design

**Date:** 2026-03-31
**Goal:** Load only an initial batch of transactions on app start (faster startup), then load more on demand as the user scrolls down.

---

## Problem

All transactions are currently loaded at once via a single `SELECT * FROM TransactionEntity` query with no LIMIT. On devices with large transaction histories this makes app startup noticeably slow, and all data is held in memory at once.

---

## Approach

Cursor-based pagination driven by a `trigger: Flow<*>` parameter on the repository. The repository owns all pagination state; the ViewModel owns only the trigger. Pages are padded to day boundaries so daily summary totals are always accurate.

---

## Data Layer

### `TransactionRepository.query()`

Add a `trigger` parameter with a default of `emptyFlow()`:

```kotlin
fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow()): Flow<T>
```

Existing callers are unaffected.

### New criteria: `Criteria.After(dateTime: LocalDateTime)`

Returns a reactive `Flow<List<Transaction>>` — Room re-emits whenever a transaction newer than `dateTime` is inserted or changed. Used to surface new transactions added during the current session.

```kotlin
data class After(val dateTime: LocalDateTime) : Criteria<List<Transaction>>
```

### `Criteria.All()` with trigger (cursor-based pagination)

When `trigger` is provided:

1. On subscription, load the first page via a one-shot suspend query: `ORDER BY datetime(enteredDateTime) DESC LIMIT :pageSize`
2. After fetching a page, extend it to the end of the last day — load all remaining transactions that share the same local date as the oldest transaction in the batch
3. Emit the accumulated list
4. On each trigger emission, load the next page using the date of the oldest loaded transaction as the cursor: `WHERE date(enteredDateTime) < date(:cursorDate) LIMIT :pageSize`, pad to day boundary, append, re-emit
5. `pageSize` = 20

Both page queries are one-shot `suspend` functions — no Room reactivity. New transactions are handled exclusively by `Criteria.After`.

**Why day-boundary padding:** Each date group shows a daily total. A group is only correct when all transactions for that day are loaded. Padding ensures no group is ever partial.

---

## ViewModel Layer

### `TransactionViewModel.Action`

Add `LoadMore` to the existing sealed action class:

```kotlin
sealed interface Action {
    // existing actions...
    data object LoadMore : Action
}
```

### `DefaultTransactionViewModel`

- Records `initialTimestamp = LocalDateTime.now()` at the start of `attach()`
- Holds an internal `MutableSharedFlow<Unit>` (the trigger)
- Pre-combines two transaction flows before the existing enrichment `combine()`:
  - `query(Criteria.After(initialTimestamp))` — reactive, surfaces new inserts
  - `query(Criteria.All(), trigger = loadMoreTrigger)` — cursor-based pages
  - Combined: `new + paged`, deduplicated by `id`
- On `perform(Action.LoadMore)` → emits to the trigger
- No cursor tracking, no pagination state — all of that lives in the repository

---

## UI Layer

### `TransactionViewProvider`

- Observe `LazyListState`; when the user is within ~3 items of the last loaded item, call `perform(Action.LoadMore)`
- No other UI changes needed

---

## What Does Not Change

- `Criteria.All` and all other criteria — unchanged
- All other callers of `query()` — unaffected (default `trigger = emptyFlow()`)
- The `combine()` enrichment of categories/accounts/currencies/icons — unchanged, applies to the paginated result the same way
- Daily summary computation — unchanged logic, correct because pages are padded to day boundaries

---

## Out of Scope

- "N new transactions" badge for inserts while scrolled down — can be added later if needed
- Pull-to-refresh — not needed for this change
