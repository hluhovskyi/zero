# Category Ranking Signals (Phase 2)

## Problem

`CategoriesQueryUseCase.queryRanked(signals: Flow<RankSignal>)` already accepts contextual
signals — `RankSignal.AccountChanged` and `RankSignal.DateChanged` — but the implementation
ignores them. Ranking currently uses only global frequency × recency.

Users would benefit from contextual ranking when entering a transaction: pick the right
category faster when the system can see which account they selected, which date they're
recording for, and roughly how much money is involved.

## Solution

Wire signals from `DefaultTransactionEditUseCase` state into `queryRanked`, and apply
multiplicative score multipliers based on account-specific usage, calendar-month
seasonality, and amount proximity. Add a third signal — `RankSignal.AmountChanged` —
to round out the four-signal design (date, account, amount, plus implicit type filtering
that already happens upstream).

## Scoring

```
score = (count × e^(-daysSinceLastUse / 30)) × accountMult × monthMult × amountMult
```

| Multiplier | Formula | Range | Signal source |
|---|---|---|---|
| `accountMult` | `1 + accountCount / globalCount` | 1.0 – 2.0 | selected account |
| `monthMult`   | `1 + 0.5 × monthCount / globalCount` | 1.0 – 1.5 | selected date (calendar month 1–12) |
| `amountMult`  | `1 + 0.75 × exp(-(ln(entered/avg))² / 2)` | 1.0 – 1.75 | entered amount, log-Gaussian σ=1.0 |

Each multiplier defaults to 1.0 when the signal is null/absent or the category has no
matching stats. Categories with zero global usage skip scoring and append alphabetically
at the end (unchanged from current behavior).

Max combined boost: 2.0 × 1.5 × 1.75 = 5.25×.

## Signal Flow

```
DefaultTransactionEditUseCase.mutableState
  ├─ map { selectedAccount?.id }.distinctUntilChanged() → RankSignal.AccountChanged
  ├─ map { localDateTime?.date }.distinctUntilChanged() → RankSignal.DateChanged
  └─ map { amount→BigDecimal? }.distinctUntilChanged()  → RankSignal.AmountChanged
                                │
                                ▼  merge()
                CategoriesQueryUseCase.queryRanked(signals)
                                │
                                ▼  runningFold(SignalState())   ← seed emits immediately
                                │
                                ▼  flatMapLatest
        combine(queryAll, globalStats, accountStats?, monthStats?, amountStats)
                                │
                                ▼  rankCategories()
                       Flow<List<Category>>  (ranked)
```

`runningFold` is critical — it emits its seed immediately, so `queryRanked(emptyFlow())`
still produces results (existing tests rely on this).

## Data Layer

Three new reactive queries on `TransactionEntity`. No schema changes.

### `selectCategoryUsageStatisticByAccount`

```sql
SELECT categoryId,
       COUNT(*) AS transactionCount,
       MAX(enteredDateTime) AS lastUsedDateTime
FROM TransactionEntity
WHERE userId = :userId
  AND categoryId IS NOT NULL
  AND accountId = :accountId
  AND deletedAt IS NULL
GROUP BY categoryId
```

### `selectCategoryUsageStatisticByMonth`

```sql
SELECT categoryId,
       COUNT(*) AS transactionCount,
       MAX(enteredDateTime) AS lastUsedDateTime
FROM TransactionEntity
WHERE userId = :userId
  AND categoryId IS NOT NULL
  AND strftime('%m', enteredDateTime) = :month
  AND deletedAt IS NULL
GROUP BY categoryId
```

`:month` is the zero-padded two-digit month (`"01"`–`"12"`).

### `selectCategoryAmountStatistic`

```sql
SELECT categoryId,
       AVG(ABS(amount_value)) AS averageAmount
FROM TransactionEntity
WHERE userId = :userId
  AND categoryId IS NOT NULL
  AND deletedAt IS NULL
GROUP BY categoryId
```

`ABS()` so income and expense rows both contribute meaningfully to the per-category
average.

## API additions

### `zero-api`

