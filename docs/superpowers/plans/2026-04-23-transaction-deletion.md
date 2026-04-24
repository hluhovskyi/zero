# Transaction Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add long-press → popup → soft-delete for transaction items in the transaction list.

**Architecture:** Soft-delete via the existing `deletedAt` field on `TransactionEntity`. `selectAfter` (the live-update stream) carries deletion events to the ViewModel, which filters them out before rendering. Pagination and search queries are updated to exclude deleted rows. The UI uses `combinedClickable` + a single `DropdownMenu` per list.

**Tech Stack:** Kotlin, Jetpack Compose (`combinedClickable`, `DropdownMenu`), Room (`@Query` UPDATE), Coroutines/Flow, JUnit 4 + Mockito

---

## File Map

| File | Change |
|------|--------|
| `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt` | Add `deletedAt: LocalDateTime?` to `Transaction` sealed interface + each data class; add `suspend fun delete(id: Id.Known)` |
| `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt` | Add `softDelete` @Query; add `AND deletedAt IS NULL` to `selectFirstPage`, `selectNextPage`, `selectRemainingOnDay`, `search` |
| `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt` | Implement `delete()`; map `deletedAt` in `toRepository()` |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt` | Add `DeleteTransaction(id)` action |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt` | Handle action; filter deleted in `resolve()` |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` | `combinedClickable`, popup state, `DropdownMenu` with Delete item |
| `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt` | Add `DeleteTransaction` test |
| `zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt` | Add soft-delete test |

---

### Task 1: Add `deletedAt` + `delete()` to `TransactionRepository`

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`

- [ ] **Step 1: Add `deletedAt` property to the `Transaction` sealed interface and all three data classes**

Replace the `Transaction` sealed interface block (lines 38–79) with:

```kotlin
sealed interface Transaction {

    val id: Id.Known
    val amount: Amount
    val currencyId: Id.Known
    val accountId: Id.Known
    val dateTime: LocalDateTime
    val updatedDateTime: LocalDateTime
    val deletedAt: LocalDateTime?

    data class Expense(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val dateTime: LocalDateTime,
        override val updatedDateTime: LocalDateTime,
        override val deletedAt: LocalDateTime? = null,
        val categoryId: Id.Known,
        val rate: Rate,
    ) : Transaction

    data class Income(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val dateTime: LocalDateTime,
        override val updatedDateTime: LocalDateTime,
        override val deletedAt: LocalDateTime? = null,
        val categoryId: Id.Known,
        val rate: Rate,
    ) : Transaction

    data class Transfer(
        override val id: Id.Known,
        override val amount: Amount,
        override val currencyId: Id.Known,
        override val accountId: Id.Known,
        override val dateTime: LocalDateTime,
        override val updatedDateTime: LocalDateTime,
        override val deletedAt: LocalDateTime? = null,
        val targetAccount: Id.Known,
        val targetAmount: Amount,
    ) : Transaction
}
```

- [ ] **Step 2: Add `delete()` to the interface and `Noop`**

After `suspend fun insert(transactions: List<Transaction>)` add:

```kotlin
suspend fun delete(id: Id.Known)
```

In the `Noop` object add:

```kotlin
override suspend fun delete(id: Id.Known) = Unit
```

- [ ] **Compile-check only (no test yet)**

```bash
./gradlew :zero-api:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 2: Add soft-delete DAO method + filter deleted rows in `TransactionRoom`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`

- [ ] **Step 1: Add `softDelete` @Query method**

After the `insert` methods at the bottom of `TransactionRoom`, add:

```kotlin
@Query(
    """
    UPDATE TransactionEntity
    SET deletedAt = :deletedAt, updatedDateTime = :updatedDateTime
    WHERE id = :id AND userId = :userId
""",
)
suspend fun softDelete(id: String, userId: String, deletedAt: String, updatedDateTime: String)
```

- [ ] **Step 2: Add `AND deletedAt IS NULL` to pagination and search queries**

