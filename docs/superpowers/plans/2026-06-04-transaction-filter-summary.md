# Transaction Filter Summary — Plan

Spec: `docs/superpowers/specs/2026-06-04-transaction-filter-summary.md`.
All paths under `zero-core/src/main/java/com/hluhovskyi/zero/transactions/`.

## Task 1 — Model on TransactionViewModel

In `TransactionViewModel.kt`:
- Add `val filterSummary: FilterSummary? = null` to `State`.
- Add nested `data class FilterSummary` exactly as in the spec, with nested `Column`,
  `DateSpan`, and enums `Label`, `Emphasis`. `amount: Amount?` (Amount already imported).
  Import `LocalDate` (already imported).

Structural analog: the existing `Item.Summary` data class (date + Amount + symbol) for
shape; keep everything `@Stable`-free (plain data classes like `Conversion`).

## Task 2 — Compute summary in DefaultTransactionViewModel (TDD)

In `DefaultTransactionViewModel.kt`, inside the big `combine(...)` transform:

1. Extract a `private suspend fun primaryAmount(amount, currencyId, conversion, primaryId): Amount`
   holding the existing "WithAmount-matches-primary else convertToPrimary" logic.
2. Refactor the per-date fold's Expense/Income branches to call `primaryAmount(...)`
   (Transfer branch unchanged). Removes the 2× duplication.
3. Capture `val resolved = transactions.mapNotNull { resolve(...) }` once; feed both the
   existing `groupBy { it.date.date }` and the new summary.
4. Compute `filterSummary` only when
   `mutableState.value.searchQuery.isNotBlank() || mutableState.value.activeFilter.isActive`
   and `resolved.isNotEmpty()`; else `null`. Build it with a private
   `suspend fun computeFilterSummary(resolved, primaryCurrency): FilterSummary`:
   - `totalIn` = Σ primaryAmount over `Item.Transaction.Income`
   - `totalOut` = Σ primaryAmount over `Item.Transaction.Expense`
   - `moneyCount` = incomes + expenses; transfers excluded from stats but counted in `count`
   - `net = totalIn - totalOut`; `avg = if (moneyCount>0) (totalIn+totalOut)/moneyCount else zero`
   - `largest` = max single income/expense primaryAmount (`maxWithOrNull`/`compareTo`) or zero
   - `dateSpan` = min/max `resolved.date.date`
   - columns/emphasis per the spec's 4-way `when (hasIn to hasOut)` (`hasIn = totalIn > 0L`, etc.)
5. `mutableState.update { it.copy(transactions = items, filterSummary = filterSummary) }`.

**Test first** (`DefaultTransactionViewModelTest.kt`, follow existing mock setup):
add cases asserting `state.filterSummary` columns/emphasis for (a) mixed, (b) expenses-only
→ Spent/Avg/Largest, (c) income-only, (d) transfers-only → Net $0 + two Faint nulls,
(e) null when neither search nor filter active. Seed search via
`UpdateSearchQuery` and `advanceTimeBy(300)` past the debounce; stub
`currencyConvertUseCase.convertToPrimary` to identity and `currencyPrimaryUseCase`.

## Task 3 — FilterSummaryCard composable

New file `FilterSummaryCard.kt` — structural analog `budget/SummaryBar.kt`:
- Reuse the same bespoke `@Suppress("ZeroThemeBypass")` navy palette constants
  (SummaryBg/GreenAccent/RedAccent/SummaryTextStrong/SummaryTextDim). Define locally
  (mirror SummaryBar; do not export from budget).
- `internal fun FilterSummaryCard(summary, amountFormatter, dateFormatter, modifier)`:
  header Row (count plural + span), then a Row of 3 columns (start/center/end aligned).
  Map `Label → stringResource`, `Emphasis → Color`, sign prefix derived from `Label`
  (+ for In/Received, – for Out/Spent, +/– for Net via emphasis, none for Avg/Largest/$0).
  `amount == null` → placeholder `—`. Format via `amountFormatter.format(amount, symbol)`.
  Span via `dateFormatter` (short month + day; single date when start == end).

## Task 4 — Render in TransactionViewProvider

In `TransactionViewProvider.kt`, inside the `LazyColumn` (the non-empty branch), add a
leading `item { }` before `items(state.transactions)`:
`state.filterSummary?.let { FilterSummaryCard(it, amountFormatter, dateFormatter) }`.
No change to the `showEmpty` path.

## Task 5 — Strings

`zero-core/src/main/res/values/strings.xml` (near `budget_summary_*`):
`filter_summary_label_{net,out,in,spent,avg,largest,received}` (Title Case, uppercased in
View), `filter_summary_placeholder` = `—`, and plural `filter_summary_count`
(`%d transaction` / `%d transactions`).

## Task 6 — Verify

`./gradlew testDebugUnitTest lintDebug` → green. Then UI inspector: search the list and
confirm the navy card renders with correct adaptive columns (incl. an expenses-only query).
