# Account Detail Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Account Detail screen that opens when the user taps any account row, showing a collapsible hero card (balance + monthly in/out stats) above a transaction list filtered to that account.

**Architecture:** Mirrors `CategoryDetailComponent` exactly — `AccountDetailComponent` owns a `DefaultAccountDetailViewModel` (collects `AccountUseCase.state` + `AccountDetailSpendingUseCase`), an `AccountDetailViewProvider` (collapsible hero card + `TransactionComponent`), and a Dagger component wired via `ActivityComponent`. New `TransactionFilter.ForAccount` / `TransactionRepository.Criteria.ForAccount[Between]` feed the existing transaction infrastructure.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger 2, Room, Kotlin Coroutines/Flow, kotlinx-datetime

---

## File Map

| Action | Path | Responsibility |
|--------|------|---------------|
| Modify | `zero-core/…/transactions/TransactionFilter.kt` | Add `ForAccount(accountId)` variant |
| Modify | `zero-api/…/transactions/TransactionRepository.kt` | Add `Criteria.ForAccount` + `ForAccountBetween` |
| Modify | `zero-database/…/transactions/TransactionRoom.kt` | Add two Room `@Query` methods |
| Modify | `zero-database/…/transactions/RoomTransactionRepository.kt` | Handle two new criteria |
| Modify | `zero-core/…/transactions/DefaultTransactionViewModel.kt` | Handle `TransactionFilter.ForAccount` |
| Create | `zero-ui/…/ui/DetailTopBar.kt` | Shared top bar composable |
| Create | `zero-ui/…/ui/DetailStatColumn.kt` | Shared stat column composable |
| Modify | `zero-core/…/categories/detail/CategoryDetailViewProvider.kt` | Use shared DetailTopBar + DetailStatColumn |
| Create | `zero-api/…/accounts/AccountDetailSpendingUseCase.kt` | Interface: monthly in/out/count for an account |
| Create | `zero-core/…/accounts/detail/DefaultAccountDetailSpendingUseCase.kt` | Implementation |
| Create | `zero-core/…/accounts/detail/AccountDetailViewModel.kt` | ViewModel interface (State + Action) |
| Create | `zero-core/…/accounts/detail/DefaultAccountDetailViewModel.kt` | ViewModel implementation |
| Create | `zero-core/…/accounts/detail/AccountDetailViewProvider.kt` | Composable UI |
| Create | `zero-core/…/accounts/detail/AccountDetailComponent.kt` | Dagger component |
| Create | `zero-core/…/accounts/OnAccountSelectedHandler.kt` | Fun interface for account tap |
| Modify | `zero-core/…/accounts/AccountViewModel.kt` | Add `Action.Select(accountId)` |
| Modify | `zero-core/…/accounts/DefaultAccountViewModel.kt` | Dispatch Select → handler |
| Modify | `zero-core/…/accounts/AccountComponent.kt` | Add `onAccountSelectedHandler` builder |
| Modify | `zero-core/…/accounts/AccountViewProvider.kt` | Make `AccountRow` clickable |
| Modify | `app/…/navigation/Destinations.kt` | Add `Account.Item.Detail` destination |
| Modify | `app/…/screens/MainActivityScreenComponent.kt` | Add detail builder + navigation entry |
| Modify | `app/…/activity/ActivityComponent.kt` | Implement `AccountDetailComponent.Dependencies` |

---

## Task 0: Create feature branch and commit spec

**Files:** git, `docs/superpowers/specs/2026-05-06-account-detail-screen-design.md`

- [ ] **Step 1: Create branch**

```bash
git checkout -b feature/account-detail
```

- [ ] **Step 2: Stage and commit the spec**

```bash
git add docs/superpowers/specs/2026-05-06-account-detail-screen-design.md docs/superpowers/plans/2026-05-06-account-detail-screen.md
git commit -m "docs: add account detail screen spec and implementation plan"
```

---

## Task 1: Data layer — TransactionFilter, Criteria, Room DAO, Repository

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilter.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`

- [ ] **Step 1: Add `ForAccount` to `TransactionFilter`**

Replace the contents of `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilter.kt`:

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

sealed interface TransactionFilter {
    data object All : TransactionFilter
    data class ForCategory(val categoryId: Id.Known) : TransactionFilter
    data class ForAccount(val accountId: Id.Known) : TransactionFilter
}
```

- [ ] **Step 2: Add `ForAccount` and `ForAccountBetween` criteria to `TransactionRepository`**

In `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`, add inside the `Criteria` sealed interface, after `ForCategoryBetween`:

```kotlin
        data class ForAccount(val accountId: Id.Known) : Criteria<List<Transaction>>
        data class ForAccountBetween(
            val accountId: Id.Known,
            val from: LocalDate,
            val to: LocalDate,
        ) : Criteria<List<Transaction>>
```

(The existing `import kotlinx.datetime.LocalDate` already covers the `LocalDate` reference.)

- [ ] **Step 3: Add Room DAO queries to `TransactionRoom`**

In `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`, add after `selectByCategoryBetween`:

```kotlin
    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId     = :userId
          AND accountId  = :accountId
          AND deletedAt  IS NULL
        ORDER BY datetime(enteredDateTime) DESC
    """,
    )
    fun selectByAccount(userId: String, accountId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId     = :userId
          AND accountId  = :accountId
          AND deletedAt  IS NULL
          AND date(enteredDateTime) >= date(:from)
          AND date(enteredDateTime) <= date(:to)
        ORDER BY datetime(enteredDateTime) DESC
    """,
    )
    fun selectByAccountBetween(
        userId: String,
        accountId: String,
        from: String,
        to: String,
    ): Flow<List<TransactionEntity>>
```

