# Batch-select transaction removal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add long-press multi-select to the transaction list with a contextual action bar whose only action is batch removal.

**Architecture:** Selection is ephemeral UI state (`selectedIds: Set<Id.Known>`) on `TransactionViewModel.State`; it never feeds the DB-resolve pipeline. Long-press enters selection mode (replacing the old per-item dropdown, whose Delete+Duplicate already live on the edit screen). The shared `TransactionComponent` carries the behaviour, so it appears on Home, Account detail, and Category detail.

**Tech Stack:** Kotlin, Jetbrains Compose, Dagger, JUnit + Mockito, Room.

Spec: `docs/superpowers/specs/2026-05-26-batch-select-transaction-removal-design.md`

---

### Task 1: Selection state, actions & dead-action removal (zero-core)

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt`
- Delete: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/OnDuplicateTransactionHandler.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt`

- [ ] **Step 1: Replace the `DeleteTransaction` test with selection tests**

In `DefaultTransactionViewModelTest.kt`, delete the test `DeleteTransaction action calls transactionRepository delete` (the `viewModel.perform(...DeleteTransaction...)` test) and add:

```kotlin
    @Test
    fun `ToggleSelection adds then removes an id and exits selection mode when empty`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        assertEquals(setOf(Id.Known("t1")), viewModel.state.first().selectedIds)
        assertEquals(true, viewModel.state.first().inSelectionMode)

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
        assertEquals(false, viewModel.state.first().inSelectionMode)
    }

    @Test
    fun `DeleteSelected deletes every selected id and clears the selection`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t2")))
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.DeleteSelected)
        advanceUntilIdle()

        verify(transactionRepository).delete(Id.Known("t1"))
        verify(transactionRepository).delete(Id.Known("t2"))
        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
    }

    @Test
    fun `ExitSelection clears the selection`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        viewModel.perform(TransactionViewModel.Action.ExitSelection)
        runCurrent()

        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
    }
```

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultTransactionViewModelTest*" 2>&1 | tail -20`
Expected: compilation failure — `ToggleSelection`, `DeleteSelected`, `ExitSelection`, `selectedIds`, `inSelectionMode` unresolved.

- [ ] **Step 3: Update `TransactionViewModel.kt` State & actions**

Replace the `DeleteTransaction`/`DuplicateTransaction` action lines with the selection actions, and extend `State`:

```kotlin
    sealed interface Action {
        data class SelectTransaction(val item: Item.Transaction) : Action
        data object LoadMore : Action
        data class UpdateSearchQuery(val query: String) : Action
        data class ToggleSelection(val id: Id.Known) : Action
        data object ExitSelection : Action
        data object DeleteSelected : Action

        sealed interface Filter : Action {
            data object Open : Filter
            data object RemovePeriod : Filter
            data object RemoveType : Filter
            data object RemoveCategories : Filter
            data object RemoveAccounts : Filter
            data object Clear : Filter
        }
    }

    data class State(
        val transactions: List<Item> = emptyList(),
        val searchQuery: String = "",
        val activeFilter: TransactionFilter = TransactionFilter(),
        val selectedIds: Set<Id.Known> = emptySet(),
    ) {
        val inSelectionMode: Boolean = selectedIds.isNotEmpty()
        val selectionCount: Int = selectedIds.size

        fun isSelected(id: Id.Known): Boolean = id in selectedIds
    }
```

- [ ] **Step 4: Update `DefaultTransactionViewModel.kt` `perform` and constructor**

Remove the `onDuplicateTransactionHandler` constructor parameter (the line `private val onDuplicateTransactionHandler: OnDuplicateTransactionHandler = OnDuplicateTransactionHandler.Noop,`). In `perform`, delete the `DeleteTransaction` and `DuplicateTransaction` branches and add:

