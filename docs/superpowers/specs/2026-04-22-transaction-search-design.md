# Transaction Search Design

**Date:** 2026-04-22
**Status:** Approved

## Overview

Real-time search across all transactions by account name and category name. Search results come from a DB-level JOIN query so that all transactions (including unpaginated ones) are always reachable. The existing paginated flow stays subscribed during search so pages and cursor are preserved on return.

## Architecture

Three modules touched, all additive:

| Module | Change |
|--------|--------|
| `zero-api` | Add `Criteria.Search(query: String)` to `TransactionRepository` |
| `zero-database` | New JOIN DAO query in `TransactionRoom`; implement `Search` in `RoomTransactionRepository` |
| `zero-core` | `TransactionViewModel`: add `searchQuery` to `State`, `UpdateSearchQuery` to `Action`; `DefaultTransactionViewModel`: debounce + source-switch logic; `TransactionViewProvider`: SearchBar + empty state |

## Data Layer

### New DAO query (`TransactionRoom`)

```sql
SELECT t.* FROM TransactionEntity t
LEFT JOIN AccountEntity a ON t.accountId = a.id AND a.userId = t.userId
LEFT JOIN CategoryEntity c ON t.categoryId = c.id AND c.userId = t.userId
WHERE t.userId = :userId
  AND (a.name LIKE :query OR c.name LIKE :query)
ORDER BY datetime(t.enteredDateTime) DESC
```

Returns `Flow<List<TransactionEntity>>` — reactive, re-emits when any matching row changes.
Query parameter: `%<userInput>%` (wrapping added at repository level).

### New Criteria (`TransactionRepository`)

```kotlin
data class Search(val query: String) : Criteria
```

`RoomTransactionRepository` maps this criteria to the new JOIN query flow.

## ViewModel

### State

```kotlin
data class State(
    val transactions: List<Item> = emptyList(),
    val searchQuery: String = "",
)
```

### Action

```kotlin
data class UpdateSearchQuery(val query: String) : Action
```

### Flow composition (`DefaultTransactionViewModel`)

```kotlin
// Always subscribed — cursor/pages survive search round-trips
val pagedTransactions: Flow<List<Transaction>> = combine(afterFlow, pagedFlow) { ... }

// Display source switches based on query; never cancels pagedTransactions
combine(
    pagedTransactions,
    searchQueryFlow
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(null)
            else transactionRepository.query(Criteria.Search(query))
        }
) { paged, searchResult ->
    searchResult ?: paged
}
```

- **Debounce:** 300 ms — avoids a DB query per keystroke
- **Blank query:** emits `null`, falls back to already-live `paged` immediately
- **Non-blank query:** emits search results; pagination (`LoadMore`) is suppressed (action is a no-op while `searchQuery` is non-empty)
- **Restore:** clearing search switches back to `paged` without re-subscribing or resetting the cursor

The resolved pipeline (accounts/categories/icons lookup, grouping by date, daily summaries) is unchanged — it receives the same `List<Transaction>` regardless of source.

## UI (`TransactionViewProvider`)

- `SearchBar` pinned above the `LazyColumn` (existing component from `zero-ui`)
- Dispatches `UpdateSearchQuery` on each character change (debounce is in ViewModel)
- When `searchQuery` is non-empty and `transactions` is empty: show "No transactions found" text in place of the list
- `LoadMore` trigger is suppressed while `searchQuery` is non-empty (search results are already complete)

## Search Fields

| Field | Source |
|-------|--------|
| Account name | `AccountEntity.name` via JOIN |
| Category name | `CategoryEntity.name` via JOIN |
| Transfer target account | same `AccountEntity` JOIN covers it |

Amount and date search are out of scope for this iteration.

## Edge Cases

- **Blank / whitespace query:** treated as empty → paged mode, no DB call
- **Very fast typing:** debounce absorbs intermediate keystrokes; only the last value after 300 ms idle hits the DB
- **No results:** empty state shown; paged data is still live underneath
- **New transaction while searching:** Room's reactive Flow re-emits automatically if a matching row is inserted/updated