- [ ] **Step 4: Handle new criteria in `RoomTransactionRepository`**

In `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`, inside the `when (criteria)` block after the `ForCategoryBetween` arm, add:

```kotlin
                is TransactionRepository.Criteria.ForAccount -> transactionRoom()
                    .selectByAccount(userId.value, criteria.accountId.value)
                    .map { entities -> entities.mapNotNull { it.toRepository() } }

                is TransactionRepository.Criteria.ForAccountBetween -> transactionRoom()
                    .selectByAccountBetween(
                        userId = userId.value,
                        accountId = criteria.accountId.value,
                        from = criteria.from.toString(),
                        to = criteria.to.toString(),
                    )
                    .map { entities -> entities.mapNotNull { it.toRepository() } }
```

- [ ] **Step 5: Verify the module compiles**

```bash
./gradlew :zero-api:assembleDebug :zero-database:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilter.kt \
        zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt
git commit -m "feat: add ForAccount/ForAccountBetween transaction criteria and Room queries"
```

---

## Task 2: Handle `TransactionFilter.ForAccount` in `DefaultTransactionViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`

- [ ] **Step 1: Add `forAccountTransactionsFlow` private function**

In `DefaultTransactionViewModel.kt`, add after `forCategoryTransactionsFlow`:

```kotlin
    private fun forAccountTransactionsFlow(
        filter: TransactionFilter.ForAccount,
    ): Flow<List<TransactionRepository.Transaction>> = transactionRepository
        .query(TransactionRepository.Criteria.ForAccount(filter.accountId))
        .onStartWithEmptyList()
        .onEmptyReturnEmptyList()
```

- [ ] **Step 2: Handle `ForAccount` in the `when(filter)` block**

In `attach()`, find:

```kotlin
            val pagedTransactions: Flow<List<TransactionRepository.Transaction>> = when (filter) {
                TransactionFilter.All -> allTransactionsFlow(initialTimestamp)
                is TransactionFilter.ForCategory -> forCategoryTransactionsFlow(filter)
            }
```

Replace with:

```kotlin
            val pagedTransactions: Flow<List<TransactionRepository.Transaction>> = when (filter) {
                TransactionFilter.All -> allTransactionsFlow(initialTimestamp)
                is TransactionFilter.ForCategory -> forCategoryTransactionsFlow(filter)
                is TransactionFilter.ForAccount -> forAccountTransactionsFlow(filter)
            }
```

- [ ] **Step 3: Compile zero-core**

```bash
./gradlew :zero-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
git commit -m "feat: handle TransactionFilter.ForAccount in DefaultTransactionViewModel"
```

---

## Task 3: Extract shared UI composables + refactor CategoryDetailViewProvider

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DetailTopBar.kt`
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DetailStatColumn.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt`

- [ ] **Step 1: Create `DetailTopBar.kt`**

Create `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DetailTopBar.kt`:

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.PrimaryContainer

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PrimaryContainer,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryContainer,
            ),
        )
        Box {
            trailing()
        }
    }
}
```

- [ ] **Step 2: Create `DetailStatColumn.kt`**

Create `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DetailStatColumn.kt`:

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DetailStatColumn(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    valueFontSize: TextUnit = 17.sp,
) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
                letterSpacing = 1.2.sp,
            ),
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            ),
        )
    }
}
```

- [ ] **Step 3: Refactor `CategoryDetailViewProvider` to use shared composables**

Replace the private `TopBar` function:

```kotlin
// DELETE the private TopBar function entirely — it is replaced by DetailTopBar from zero-ui
```

Replace the private `StatColumn` function:

```kotlin
// DELETE the private StatColumn function entirely — it is replaced by DetailStatColumn from zero-ui
```

Replace the call site in `CategoryDetailViewProvider.View()`:

Find:
```kotlin
            TopBar(state.categoryName, viewModel)
```
Replace with:
```kotlin
            DetailTopBar(
                title = state.categoryName,
                onBack = { viewModel.perform(CategoryDetailViewModel.Action.Back) },
                trailing = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = PrimaryContainer,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                menuExpanded = false
                                viewModel.perform(CategoryDetailViewModel.Action.Edit)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Edit category")
                        }
                    }
                },
            )
```

Replace the three `StatColumn(...)` calls inside `HeroCard`:

Find (in `HeroCard`):
```kotlin
            Row {
                StatColumn(
                    label = "TRANSACTIONS",
                    value = state.transactionCount.toString(),
                    colorScheme = colorScheme,
                )
                Spacer(Modifier.width(24.dp))
                StatColumn(
                    label = "AVG PER TX",
                    value = amountFormatter.format(state.averageAmount, state.currencySymbol),
                    colorScheme = colorScheme,
                )
                Spacer(Modifier.width(24.dp))
                StatColumn(
                    label = "LARGEST",
                    value = amountFormatter.format(state.largestAmount, state.currencySymbol),
                    colorScheme = colorScheme,
                )
            }
```

Replace with:
```kotlin
            Row {
                DetailStatColumn(
                    label = "TRANSACTIONS",
                    value = state.transactionCount.toString(),
                    labelColor = colorScheme.primary.copy(alpha = 0.7f),
                    valueColor = colorScheme.primary,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = "AVG PER TX",
                    value = amountFormatter.format(state.averageAmount, state.currencySymbol),
                    labelColor = colorScheme.primary.copy(alpha = 0.7f),
                    valueColor = colorScheme.primary,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = "LARGEST",
                    value = amountFormatter.format(state.largestAmount, state.currencySymbol),
                    labelColor = colorScheme.primary.copy(alpha = 0.7f),
                    valueColor = colorScheme.primary,
                )
            }
```

