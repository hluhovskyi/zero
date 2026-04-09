# Category Ranking in Transaction Edit

## Problem

Categories in the transaction edit screen are displayed in database insertion order with no ranking. With 20+ categories, finding the right one requires scrolling through an unordered horizontal list. The selected category when editing is not prioritized in position.

## Solution

Rank categories by usage frequency and recency. Add a "Show all" action item. Place the original category first when editing.

## Scoring

### Formula

```
score = frequency * recencyDecay
```

- **frequency** = count of transactions using this category
- **recencyDecay** = `e^(-daysSinceLastUse / 30)` — today ~1.0, 30 days ago ~0.37, 90 days ago ~0.05

### Extensibility

The interface accepts a `Flow<RankSignal>` for future contextual signals:

```kotlin
sealed class RankSignal {
    data class AccountChanged(val accountId: String?) : RankSignal()
    data class DateChanged(val date: LocalDate?) : RankSignal()
}
```

Phase 1: signals are accepted but ignored in scoring. Phase 2: account/date become score multipliers.

### Zero-score categories

Categories with no transactions (score 0) are appended at the end, sorted alphabetically.

## Data Layer

New DAO query on existing `TransactionEntity` — no new tables:

```kotlin
@Query("""
    SELECT categoryId,
           COUNT(*) as transactionCount,
           MAX(date) as lastUsedDate
    FROM TransactionEntity
    WHERE userId = :userId AND categoryId IS NOT NULL
    GROUP BY categoryId
""")
fun selectCategoryUsageStatistic(userId: String): Flow<List<CategoryUsageStatistic>>
```

```kotlin
data class CategoryUsageStatistic(
    val categoryId: String,
    val transactionCount: Int,
    val lastUsedDate: LocalDate,
)
```

## CategoriesQueryUseCase

New method alongside existing `queryAll()`:

```kotlin
interface CategoriesQueryUseCase {
    fun queryAll(): Flow<List<Category>>
    fun queryRanked(signals: Flow<RankSignal>): Flow<List<Category>>
}
```

`queryRanked` combines `queryAll()`, usage statistics, and signals to produce a ranked list.

## Transaction Edit Behavior

`DefaultTransactionEditUseCase`:

1. Subscribes to `queryRanked(signals)` — the flow is reactive. In Phase 1, ranking effectively computes once because signals are ignored. In Phase 2, the list may re-rank when account/date signals change.
2. If editing an existing transaction, moves the **original** category (the one saved on the transaction when opened) to position 0. Subsequent user selections do not reorder the list.
3. Updates categories state on each emission.

## UI Changes to CategoryScrollRow

### "Show all" item (position 0)

- A special action item at the start of the `LazyRow`, before all categories.
- Displays a dropdown/grid icon. Styled with an action-like color scheme to distinguish from regular categories.
- Not selectable — cannot be chosen as a category.
- `onShowAll: () -> Unit` callback, wired to noop for now. Will open a bottom sheet in the future.

### Selected category positioning (edit mode)

- Handled by the edit use case, not the UI — the UI just renders the list it receives.
- Only the original category from the saved transaction is moved to front, not subsequent selections.

### Ranked order

- Categories appear in score-descending order after "Show all" (and after the original category in edit mode).