```kotlin
            is TransactionViewModel.Action.ToggleSelection -> {
                mutableState.update { state ->
                    val updated = if (action.id in state.selectedIds) {
                        state.selectedIds - action.id
                    } else {
                        state.selectedIds + action.id
                    }
                    state.copy(selectedIds = updated)
                }
            }

            is TransactionViewModel.Action.ExitSelection -> {
                mutableState.update { it.copy(selectedIds = emptySet()) }
            }

            is TransactionViewModel.Action.DeleteSelected -> {
                val ids = mutableState.value.selectedIds
                coroutineScope.launch {
                    ids.forEach { transactionRepository.delete(it) }
                }
                mutableState.update { it.copy(selectedIds = emptySet()) }
            }
```

- [ ] **Step 5: Drop the duplicate handler from `TransactionComponent.kt`**

In `TransactionComponent.kt`: remove the `.onDuplicateTransactionHandler(OnDuplicateTransactionHandler.Noop)` line from `builder()`, remove the `onDuplicateTransactionHandler` method from the `Builder` interface, and remove the `onDuplicateTransactionHandler: OnDuplicateTransactionHandler,` parameter from `Module.viewModel(...)` plus the `onDuplicateTransactionHandler = onDuplicateTransactionHandler,` argument in the `DefaultTransactionViewModel(...)` call.

- [ ] **Step 6: Delete the now-unused handler file**

```bash
git rm zero-core/src/main/java/com/hluhovskyi/zero/transactions/OnDuplicateTransactionHandler.kt
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultTransactionViewModelTest*" 2>&1 | tail -20`
Expected: PASS (note: the `app` module is not built by this task-scoped command; it is fixed in Task 2).

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions zero-core/src/test/java/com/hluhovskyi/zero/transactions
git commit -m "feat(transactions): add selection state + batch delete action"
```

---

### Task 2: Remove duplicate-handler wiring from the app module

The qualified `@ForMainActivity TransactionComponent.Builder` provider existed only to attach the duplicate handler. With that gone, all consumers use the unqualified builder (provided in `ActivityComponent`). See [DI](docs/agents/dependency-injection.md).

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Remove the qualified provider**

Delete the `@Provides @ForMainActivity fun transactionComponentBuilderForMainActivity(...)` function (the one returning `builder.onDuplicateTransactionHandler { ... }`).

- [ ] **Step 2: Switch the three consumers to the unqualified builder**

In `homeNavigationEntry`, `categoryDetailNavigationEntry`, and `accountDetailNavigationEntry`, change the parameter
`@ForMainActivity transactionComponentBuilder: TransactionComponent.Builder,`
to
`transactionComponentBuilder: TransactionComponent.Builder,`
(remove the `@ForMainActivity` qualifier on all three). Leave the `TransactionEditComponent.Builder` `@ForMainActivity` usages untouched.

- [ ] **Step 3: Verify the app module compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "refactor(di): drop duplicate-handler wiring for transaction list"
```

---

### Task 3: Contextual action bar, selectable rows & strings

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Add/remove strings**

In `zero-core/src/main/res/values/strings.xml`, remove `<string name="transaction_delete">Delete</string>`, add the two content descriptions near the other `transaction_*` strings:

```xml
    <string name="transaction_selection_exit_description">Exit selection</string>
    <string name="transaction_selection_delete_description">Delete selected</string>
```

and add the plural (inside the same file, alongside the existing `<plurals name="filter_chip_categories">`):

```xml
    <plurals name="transaction_selection_count">
        <item quantity="one">%d selected</item>
        <item quantity="other">%d selected</item>
    </plurals>
```

- [ ] **Step 2: Rewrite header, row dispatch, FAB & BackHandler in `TransactionView`**

Add import `androidx.activity.compose.BackHandler` and `androidx.compose.material.icons.filled.Check`. Remove imports `androidx.compose.material.DropdownMenu`, `androidx.compose.material.DropdownMenuItem`, `androidx.compose.material.icons.outlined.ContentCopy`.

Delete the `var expandedItemId ... by remember { mutableStateOf(null) }` line.

Add at the top of the composable body (after `state` is read):

```kotlin
    BackHandler(enabled = state.inSelectionMode) {
        viewModel.perform(TransactionViewModel.Action.ExitSelection)
    }
```

Replace the header `if (displayConfig.showSearchBar || displayConfig.showFilterButton) { Row(...) }` block with:

```kotlin
            if (state.inSelectionMode) {
                SelectionBar(
                    count = state.selectionCount,
                    onClose = { viewModel.perform(TransactionViewModel.Action.ExitSelection) },
                    onDelete = { viewModel.perform(TransactionViewModel.Action.DeleteSelected) },
                )
            } else if (displayConfig.showSearchBar || displayConfig.showFilterButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (displayConfig.showSearchBar) {
                        SearchBar(
                            placeholder = stringResource(R.string.transaction_search_placeholder),
                            query = state.searchQuery,
                            onQueryChange = {
                                viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery(it))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (displayConfig.showFilterButton) {
                        if (displayConfig.showSearchBar) {
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        FilterButton(
                            activeCount = state.activeFilter.activeCount,
                            onClick = { viewModel.perform(TransactionViewModel.Action.Filter.Open) },
                        )
                    }
                }
            }
```

Change the filter-chips guard from `if (state.activeFilter.isActive)` to `if (!state.inSelectionMode && state.activeFilter.isActive)`.

Replace the `is TransactionViewModel.Item.Transaction -> TransactionRow(...)` call with:

```kotlin
                            is TransactionViewModel.Item.Transaction ->
                                TransactionRow(
                                    transaction = transaction,
                                    imageLoader = imageLoader,
                                    amountFormatter = amountFormatter,
                                    selected = state.isSelected(transaction.id),
                                    onClick = {
                                        if (state.inSelectionMode) {
                                            viewModel.perform(TransactionViewModel.Action.ToggleSelection(transaction.id))
                                        } else {
                                            viewModel.perform(TransactionViewModel.Action.SelectTransaction(transaction))
                                        }
                                    },
                                    onLongPress = {
                                        viewModel.perform(TransactionViewModel.Action.ToggleSelection(transaction.id))
                                    },
                                )
```

Change the FAB guard from `if (displayConfig.showFab)` to `if (displayConfig.showFab && !state.inSelectionMode)`.

- [ ] **Step 3: Rewrite `TransactionRow` (drop dropdown, add selected visual)**

Replace the whole `TransactionRow` composable signature and body wrapper. New signature drops `expanded`, `onSelect`, `onDismissMenu`, `onDuplicate`, `onDelete`; adds `selected`, `onClick`, `onLongPress`:

```kotlin
@Composable
private fun TransactionRow(
    transaction: TransactionViewModel.Item.Transaction,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val contentModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress,
        )
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp)
            .clip(cardShape)
            .background(
                if (selected) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.surfaceContainerLowest,
            ),
    ) {
        // ... keep the existing `when (transaction) { Expense/Income/Transfer -> ...View(...) }` block unchanged ...

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ZeroTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ZeroTheme.colors.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
```

Keep the three `TransactionExpenseView`/`TransactionIncomeView`/`TransactionTransferView` branches exactly as they are; only the surrounding `Box` background and the trailing `DropdownMenu` block change (the `DropdownMenu(...)` block is deleted, replaced by the `if (selected)` badge above).

- [ ] **Step 4: Add the `SelectionBar` composable**

Add near `FilterButton`:

```kotlin
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.transaction_selection_exit_description),
                tint = ZeroTheme.colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = pluralStringResource(R.plurals.transaction_selection_count, count, count),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurface,
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.transaction_selection_delete_description),
                tint = ZeroTheme.colors.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt zero-core/src/main/res/values/strings.xml
git commit -m "feat(transactions): contextual selection bar + selectable rows"
```

---

### Task 4: Full verification

- [ ] **Step 1: Unit tests** — `./gradlew testDebugUnitTest 2>&1 | tail -20` → all pass.
- [ ] **Step 2: Lint** — `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20` → no new errors (watch for `ViewProviderDerivation`; if it flags `state.isSelected(...)`, that means the check belongs in the View — re-confirm `isSelected` lives on `State`).
- [ ] **Step 3: UI inspection** — invoke `zero-project:android-ui-inspector`: long-press a transaction → selection bar appears with "1 selected", row shows the check badge + tinted background; tap another row → "2 selected"; tap delete → rows gone, bar dismissed; re-enter, press system back → bar dismissed, list intact.