Add these imports to `CategoryDetailViewProvider.kt` (keep existing ones, add new):
```kotlin
import com.hluhovskyi.zero.ui.DetailStatColumn
import com.hluhovskyi.zero.ui.DetailTopBar
```

Remove no-longer-needed imports from `CategoryDetailViewProvider.kt`:
```kotlin
// Remove: import androidx.compose.material.icons.filled.ArrowBack  (used only in deleted TopBar)
// Keep: all other imports still needed by HeroCard, DropdownMenu, etc.
```

- [ ] **Step 4: Compile**

```bash
./gradlew :zero-ui:assembleDebug :zero-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/DetailTopBar.kt \
        zero-ui/src/main/java/com/hluhovskyi/zero/ui/DetailStatColumn.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt
git commit -m "refactor: extract DetailTopBar and DetailStatColumn shared composables, update CategoryDetailViewProvider"
```

---

## Task 4: `AccountDetailSpendingUseCase` interface and implementation

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountDetailSpendingUseCase.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCase.kt`

- [ ] **Step 1: Create the interface in `zero-api`**

Create `zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountDetailSpendingUseCase.kt`:

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AccountDetailSpendingUseCase {

    fun queryForAccount(accountId: Id.Known, period: Period): Flow<AccountSpending?>

    data class AccountSpending(
        val totalIn: Amount,
        val totalOut: Amount,
        val transactionCount: Int,
    )

    sealed interface Period {
        object CurrentMonth : Period
    }

    object Noop : AccountDetailSpendingUseCase {
        override fun queryForAccount(accountId: Id.Known, period: Period): Flow<AccountSpending?> = emptyFlow()
    }
}
```

- [ ] **Step 2: Write a failing test for the implementation**

Create `zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCaseTest.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultAccountDetailSpendingUseCaseTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    private val accountId = Id.Known("acc1")
    private val fixedInstant = Instant.parse("2026-05-15T12:00:00Z")
    private val fakeClock = object : Clock { override fun now() = fixedInstant }
    private val fakeZone = object : ZoneProvider { override fun timeZone() = TimeZone.UTC }

    private fun createUseCase() = DefaultAccountDetailSpendingUseCase(
        transactionRepository = transactionRepository,
        clock = fakeClock,
        zoneProvider = fakeZone,
    )

    @Test
    fun `returns null when no transactions`() = runTest {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForAccountBetween>()))
            .thenReturn(flowOf(emptyList()))

        val result = createUseCase()
            .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
            .first()

        assertNull(result)
    }

    @Test
    fun `sums income as totalIn and expense as totalOut`() = runTest {
        val income = makeIncome(amount = BigDecimal("1000.00"))
        val expense = makeExpense(amount = BigDecimal("250.00"))
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForAccountBetween>()))
            .thenReturn(flowOf(listOf(income, expense)))

        val result = createUseCase()
            .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
            .first()!!

        assertEquals(0, BigDecimal("1000.00").compareTo(result.totalIn.value))
        assertEquals(0, BigDecimal("250.00").compareTo(result.totalOut.value))
    }

    @Test
    fun `counts only income and expense, not transfers`() = runTest {
        val income = makeIncome(amount = BigDecimal("500.00"))
        val transfer = makeTransfer()
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForAccountBetween>()))
            .thenReturn(flowOf(listOf(income, transfer)))

        val result = createUseCase()
            .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
            .first()!!

        assertEquals(1, result.transactionCount)
    }

    private fun makeIncome(amount: BigDecimal) = TransactionRepository.Transaction.Income(
        id = Id.Known("tx-income"),
        amount = Amount(amount),
        accountId = accountId,
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("2026-05-10T10:00:00"),
        updatedDateTime = LocalDateTime.parse("2026-05-10T10:00:00"),
        categoryId = Id.Known("cat1"),
        rate = com.hluhovskyi.zero.common.Rate(BigDecimal.ONE),
    )

    private fun makeExpense(amount: BigDecimal) = TransactionRepository.Transaction.Expense(
        id = Id.Known("tx-expense"),
        amount = Amount(amount),
        accountId = accountId,
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("2026-05-12T10:00:00"),
        updatedDateTime = LocalDateTime.parse("2026-05-12T10:00:00"),
        categoryId = Id.Known("cat1"),
        rate = com.hluhovskyi.zero.common.Rate(BigDecimal.ONE),
    )

    private fun makeTransfer() = TransactionRepository.Transaction.Transfer(
        id = Id.Known("tx-transfer"),
        amount = Amount(BigDecimal("200.00")),
        accountId = accountId,
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("2026-05-14T10:00:00"),
        updatedDateTime = LocalDateTime.parse("2026-05-14T10:00:00"),
        targetAccount = Id.Known("acc2"),
        targetAmount = Amount(BigDecimal("200.00")),
    )
}
```

- [ ] **Step 3: Run the test to confirm it fails**

```bash
./gradlew :zero-core:test --tests "*.DefaultAccountDetailSpendingUseCaseTest"
```

Expected: compilation error (class not found)

- [ ] **Step 4: Create the implementation**

