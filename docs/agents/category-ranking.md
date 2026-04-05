# Category Ranking

Categories in the transaction edit screen are ranked by a composite score. The system is signal-driven and reactive — ranking updates live as the user changes form fields.

## Scoring Formula

```
score = baseScore × accountMultiplier × monthMultiplier × amountMultiplier
```

| Component | Formula | Range | What it does |
|-----------|---------|-------|-------------|
| `baseScore` | `frequency × e^(-daysSinceLastUse / 30)` | 0–∞ | Global usage weighted by recency (30-day half-life) |
| `accountMultiplier` | `1 + accountFreq / globalFreq` | 1.0–2.0 | Boosts categories used with the selected account |
| `monthMultiplier` | `1 + 0.5 × monthFreq / globalFreq` | 1.0–1.5 | Boosts categories used in the same calendar month (seasonal patterns) |
| `amountMultiplier` | `1 + 0.75 × exp(-(ln(entered/avg))² / 2)` | 1.0–1.75 | Boosts categories whose average amount is close to entered amount (log-scale Gaussian, σ=1.0) |

Categories with zero global usage are appended at the end, sorted alphabetically.

## Signal Flow

```
DefaultTransactionEditUseCase.mutableState
  ├─ .map { selectedAccount } → RankSignal.AccountChanged
  ├─ .map { localDateTime }   → RankSignal.DateChanged
  └─ .map { amount }          → RankSignal.AmountChanged
        │
        ▼  merge()
  CategoriesQueryUseCase.queryRanked(signals)
        │
        ▼  runningFold → SignalState(accountId?, date?, amount?)
        │
        ▼  flatMapLatest
  combine(queryAll, globalStats, accountStats?, monthStats?, amountStats)
        │
        ▼  rankCategories()
  Flow<List<Category>>  (ranked)
```

`runningFold` emits an initial empty `SignalState` immediately, so ranking works even with no signals (`emptyFlow()`). Each signal updates only its field; `flatMapLatest` cancels and re-subscribes to the inner `combine` when signals change.

## Data Queries

All queries are reactive Room `Flow`s — ranking auto-updates when transactions are added/edited.

| Criteria | DAO method | Filters |
|----------|-----------|---------|
| `CategoryUsageStatistics` | `selectCategoryUsageStatistic` | Global: all transactions per category |
| `CategoryUsageStatisticsByAccount(accountId)` | `selectCategoryUsageStatisticByAccount` | Filtered by accountId |
| `CategoryUsageStatisticsByMonth(month)` | `selectCategoryUsageStatisticByMonth` | Filtered by calendar month (1–12) |
| `CategoryAmountStatistics` | `selectCategoryAmountStatistic` | `AVG(ABS(amount_value))` per category |

## Key Files

- `zero-api/.../categories/CategoriesQueryUseCase.kt` — `RankSignal` sealed class, `queryRanked()` interface
- `zero-core/.../categories/DefaultCategoriesQueryUseCase.kt` — scoring logic, signal state management
- `zero-core/.../transactions/edit/DefaultTransactionEditUseCase.kt` — signal wiring from form state
- `zero-database/.../transactions/TransactionRoom.kt` — DAO queries
- `zero-core/src/test/.../categories/DefaultCategoriesQueryUseCaseTest.kt` — 6 tests covering all signals

## Gotchas

- **`runningFold` vs `emptyFlow()`** — `runningFold` emits the seed even if the source never emits, so `queryRanked(emptyFlow())` still produces results. Tests that use signal flows should use `flowOf(signal)`, not `MutableSharedFlow` (which suspends on `emit` without collectors).
- **Amount uses log scale** — the Gaussian operates on `ln(entered/average)`, so $5 vs $10 is the same "distance" as $500 vs $1000. This prevents large-amount categories from dominating proximity matches.
- **Multipliers are independent** — each signal multiplies the base score independently. A category matching all three signals gets compounding boosts (up to ~2.0 × 1.5 × 1.75 ≈ 5.25× base score).
- **Original category placement** — in edit mode, `resolveCategoryForEdit` moves the transaction's original category to position 0 *after* ranking. This is separate from the scoring system.
- **Account/month queries are conditional** — only issued when the corresponding signal is non-null. When null, `flowOf(emptyList())` is used, and the multiplier defaults to 1.0.

## Adding a New Signal

1. Add a variant to `RankSignal` in `CategoriesQueryUseCase.kt`
2. Add the field to `SignalState` in `DefaultCategoriesQueryUseCase.kt`
3. Handle it in the `runningFold` `when` block
4. If it needs new data: add a DAO query, `Criteria` subclass, and `RoomTransactionRepository` branch
5. Add the query flow inside `flatMapLatest` (conditional on signal being non-null)
6. Add a multiplier in `rankCategories()`
7. Wire the signal from `DefaultTransactionEditUseCase` state via `.map { ... }.distinctUntilChanged().map { RankSignal.Xxx(it) }`
8. Add it to the existing `merge()` call
9. Write a test using `flowOf(RankSignal.Xxx(value))` as the signals flow