Update `selectFirstPage`:
```kotlin
@Query(
    """
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
      AND deletedAt IS NULL
    ORDER BY datetime(enteredDateTime) DESC
    LIMIT :limit
""",
)
suspend fun selectFirstPage(userId: String, limit: Int): List<TransactionEntity>
```

Update `selectNextPage`:
```kotlin
@Query(
    """
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
      AND date(enteredDateTime) < date(:cursorDate)
      AND deletedAt IS NULL
    ORDER BY datetime(enteredDateTime) DESC
    LIMIT :limit
""",
)
suspend fun selectNextPage(userId: String, cursorDate: String, limit: Int): List<TransactionEntity>
```

Update `selectRemainingOnDay`:
```kotlin
@Query(
    """
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
      AND date(enteredDateTime) = date(:day)
      AND datetime(enteredDateTime) < datetime(:beforeDateTime)
      AND deletedAt IS NULL
    ORDER BY datetime(enteredDateTime) DESC
""",
)
suspend fun selectRemainingOnDay(
    userId: String,
    day: String,
    beforeDateTime: String,
): List<TransactionEntity>
```

Update `search`:
```kotlin
@Query(
    """
    SELECT t.* FROM TransactionEntity t
    LEFT JOIN AccountEntity a ON t.accountId = a.id AND a.userId = t.userId
    LEFT JOIN CategoryEntity c ON t.categoryId = c.id AND c.userId = t.userId
    WHERE t.userId = :userId
      AND t.deletedAt IS NULL
      AND (a.name LIKE :query ESCAPE '\' OR c.name LIKE :query ESCAPE '\')
    ORDER BY datetime(t.enteredDateTime) DESC
""",
)
fun search(userId: String, query: String): Flow<List<TransactionEntity>>
```

Note: `selectAfter` is intentionally left without the filter — it must carry deletion events to the ViewModel.

- [ ] **Compile-check**

```bash
./gradlew :zero-database:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 3: Implement `delete()` in `RoomTransactionRepository` + map `deletedAt` in `toRepository()`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`

- [ ] **Step 1: Map `deletedAt` through `toRepository()`**

In `toRepository()`, update each branch to pass `deletedAt` through. The function signature and `when` block stay the same; each constructor call gets one new named argument:

For `EXPENSE` branch, change the constructor call to:
```kotlin
TransactionRepository.Transaction.Expense(
    id = id,
    amount = amount.convert(),
    accountId = accountId,
    currencyId = currencyId,
    dateTime = enteredDateTime,
    updatedDateTime = updatedDateTime,
    deletedAt = deletedAt,
    categoryId = categoryId,
    rate = rate.convert(),
)
```

For `INCOME` branch:
```kotlin
TransactionRepository.Transaction.Income(
    id = id,
    amount = amount.convert(),
    accountId = accountId,
    currencyId = currencyId,
    dateTime = enteredDateTime,
    updatedDateTime = updatedDateTime,
    deletedAt = deletedAt,
    categoryId = categoryId,
    rate = rate.convert(),
)
```

For `TRANSFER` branch:
```kotlin
TransactionRepository.Transaction.Transfer(
    id = id,
    amount = amount.convert(),
    accountId = accountId,
    currencyId = currencyId,
    dateTime = enteredDateTime,
    updatedDateTime = updatedDateTime,
    deletedAt = deletedAt,
    targetAccount = Id.Known(targetAccount),
    targetAmount = targetAmount.convert(),
)
```

- [ ] **Step 2: Implement `delete()`**

After `insert(transactions: List<...>)`, add:

```kotlin
override suspend fun delete(id: Id.Known) {
    incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
        val now = clock.localDateTime(zoneProvider.timeZone()).toString()
        transactionRoom().softDelete(
            id = id.value,
            userId = userId.value,
            deletedAt = now,
            updatedDateTime = now,
        )
    }
}
```

- [ ] **Write failing test for `delete()`**