Create `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCase.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

internal class DefaultAccountDetailSpendingUseCase(
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : AccountDetailSpendingUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun queryForAccount(
        accountId: Id.Known,
        period: AccountDetailSpendingUseCase.Period,
    ): Flow<AccountDetailSpendingUseCase.AccountSpending?> {
        val (from, to) = period.resolve()
        return transactionRepository
            .query(TransactionRepository.Criteria.ForAccountBetween(accountId, from, to))
            .flatMapLatest { transactions -> flow { emit(aggregate(transactions)) } }
    }

    private fun aggregate(
        transactions: List<TransactionRepository.Transaction>,
    ): AccountDetailSpendingUseCase.AccountSpending? {
        if (transactions.isEmpty()) return null
        var totalIn = Amount.zero()
        var totalOut = Amount.zero()
        var count = 0
        for (tx in transactions) {
            when (tx) {
                is TransactionRepository.Transaction.Income -> {
                    totalIn += tx.amount
                    count++
                }
                is TransactionRepository.Transaction.Expense -> {
                    totalOut += tx.amount
                    count++
                }
                is TransactionRepository.Transaction.Transfer -> Unit
            }
        }
        return AccountDetailSpendingUseCase.AccountSpending(
            totalIn = totalIn,
            totalOut = totalOut,
            transactionCount = count,
        )
    }

    private fun AccountDetailSpendingUseCase.Period.resolve(): Pair<LocalDate, LocalDate> {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        return when (this) {
            is AccountDetailSpendingUseCase.Period.CurrentMonth ->
                LocalDate(today.year, today.month, 1) to today
        }
    }
}
```

- [ ] **Step 5: Run the test — expect green**

```bash
./gradlew :zero-core:test --tests "*.DefaultAccountDetailSpendingUseCaseTest"
```

Expected: `BUILD SUCCESSFUL`, all tests pass

- [ ] **Step 6: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountDetailSpendingUseCase.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCase.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCaseTest.kt
git commit -m "feat: add AccountDetailSpendingUseCase and implementation"
```

---

## Task 5: `AccountDetailViewModel` interface + `DefaultAccountDetailViewModel` + tests

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt`
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt`

- [ ] **Step 1: Create the interface**

Create `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

interface AccountDetailViewModel : AttachableActionStateModel<AccountDetailViewModel.Action, AccountDetailViewModel.State> {

    sealed interface Action {
        object Back : Action
    }

    data class State(
        val accountName: String = "",
        val accountIcon: Image = Image.empty(),
        val accountDetails: String? = null,
        val balance: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val isNegativeBalance: Boolean = false,
        val periodDate: LocalDate? = null,
        val totalIn: Amount = Amount.zero(),
        val totalOut: Amount = Amount.zero(),
        val transactionCount: Int = 0,
    )

    object Noop : AccountDetailViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): java.io.Closeable = java.io.Closeable { }
    }
}
```

- [ ] **Step 2: Write failing tests**

Create `zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.Account
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.accounts.AccountUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultAccountDetailViewModelTest {

    @Mock private lateinit var accountUseCase: AccountUseCase
    @Mock private lateinit var spendingUseCase: AccountDetailSpendingUseCase

    private val accountId = Id.Known("acc1")
    private val fixedInstant = Instant.parse("2026-05-15T12:00:00Z")
    private val fakeClock = object : Clock { override fun now() = fixedInstant }
    private val fakeZone = object : ZoneProvider { override fun timeZone() = TimeZone.UTC }

    @Before
    fun setUp() {
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
        whenever(spendingUseCase.queryForAccount(any(), any())).thenReturn(flowOf(null))
    }

    @Test
    fun `state reflects account name from accountUseCase`() = runTest {
        val account = makeAccount(name = "Chase Sapphire")
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(account))))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertEquals("Chase Sapphire", vm.state.first().accountName)
    }

    @Test
    fun `state reflects balance and currencySymbol from account`() = runTest {
        val account = makeAccount(balance = Amount(BigDecimal("12480.00")), currencySymbol = "$")
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(account))))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals(0, BigDecimal("12480.00").compareTo(state.balance.value))
        assertEquals("$", state.currencySymbol)
    }

    @Test
    fun `isNegativeBalance is true when balance is negative`() = runTest {
        val account = makeAccount(balance = Amount(BigDecimal("-1240.00")))
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(account))))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertTrue(vm.state.first().isNegativeBalance)
    }

    @Test
    fun `state totalIn and totalOut come from spendingUseCase`() = runTest {
        val spending = AccountDetailSpendingUseCase.AccountSpending(
            totalIn = Amount(BigDecimal("5000.00")),
            totalOut = Amount(BigDecimal("3000.00")),
            transactionCount = 8,
        )
        whenever(spendingUseCase.queryForAccount(any(), any())).thenReturn(flowOf(spending))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals(0, BigDecimal("5000.00").compareTo(state.totalIn.value))
        assertEquals(0, BigDecimal("3000.00").compareTo(state.totalOut.value))
        assertEquals(8, state.transactionCount)
    }

    @Test
    fun `periodDate is first day of current month`() = runTest {
        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        // fixedInstant = 2026-05-15 UTC → periodDate = 2026-05-01
        assertEquals(LocalDate(2026, 5, 1), vm.state.first().periodDate)
    }

    private fun makeAccount(
        name: String = "Test Account",
        balance: Amount = Amount.zero(),
        currencySymbol: String = "$",
    ) = Account(
        id = accountId,
        name = name,
        balance = balance,
        currencySymbol = currencySymbol,
        icon = Image.empty(),
        category = AccountCategory.BANK,
        details = null,
    )

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultAccountDetailViewModel(
        accountId = accountId,
        accountUseCase = accountUseCase,
        accountDetailSpendingUseCase = spendingUseCase,
        onBackHandler = OnBackHandler.Noop,
        clock = fakeClock,
        zoneProvider = fakeZone,
        coroutineScope = coroutineScope,
    )
}
```

- [ ] **Step 3: Run tests — expect compilation failure**

```bash
./gradlew :zero-core:test --tests "*.DefaultAccountDetailViewModelTest"
```

Expected: compilation error (class not found)

- [ ] **Step 4: Create the implementation**

Create `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.accounts.AccountUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import java.io.Closeable
import java.math.BigDecimal

