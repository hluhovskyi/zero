# Pagination Strategy

The transaction list uses a dual-query strategy to achieve both **infinite scroll history** and **reactive updates for new items**.

## Architecture

The ViewModel combines two distinct data sources from the `TransactionRepository`:

1.  **Reactive "After" Stream (`Criteria.After`):** A Room `Flow` that listens for any transaction created *after* the current session started (using an initial timestamp). This ensures newly added transactions appear instantly at the top.
2.  **Paged "History" Source (`Criteria.All`):** A cursor-based, one-shot fetch that loads chunks of 100 transactions. It uses a `trigger: Flow<*>` to signal when the next page should be loaded.

### ViewModel Combination

```kotlin
val initialTimestamp = LocalDateTime.now()
combine(
    repository.query(Criteria.After(initialTimestamp)),
    repository.query(Criteria.All(), trigger = loadMoreTrigger)
) { new, paged ->
    (new + paged).distinctBy { it.id }
}
```

## UI Trigger (Infinite Scroll)

Scroll detection is implemented in the `TransactionView` using `LazyListState` and `derivedStateOf`.

```kotlin
val shouldLoadMore by remember {
    derivedStateOf {
        val layoutInfo = lazyListState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        
        // Trigger when user is 30 items from the bottom
        lastVisibleIndex >= totalItems - 30
    }
}

LaunchedEffect(shouldLoadMore) {
    if (shouldLoadMore) viewModel.perform(Action.LoadMore)
}
```

## Database Implementation

The `TransactionRoom` DAO uses specific queries for padding:
*   `selectFirstPage`: Initial load.
*   `selectNextPage`: Cursor-based fetch older than the last loaded item.
*   `selectRemainingOnDay`: Critical for **Daily Summaries**. When a page cut happens in the middle of a day, this query fetches the rest of that day's transactions to ensure the "Day Total" header is accurate.