In `RoomTransactionRepositoryPaginationTest.kt`, add at the bottom:

```kotlin
@Test
fun `delete soft-deletes the transaction`() = runTest {
    val entity = expenseEntity("t1", jan15h10)
    val now = LocalDateTime(2024, 1, 16, 9, 0)
    whenever(clock.now()).thenReturn(kotlinx.datetime.Instant.parse("2024-01-16T09:00:00Z"))
    whenever(zoneProvider.timeZone()).thenReturn(kotlinx.datetime.TimeZone.UTC)

    repo.delete(Id.Known("t1"))
    advanceUntilIdle()

    org.mockito.kotlin.verify(transactionRoom).softDelete(
        id = "t1",
        userId = "user1",
        deletedAt = now.toString(),
        updatedDateTime = now.toString(),
    )
}
```

- [ ] **Run test to verify it fails**

```bash
./gradlew :zero-database:testDebugUnitTest --tests "com.hluhovskyi.zero.transactions.RoomTransactionRepositoryPaginationTest.delete soft-deletes the transaction"
```
Expected: FAIL (method not yet implemented, or verify fails)

- [ ] **Run test to verify it passes after implementation**

```bash
./gradlew :zero-database:testDebugUnitTest --tests "com.hluhovskyi.zero.transactions.RoomTransactionRepositoryPaginationTest.delete soft-deletes the transaction"
```
Expected: PASS

- [ ] **Run full database test suite**

```bash
./gradlew :zero-database:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt \
        zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt
git commit -m "feat: add soft-delete to TransactionRepository and DAO"
```

---

### Task 4: Add `DeleteTransaction` action to ViewModel and handle it

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`

- [ ] **Step 1: Add `DeleteTransaction` action to `TransactionViewModel`**

In `TransactionViewModel.kt`, inside `sealed interface Action`, add after `UpdateSearchQuery`:

```kotlin
data class DeleteTransaction(val id: Id.Known) : Action
```

- [ ] **Step 2: Handle `DeleteTransaction` in `DefaultTransactionViewModel.perform()`**

Add a new branch in the `when` block inside `perform()`:

```kotlin
is TransactionViewModel.Action.DeleteTransaction -> {
    coroutineScope.launch {
        transactionRepository.delete(action.id)
    }
}
```

- [ ] **Step 3: Filter deleted transactions in `resolve()` / combine**

In `DefaultTransactionViewModel.attach()`, inside the big `combine { transactions, ... ->` lambda, change the line:

```kotlin
transactions
    .mapNotNull { transaction ->
        resolve(
            transaction = transaction,
            ...
        )
    }
```

to:

```kotlin
transactions
    .filter { it.deletedAt == null }
    .mapNotNull { transaction ->
        resolve(
            transaction = transaction,
            ...
        )
    }
```

The exact location is inside the `combine(activeTransactions, categoriesQueryUseCase..., accountRepository..., currencyRepository..., iconRepository...) { transactions, idToCategories, idToAccounts, idToCurrencies, idToIcons ->` block, right before `.groupBy { it.date.date }`.

- [ ] **Write failing test**

In `DefaultTransactionViewModelTest.kt`, add:

```kotlin
@Test
fun `DeleteTransaction action calls transactionRepository delete`() = runTest {
    val viewModel = createViewModel(backgroundScope)
    viewModel.attach()
    runCurrent()

    viewModel.perform(TransactionViewModel.Action.DeleteTransaction(Id.Known("t1")))
    advanceUntilIdle()

    org.mockito.kotlin.verify(transactionRepository).delete(Id.Known("t1"))
}
```

- [ ] **Run test to verify it fails**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.transactions.DefaultTransactionViewModelTest.DeleteTransaction action calls transactionRepository delete"
```
Expected: FAIL (action not handled)

- [ ] **Run test to verify it passes**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.transactions.DefaultTransactionViewModelTest.DeleteTransaction action calls transactionRepository delete"
```
Expected: PASS

- [ ] **Run full core test suite**

```bash
./gradlew :zero-core:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt
git commit -m "feat: add DeleteTransaction action and filter deleted items from list"
```

---

### Task 5: Add long-press popup to `TransactionViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] **Step 1: Add required imports**

Add to the import block:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.hluhovskyi.zero.common.Id
```

(Some of these imports may already exist — only add ones that are not present.)

- [ ] **Step 2: Add popup state to `TransactionView` and wire `combinedClickable`**

Inside `TransactionView`, immediately before the `LazyColumn`, add:

```kotlin
var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
```

In the `is TransactionViewModel.Item.Transaction ->` branch, replace the existing `contentModifier`:

```kotlin
val contentModifier = Modifier
    .fillMaxWidth()
    .combinedClickable(
        onClick = { viewModel.perform(TransactionViewModel.Action.SelectTransaction(transaction)) },
        onLongClick = { expandedItemId = transaction.id },
    )
    .padding(horizontal = 16.dp, vertical = 14.dp)
```

- [ ] **Step 3: Append `DropdownMenu` inside the card `Box`**

Inside the white card `Box` (which currently ends after the `when (transaction) { ... }` block), append after the `when` block but still inside the outer `Box`:

```kotlin
DropdownMenu(
    expanded = expandedItemId == transaction.id,
    onDismissRequest = { expandedItemId = null },
) {
    DropdownMenuItem(
        onClick = {
            viewModel.perform(TransactionViewModel.Action.DeleteTransaction(transaction.id))
            expandedItemId = null
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = Color(0xFFBA1A1A),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Delete",
                color = Color(0xFFBA1A1A),
            )
        }
    }
}
```

Add `import androidx.compose.foundation.layout.Spacer` if not already present.

- [ ] **Add `@OptIn(ExperimentalFoundationApi::class)` annotation**

Add `@OptIn(ExperimentalFoundationApi::class)` to the `TransactionView` composable function.

- [ ] **Build to verify compilation**

```bash
./gradlew :zero-core:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Run lint**