```kotlin
sealed class RankSignal {
    data class AccountChanged(val accountId: Id.Known?) : RankSignal()
    data class DateChanged(val date: LocalDate?) : RankSignal()
    data class AmountChanged(val amount: BigDecimal?) : RankSignal()  // NEW
}

sealed interface Criteria<T> {
    // ... existing ...
    data class CategoryUsageStatisticsByAccount(val accountId: Id.Known)
        : Criteria<List<CategoryUsageStatistic>>
    data class CategoryUsageStatisticsByMonth(val month: Int)
        : Criteria<List<CategoryUsageStatistic>>
    class CategoryAmountStatistics : Criteria<List<CategoryAmountStatistic>>
}

data class CategoryAmountStatistic(
    val categoryId: Id.Known,
    val averageAmount: BigDecimal,
)
```

## Edge cases

- **No signals yet** — `runningFold` seed produces `SignalState()` → all multipliers = 1.0
  → behaves identically to current frequency × recency ranking.
- **Empty/unparseable amount** — `state.amount.toBigDecimalOrNull()?.takeIf { it > ZERO }`
  filters at the source, so the signal only fires for valid positive amounts.
- **Account/date changes** — `distinctUntilChanged()` per source flow prevents redundant
  query subscriptions.
- **Live amount typing** — each parseable amount that differs from the previous one
  triggers a re-rank. Acceptable; queries are cheap.
- **Categories with zero global usage** — skipped during scoring (`partition`) and
  appended alphabetically. Same as current.
- **Type filtering** — happens upstream in `DefaultTransactionEditUseCase`. Ranking spans
  all categories; picker filters to expense/income subset. No change here.
- **Multiplicative double-counting** — accepted risk. If account and month signals are
  correlated (e.g. a business account used heavily in tax season), boosts compound. Bounded
  at 5.25×. Revisit if observed in production.

## Testing

Add to `DefaultCategoriesQueryUseCaseTest`:

1. **`queryRanked boosts categories used with selected account`** — two categories with equal
   global usage; one also used with selected account; verify account-boosted one ranks first.
2. **`queryRanked boosts categories used in same month`** — date signal targets month X; two
   equal global categories, one with month-X history; month-X one wins.
3. **`queryRanked boosts categories close to entered amount on log scale`** — entered $5;
   one category averages $6 (close), other averages $500 (far); close one wins.
4. **`queryRanked combines all three signals multiplicatively`** — category with all three
   matching signals beats one with only frequency.

Existing two tests (`sorts by frequency times recency`, `unused at end alphabetically`)
stay green — they use `emptyFlow()`, so SignalState is empty and multipliers are 1.0.

## Out of scope

- Persisting learned per-user weights (always use the constants above).
- Payee/merchant signal — Zero has no structured merchant field; only free-text `notes`.
- "Type" as an explicit ranking signal — already a hard upstream filter via `CategoryType`.
- Debouncing amount keystrokes — `distinctUntilChanged` is sufficient at current data scale.
- Decaying account/month boosts by recency — categories' base score already does recency
  decay; adding a second decay would over-complicate the formula.

## Module layout

| Module | Changes |
|---|---|
| `zero-api` | `RankSignal.AmountChanged`, 3 new `Criteria`, `CategoryAmountStatistic` |
| `zero-database` | 3 new `@Query` methods, 3 new branches in `RoomTransactionRepository.when` |
| `zero-core` | Rewrite `queryRanked` impl; wire signals in `DefaultTransactionEditUseCase` |
| `zero-core/test` | 4 new tests in `DefaultCategoriesQueryUseCaseTest` |

## Comparison to PR #12

The original PR #12 design (multiplicative composition, log-Gaussian amount, same weight
constants) is preserved. This spec adapts it to the current master:

- Uses `kotlinx.datetime` + injected `Clock`/`ZoneProvider` (master refactored the time
  API since PR #12).
- `CategoryAmountStatistic` lives inside `TransactionRepository` (matches existing
  `CategoryUsageStatistic` convention; PR #12 created a separate file).
- No `docs/agents/category-ranking.md` — module-level `AGENTS.md` is sufficient (per
  saved feedback).
- 4 focused tests instead of PR #12's 6 (some of the originals overlapped).
- `RankSignal.AmountChanged.amount` is filtered to `> 0` at the source rather than inside
  the multiplier.
- Type filtering is not duplicated as a "type signal" — it's already a hard upstream
  filter via `CategoryType` (which didn't exist when PR #12 was authored).
