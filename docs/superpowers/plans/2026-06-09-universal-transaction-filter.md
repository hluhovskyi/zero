# Universal DB-level transaction filter (#102)

Replace the in-memory `TransactionFilterApplicator` (transaction-list active filter) with a
single SQL-level query that filters by period / type / category IDs / account IDs. Removes the
"load all rows then filter in memory" path the issue flags. Detail-screen date-bounded
single-dimension queries (`ForCategoryBetween`, `ForAccountBetween`, …) are out of scope — a
different concern.

## Task 1 — zero-api criterion

`zero-api/.../transactions/TransactionRepository.kt`:
- Add `enum class Type { Expense, Income, Transfer }` (api-level discriminator; mirrors the
  `Transaction` subtypes, no storage strings here).
- Add `data class Filtered(from: LocalDate?, to: LocalDate?, type: Type?, categoryIds: Set<Id.Known>?, accountIds: Set<Id.Known>?) : Criteria<List<Transaction>>`.
  Period is pre-resolved to a `from`/`to` date range by the caller (clock lives in core).
  Structural analog: the existing `ForAccounts` criterion.

## Task 2 — zero-database query + routing

`TransactionRoom.kt`: add `selectFiltered(...)` — a `@Query` AND-combining nullable scalars and
flag-gated `IN` lists (empty `IN ()` is a SQL error, so gate each set with an int flag and pass a
never-empty sentinel list when the flag is 0):

```sql
SELECT * FROM TransactionEntity
WHERE userId = :userId AND deletedAt IS NULL
  AND (:from IS NULL OR date(enteredDateTime) >= date(:from))
  AND (:to   IS NULL OR date(enteredDateTime) <= date(:to))
  AND (:type IS NULL OR type = :type)
  AND (:filterCategories = 0 OR categoryId IN (:categoryIds))
  AND (:filterAccounts   = 0 OR accountId  IN (:accountIds))
ORDER BY datetime(enteredDateTime) DESC
```

`RoomTransactionRepository.kt`: route `Criteria.Filtered` → `selectFiltered`, mapping
`Type.Expense → TransactionEntity.Type.EXPENSE.name`, ids → `List<String>`, and the flag/sentinel
for each nullable set. Follow the `ForAccounts` branch as the structural template.

## Task 3 — zero-core: criterion factory + VM migration

- New `TransactionFilterCriteria(clock, zoneProvider)` — maps a `TransactionFilter` to
  `Criteria.Filtered`, resolving `period?.toDateRange(today)` and `TransactionType → Type?`
  (`All → null`). Replaces `DefaultTransactionFilterApplicator`; same DI deps.
- `DefaultTransactionViewModel`: when `activeFilter.isActive`, source the list from
  `transactionRepository.query(Criteria.Filtered(...))` (reactive on the active filter) instead of
  the paged window + in-memory `transactionFilterApplicator.apply(...)`. Unfiltered → window/all;
  search unchanged. Drop the `transactionFilterApplicator` constructor param.
- `TransactionComponent`: provide `TransactionFilterCriteria` in place of the applicator.
- Delete `TransactionFilterApplicator.kt`.

## Task 4 — Tests

- DAO instrumentation test (mirror `RoomTransactionRepositoryPaginationTest`): seed a mixed set,
  assert `selectFiltered` honours each dimension and combinations (type-only, date range,
  categories, accounts, type+category).
- `DefaultTransactionViewModelTest`: replace applicator assertions — assert a `Criteria.Filtered`
  query is issued when a filter is active, and not when it isn't.

## Task 5 — Verify

`./gradlew testDebugUnitTest lintDebug` + the new DAO instrumentation test on the emulator.
Then this branch becomes the base PR #341 rebases onto (dropping #341's full-load-when-filtered
stopgap).