```bash
./gradlew :zero-core:lintDebug
```
Expected: BUILD SUCCESSFUL (no new errors)

- [ ] **Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "feat: add long-press delete popup to transaction list items"
```

---

### Task 6: Build the full app and verify UI on device

- [ ] **Build debug APK**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Install and verify**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the transactions screen. Long-press any transaction card.

Expected:
- Popup appears anchored to the card
- Single "Delete" item with trash icon (both in red)
- Tapping "Delete" removes the item from the list immediately
- Tapping outside dismisses the popup without deleting

- [ ] **Verify via UI inspector**

```bash
./scripts/dump-ui.sh
```
Check that the popup menu appears and the item is focusable/clickable.

---

### Task 7: Commit plan + spec and open PR

- [ ] **Commit plan and spec**

```bash
git add docs/superpowers/specs/2026-04-23-transaction-deletion-design.md \
        docs/superpowers/plans/2026-04-23-transaction-deletion.md
git commit -m "docs: add transaction deletion design spec and implementation plan"
```

- [ ] **Push branch**

```bash
git push -u origin feature/transaction-deletion
```

- [ ] **Open PR**

```bash
gh pr create \
  --title "feat: add transaction deletion via long-press popup (#48)" \
  --body "Closes #48

## Summary
- Long-press on any transaction card opens a contextual popup with a red Delete action
- Soft-delete via existing \`deletedAt\` field; Room's live stream carries the event so the item disappears immediately
- Pagination and search queries updated to exclude deleted rows
- New \`TransactionRepository.delete()\` API with tests

## Test plan
- [ ] Long-press a transaction → popup with red Delete item appears
- [ ] Tap Delete → item removed immediately, no confirmation
- [ ] Tap outside popup → dismissed, item unchanged
- [ ] Add a transaction, delete it, re-open app → transaction absent
- [ ] All unit tests pass: \`./gradlew testDebugUnitTest\`"
```