internal class DefaultAccountDetailViewModel(
    private val accountId: Id.Known,
    private val accountUseCase: AccountUseCase,
    private val accountDetailSpendingUseCase: AccountDetailSpendingUseCase,
    private val onBackHandler: OnBackHandler,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AccountDetailViewModel {

    private val mutableState = MutableStateFlow(AccountDetailViewModel.State())
    override val state: Flow<AccountDetailViewModel.State> = mutableState

    override fun perform(action: AccountDetailViewModel.Action) {
        when (action) {
            AccountDetailViewModel.Action.Back -> coroutineScope.launch(Dispatchers.Main) {
                onBackHandler.onBack()
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        val periodDate = LocalDate(today.year, today.month, 1)
        mutableState.update { it.copy(periodDate = periodDate) }

        coroutineScope.launch {
            launch {
                accountUseCase.state.collectLatest { useCaseState ->
                    val account = useCaseState.accounts.find { it.id == accountId }
                        ?: return@collectLatest
                    mutableState.update { s ->
                        s.copy(
                            accountName = account.name,
                            accountIcon = account.icon,
                            accountDetails = account.details,
                            balance = account.balance,
                            currencySymbol = account.currencySymbol,
                            isNegativeBalance = account.balance.value < BigDecimal.ZERO,
                        )
                    }
                }
            }

            launch {
                accountDetailSpendingUseCase
                    .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
                    .collectLatest { spending ->
                        mutableState.update { s ->
                            s.copy(
                                totalIn = spending?.totalIn ?: Amount.zero(),
                                totalOut = spending?.totalOut ?: Amount.zero(),
                                transactionCount = spending?.transactionCount ?: 0,
                            )
                        }
                    }
            }
        }
    }
}
```

- [ ] **Step 5: Run tests — expect green**

```bash
./gradlew :zero-core:test --tests "*.DefaultAccountDetailViewModelTest"
```

Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt
git commit -m "feat: add AccountDetailViewModel and DefaultAccountDetailViewModel with tests"
```

---

## Task 6: `AccountDetailViewProvider`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt`

- [ ] **Step 1: Create the ViewProvider**

Create `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.ui.DetailStatColumn
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.ErrorContainer
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.Secondary
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal class AccountDetailViewProvider(
    private val viewModel: AccountDetailViewModel,
    private val transactionComponent: TransactionComponent,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = AccountDetailViewModel.State())

        val heroHeightPx = remember { mutableStateOf(0) }
        val heroOffsetPx = remember { mutableStateOf(0f) }

        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f) return Offset.Zero
                    val newOffset = (heroOffsetPx.value + available.y)
                        .coerceIn(-heroHeightPx.value.toFloat(), 0f)
                    val consumed = newOffset - heroOffsetPx.value
                    heroOffsetPx.value = newOffset
                    return Offset(0f, consumed)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y <= 0f) return Offset.Zero
                    val newOffset = (heroOffsetPx.value + available.y).coerceAtMost(0f)
                    val delta = newOffset - heroOffsetPx.value
                    heroOffsetPx.value = newOffset
                    return Offset(0f, delta)
                }
            }
        }

        Box(Modifier.fillMaxSize().nestedScroll(connection)) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(
                    title = state.accountName,
                    onBack = { viewModel.perform(AccountDetailViewModel.Action.Back) },
                )
                Box(Modifier.weight(1f)) {
                    val topPaddingDp = with(LocalDensity.current) {
                        (heroHeightPx.value + heroOffsetPx.value).coerceAtLeast(0f).toDp()
                    }
                    Box(Modifier.fillMaxSize().padding(top = topPaddingDp)) {
                        transactionComponent.AttachWithView()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { heroHeightPx.value = it.height }
                            .offset { IntOffset(0, heroOffsetPx.value.roundToInt()) },
                    ) {
                        HeroCard(state, amountFormatter, imageLoader)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: AccountDetailViewModel.State,
    amountFormatter: AmountFormatter,
    imageLoader: ImageLoader,
) {
    val isNeg = state.isNegativeBalance
    val heroBackground = if (isNeg) ErrorContainer else SurfaceContainerLow
    val balanceColor = if (isNeg) Error else Primary
    val accentColor = if (isNeg) Error else Primary
    val inValueColor = if (isNeg) Error else Secondary

    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(heroBackground)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.08f)
                .size(80.dp),
        ) {
            imageLoader.View(
                image = state.accountIcon,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            val periodLabel = state.accountDetails?.uppercase()
                ?: state.periodDate
                    ?.toJavaLocalDate()
                    ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    ?.uppercase()
                    .orEmpty()

            Text(
                text = periodLabel,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor.copy(alpha = 0.75f),
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = amountFormatter.format(state.balance, state.currencySymbol),
                style = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = balanceColor,
                    letterSpacing = (-0.68).sp,
                ),
            )
            Spacer(Modifier.size(16.dp))
            Row {
                DetailStatColumn(
                    label = "IN THIS MONTH",
                    value = "+${amountFormatter.format(state.totalIn, state.currencySymbol)}",
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = inValueColor,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = "OUT THIS MONTH",
                    value = "–${amountFormatter.format(state.totalOut, state.currencySymbol)}",
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = OnSurface,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = "TRANSACTIONS",
                    value = state.transactionCount.toString(),
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = OnSurface,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :zero-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt
git commit -m "feat: add AccountDetailViewProvider"
```

---

## Task 7: `AccountDetailComponent` + `OnAccountDetailEditHandler`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt`

- [ ] **Step 1: Create the Dagger component**

Create `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.AccountUseCase
import com.hluhovskyi.zero.accounts.DefaultAccountUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.DisplayConfig
import com.hluhovskyi.zero.transactions.OnTransactionSelectedHandler
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountDetailScope

private const val TAG = "AccountDetailComponent"

@AccountDetailScope
@dagger.Component(
    modules = [AccountDetailComponent.Module::class],
    dependencies = [AccountDetailComponent.Dependencies::class],
)
abstract class AccountDetailComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountDetailViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val transactionComponentBuilder: TransactionComponent.Builder

        val accountRepository: AccountRepository
        val transactionRepository: TransactionRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase

        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerAccountDetailComponent.builder()
            .dependencies(dependencies)
            .onBackHandler(OnBackHandler.Noop)
            .onTransactionSelectedHandler(OnTransactionSelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountDetailComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun accountId(id: Id.Known): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder

        @BindsInstance
        fun onTransactionSelectedHandler(handler: OnTransactionSelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountDetailScope
        fun accountUseCase(
            accountRepository: AccountRepository,
            transactionRepository: TransactionRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            currencyConvertUseCase: CurrencyConvertUseCase,
        ): AccountUseCase = DefaultAccountUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
        )

        @Provides
        @AccountDetailScope
        fun accountDetailSpendingUseCase(
            transactionRepository: TransactionRepository,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): AccountDetailSpendingUseCase = DefaultAccountDetailSpendingUseCase(
            transactionRepository = transactionRepository,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @AccountDetailScope
        fun transactionComponent(
            builder: TransactionComponent.Builder,
            accountId: Id.Known,
            onTransactionSelectedHandler: OnTransactionSelectedHandler,
        ): TransactionComponent = builder
            .transactionFilter(TransactionFilter.ForAccount(accountId))
            .displayConfig(DisplayConfig(showSearchBar = false))
            .onTransactionSelectHandler(onTransactionSelectedHandler)
            .build()

        @Provides
        @AccountDetailScope
        fun viewModel(
            accountId: Id.Known,
            accountUseCase: AccountUseCase,
            accountDetailSpendingUseCase: AccountDetailSpendingUseCase,
            onBackHandler: OnBackHandler,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): AccountDetailViewModel = DefaultAccountDetailViewModel(
            accountId = accountId,
            accountUseCase = accountUseCase,
            accountDetailSpendingUseCase = accountDetailSpendingUseCase,
            onBackHandler = onBackHandler,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @AccountDetailScope
        fun viewProvider(
            viewModel: AccountDetailViewModel,
            transactionComponent: TransactionComponent,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = AccountDetailViewProvider(
            viewModel = viewModel,
            transactionComponent = transactionComponent,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
```

- [ ] **Step 2: Compile zero-core**

```bash
./gradlew :zero-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt
git commit -m "feat: add AccountDetailComponent (Dagger)"
```

---

## Task 8: `OnAccountSelectedHandler` + `AccountViewModel`/`DefaultAccountViewModel`/`AccountComponent`/`AccountViewProvider` changes

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnAccountSelectedHandler.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`

- [ ] **Step 1: Create `OnAccountSelectedHandler`**

Create `zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnAccountSelectedHandler.kt`:

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id

fun interface OnAccountSelectedHandler {

    fun onSelected(accountId: Id.Known)

    object Noop : OnAccountSelectedHandler {
        override fun onSelected(accountId: Id.Known) = Unit
    }
}
```

- [ ] **Step 2: Add `Select` action to `AccountViewModel`**

Replace `AccountViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id

interface AccountViewModel : AttachableActionStateModel<AccountViewModel.Action, AccountViewModel.State> {

    sealed interface Action {
        data class Select(val accountId: Id.Known) : Action
    }

    data class State(
        val balance: Amount = Amount.zero(),
        val currency: Currency? = null,
        val accounts: List<Account> = emptyList(),
    )
}
```

- [ ] **Step 3: Handle `Select` in `DefaultAccountViewModel`**

In `DefaultAccountViewModel.kt`, inject `OnAccountSelectedHandler` and handle the action:

Replace:

```kotlin
internal class DefaultAccountViewModel(
    private val useCase: AccountUseCase,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    AccountViewModel {
```

With:

```kotlin
internal class DefaultAccountViewModel(
    private val useCase: AccountUseCase,
    private val dispatchers: DispatcherProvider,
    private val onAccountSelectedHandler: OnAccountSelectedHandler = OnAccountSelectedHandler.Noop,
) : BaseViewModel(dispatchers),
    AccountViewModel {
```

Replace the `perform` function:

```kotlin
    override fun perform(action: AccountViewModel.Action) {
        when (action) {
            is AccountViewModel.Action.Select -> scope.launch(dispatchers.main) {
                onAccountSelectedHandler.onSelected(action.accountId)
            }
        }
    }
```

Add import at the top of the file:
```kotlin
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
```
(already imported — verify it's present)

- [ ] **Step 4: Add `onAccountSelectedHandler` to `AccountComponent`**

In `AccountComponent.kt`, add to the `Builder` interface:

```kotlin
        @BindsInstance
        fun onAccountSelectedHandler(handler: OnAccountSelectedHandler): Builder
```

Update `companion object`:

```kotlin
        fun builder(dependencies: Dependencies): Builder = DaggerAccountComponent.builder()
            .dependencies(dependencies)
            .onAddAccountHandler(OnAddAccountHandler.Noop)
            .onAccountSelectedHandler(OnAccountSelectedHandler.Noop)
```

Update the `viewModel` @Provides method in `Module`:

```kotlin
        @Provides
        @AccountScope
        fun viewModel(
            useCase: AccountUseCase,
            dispatcherProvider: DispatcherProvider,
            onAccountSelectedHandler: OnAccountSelectedHandler,
        ): AccountViewModel = DefaultAccountViewModel(
            useCase = useCase,
            dispatchers = dispatcherProvider,
            onAccountSelectedHandler = onAccountSelectedHandler,
        )
```

- [ ] **Step 5: Make `AccountRow` clickable in `AccountViewProvider`**

In `AccountViewProvider.kt`, the `AccountView` function receives a `viewModel`. The `AccountRow` currently has no click handling.

Replace the `AccountRow` composable call site (inside `AccountView`'s `LazyColumn`):

```kotlin
            items(accounts, key = { it.id.value }) { account ->
                AccountRow(
                    account = account,
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                    onClick = { viewModel.perform(AccountViewModel.Action.Select(account.id)) },
                )
            }
```

Update the `AccountRow` composable signature and add `clickable`:

```kotlin
@Composable
private fun AccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(SurfaceContainerLowest, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // rest of the body unchanged
```

The existing `AccountViewProvider` file already imports `androidx.compose.foundation.clickable` — if not, add it.

Also, update the `AccountViewProvider` class constructor to expose the viewModel reference to `AccountView`. The current structure has `AccountViewProvider` wrapping `AccountView` as a function. Update:

```kotlin
internal class AccountViewProvider(
    private val viewModel: AccountViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val onAddAccount: OnAddAccountHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddAccount = onAddAccount,
        )
    }
}

@Composable
private fun AccountView(
    viewModel: AccountViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onAddAccount: OnAddAccountHandler,
) {
```

(This already passes `viewModel` to `AccountView` — ensure the full function passes it down to `AccountRow`.)

- [ ] **Step 6: Compile**

```bash
./gradlew :zero-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnAccountSelectedHandler.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt
git commit -m "feat: add OnAccountSelectedHandler and make account rows clickable"
```

---

## Task 9: Navigation wiring — `Destinations`, `MainActivityScreenComponent`, `ActivityComponent`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`

- [ ] **Step 1: Add `Account.Item.Detail` destination**

In `Destinations.kt`, replace:

```kotlin
    sealed interface Account : Destination {
        object All : Account, Destination by destinationOf("accounts")
        object Edit : Account, Destination by destinationOf("accounts/edit")
    }
```

With:

```kotlin
    sealed interface Account : Destination {
        object All : Account, Destination by destinationOf("accounts")
        object Edit : Account, Destination by destinationOf("accounts/edit")

        sealed interface Item : Account {
            object AccountId : Argument<Id.Known> by idKnownValueOf("accountId")
            object Detail : Item, Destination by destinationOf("accounts/{accountId}", AccountId)
        }
    }
```

- [ ] **Step 2: Add `accountDetailComponentBuilder` to `MainActivityScreenComponent.Dependencies`**

In `MainActivityScreenComponent.kt`, inside `interface Dependencies`, add after `accountEditComponentBuilder`:

```kotlin
        val accountDetailComponentBuilder: AccountDetailComponent.Builder
```

Add the import at the top of the file:
```kotlin
import com.hluhovskyi.zero.accounts.detail.AccountDetailComponent
```

- [ ] **Step 3: Update `accountNavigationEntry` to wire account selection**

In `MainActivityScreenComponent.Module`, find `accountNavigationEntry`:

```kotlin
        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountNavigationEntry(
            componentBuilder: AccountComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.composable(Destinations.Account.All) {
            AccountsScreen(
                component = componentBuilder
                    .onAddAccountHandler { navigator.navigateTo(Destinations.Account.Edit) }
                    .logging(logger),
            )
        }
```

Replace with:

```kotlin
        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountNavigationEntry(
            componentBuilder: AccountComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.composable(Destinations.Account.All) {
            AccountsScreen(
                component = componentBuilder
                    .onAddAccountHandler { navigator.navigateTo(Destinations.Account.Edit) }
                    .onAccountSelectedHandler { accountId ->
                        navigator.navigateTo(
                            Destinations.Account.Item.Detail,
                            Destinations.Account.Item.AccountId.withValue(accountId),
                        )
                    }
                    .logging(logger),
            )
        }
```

Add the import for `OnAccountSelectedHandler` if not present:
```kotlin
import com.hluhovskyi.zero.accounts.OnAccountSelectedHandler
```

- [ ] **Step 4: Add `accountDetailNavigationEntry`**

Add this new `@Provides` function inside `MainActivityScreenComponent.Module`, after `accountEditNavigationEntry`:

```kotlin
        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountDetailNavigationEntry(
            componentBuilder: AccountDetailComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Account.Item.Detail) {
            val accountId = arguments.getValue(Destinations.Account.Item.AccountId)
            componentBuilder
                .accountId(accountId)
                .onBackHandler { navigator.back() }
                .onTransactionSelectedHandler { transactionId ->
                    navigator.navigateTo(
                        Destinations.Transaction.Item.Edit,
                        Destinations.Transaction.Item.TransactionId.withValue(transactionId),
                    )
                }
                .logging(logger)
        }
```

- [ ] **Step 5: Add `AccountDetailComponent` to `ActivityComponent`**

In `ActivityComponent.kt`, add `AccountDetailComponent.Dependencies` to the implemented interfaces list:

```kotlin
abstract class ActivityComponent :
    AttachableViewComponent,
    BottomBarComponent.Dependencies,
    MainActivityScreenComponent.Dependencies,
    AccountComponent.Dependencies,
    AccountEditComponent.Dependencies,
    AccountDetailComponent.Dependencies,     // ← add this line
    CategoryComponent.Dependencies,
    CategoryDetailComponent.Dependencies,
    ...
```

Add the import:
```kotlin
import com.hluhovskyi.zero.accounts.detail.AccountDetailComponent
```

In `ActivityComponent.Module`, add a new `@Provides` method after `accountEditComponentBuilder`:

```kotlin
        @Provides
        @ActivityScope
        fun accountDetailComponentBuilder(
            component: ActivityComponent,
        ): AccountDetailComponent.Builder = AccountDetailComponent.builder(component)
```

- [ ] **Step 6: Full app build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run lint**

```bash
./gradlew lintDebug
```

Expected: no new errors

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt \
        app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt \
        app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt
git commit -m "feat: wire Account Detail navigation in Destinations, MainActivityScreenComponent, ActivityComponent"
```

---

## Task 10: UI verification with `android-ui-inspector`

**Use the `android-ui-inspector` skill** (via `./scripts/dump-ui.sh`).

- [ ] **Step 1: Install app on device**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Launch and navigate to Accounts tab, tap an account row**

Use ADB or manual interaction to open the Accounts screen and tap any account.

- [ ] **Step 3: Dump UI hierarchy**

```bash
./scripts/dump-ui.sh
```

Verify using the android-ui-inspector skill:
- Back arrow visible at top left
- Account name centered in top bar
- Hero card visible with balance text (large font)
- "IN THIS MONTH", "OUT THIS MONTH", "TRANSACTIONS" stat labels visible
- Transaction list shows below the hero card
- Tapping back returns to Accounts list

- [ ] **Step 4: Commit verification**

```bash
git add -A
git commit -m "chore: post-UI-verification cleanup (if any)" --allow-empty
```

---

## Task 11: Create pull request

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feature/account-detail
```

- [ ] **Step 2: Create the PR**

```bash
gh pr create \
  --title "feat: Account Detail screen (issue #68)" \
  --body "## Summary
- Tapping any account row opens a new Account Detail screen
- Hero card shows collapsible balance + IN THIS MONTH / OUT THIS MONTH / TRANSACTIONS stats
- Transaction list below (filtered to the selected account) reuses existing TransactionComponent
- Shared DetailTopBar + DetailStatColumn composables extracted to zero-ui, CategoryDetailViewProvider updated to use them
- Negative-balance accounts shown with red hero card (ErrorContainer background, Error text)
- Income transactions shown in green with '+' prefix (existing TransactionViewProvider behaviour)

## Closes
Fixes #68

## Test plan
- [ ] Tap an account on the Accounts screen — Account Detail opens
- [ ] Hero card shows correct account name, balance, and currency symbol
- [ ] Stats row shows IN/OUT/TRANSACTIONS with correct colours
- [ ] Negative-balance account shows red hero card
- [ ] Scrolling collapses the hero card
- [ ] Tapping a transaction row opens the edit screen
- [ ] Back arrow returns to Accounts list
- [ ] android-ui-inspector confirms all elements visible and correctly bounded

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Hero card with balance, period label, IN/OUT/TRANSACTIONS
- ✅ Negative balance → red card
- ✅ CollapsibleHero via NestedScrollConnection (same as CategoryDetail)
- ✅ No FAB (per design)
- ✅ TransactionFilter.ForAccount → TransactionComponent filtered list
- ✅ ForAccountBetween → spending stats use case
- ✅ DetailTopBar + DetailStatColumn extracted and shared
- ✅ CategoryDetailViewProvider refactored to use shared composables
- ✅ OnAccountSelectedHandler wired through AccountComponent
- ✅ AccountRow clickable
- ✅ Navigation: Destinations.Account.Item.Detail
- ✅ ActivityComponent implements AccountDetailComponent.Dependencies
- ✅ Income transactions display green in transaction list (existing behaviour, no code change needed)

**No placeholder scan:** All code blocks are complete. No TBD/TODO in any step.

**Type consistency:**
- `AccountDetailViewModel.State` fields match exactly what `AccountDetailViewProvider` reads and what `DefaultAccountDetailViewModel` sets
- `AccountDetailSpendingUseCase.AccountSpending` fields `totalIn`/`totalOut`/`transactionCount` match the ViewModel usage
- `TransactionFilter.ForAccount(accountId)` used in `AccountDetailComponent.Module.transactionComponent()` and `DefaultTransactionViewModel`
- `Destinations.Account.Item.AccountId` used consistently in `accountNavigationEntry` and `accountDetailNavigationEntry`
