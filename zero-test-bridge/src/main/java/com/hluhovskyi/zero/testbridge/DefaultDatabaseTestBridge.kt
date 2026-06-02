package com.hluhovskyi.zero.testbridge

import com.hluhovskyi.zero.CleanupJob
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.math.BigDecimal

fun DatabaseTestBridge(
    cleanupJob: CleanupJob,
    currentUserRepository: CurrentUserRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
    seedPresets: suspend () -> Unit,
): DatabaseTestBridge = DefaultDatabaseTestBridge(
    cleanupJob = cleanupJob,
    currentUserRepository = currentUserRepository,
    accountRepository = accountRepository,
    categoryRepository = categoryRepository,
    transactionRepository = transactionRepository,
    budgetRepository = budgetRepository,
    seedPresetsAction = seedPresets,
)

internal class DefaultDatabaseTestBridge(
    private val cleanupJob: CleanupJob,
    private val currentUserRepository: CurrentUserRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val seedPresetsAction: suspend () -> Unit,
) : DatabaseTestBridge {

    override suspend fun clearData() {
        cleanupJob.clearAllTables()
        seedPresetsAction()
    }

    override suspend fun seedDefaultSetup() {
        currentUserRepository.query().first()
        insertWalletAccount()
        insertFoodCategory()
        insertBootstrapExpense()
    }

    override suspend fun seedExpenses() {
        currentUserRepository.query().first()
        insertWalletAccount()
        insertFoodCategory()

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val noonToday = LocalDateTime(today.year, today.month, today.dayOfMonth, 12, 0)
        listOf("42" to "expense-42", "99" to "expense-99").forEach { (amount, id) ->
            transactionRepository.insert(
                TransactionRepository.Transaction.Expense(
                    id = Id.Known(id),
                    amount = Amount(BigDecimal(amount)),
                    accountId = Id.Known("test-account"),
                    currencyId = Id.Known("USD"),
                    dateTime = noonToday,
                    updatedDateTime = noonToday,
                    categoryId = Id.Known("test-category"),
                    rate = Rate.Same,
                ),
            )
        }
    }

    override suspend fun seedFxAccounts() {
        currentUserRepository.query().first()
        insertWalletAccount()
        insertWalletAccount(id = Id.Known("test-account-eur"), name = "Revolut", currencyId = Id.Known("EUR"))
        insertFoodCategory()
        insertBootstrapExpense()
    }

    override suspend fun seedBudgetOverScenario() {
        // Presets are seeded by BaseE2eTest after clearData; this scenario relies on
        // Food & Drink / Transport from that baseline.
        currentUserRepository.query().first()

        val currencyId = Id.Known("USD")
        val accountId = Id.Known("test-account")
        val iconId = IconRepository.unknownCategoryIconId()

        accountRepository.insert(
            AccountRepository.AccountInsert(
                id = accountId,
                name = "Wallet",
                currencyId = currencyId,
                iconId = iconId,
                colorId = Id.Known("blue"),
                initialBalance = Amount(BigDecimal.ZERO),
                category = AccountCategory.CASH,
            ),
        )

        val categories = categoryRepository.query(CategoryRepository.Criteria.All()).first()
        val target = categories.first { it.name == "Food & Drink" }
        val source = categories.first { it.name == "Transport" }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val periodStart = LocalDate(today.year, today.month, 1)
        val periodEnd = periodStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        budgetRepository.insert(
            listOf(
                BudgetRepository.BudgetInsert(
                    categoryId = target.id,
                    type = BudgetType.EXPENSE,
                    amount = Amount(BigDecimal("50")),
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                ),
                BudgetRepository.BudgetInsert(
                    categoryId = source.id,
                    type = BudgetType.EXPENSE,
                    amount = Amount(BigDecimal("200")),
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                ),
            ),
        )

        val noonToday = LocalDateTime(today.year, today.month, today.dayOfMonth, 12, 0)
        transactionRepository.insert(
            TransactionRepository.Transaction.Expense(
                id = Id.Known("seed-over-spend"),
                amount = Amount(BigDecimal("100")),
                accountId = accountId,
                currencyId = currencyId,
                dateTime = noonToday,
                updatedDateTime = noonToday,
                categoryId = target.id,
                rate = Rate.Same,
            ),
        )
    }

    private suspend fun insertWalletAccount(
        id: Id.Known = Id.Known("test-account"),
        name: String = "Wallet",
        currencyId: Id.Known = Id.Known("USD"),
    ) {
        accountRepository.insert(
            AccountRepository.AccountInsert(
                id = id,
                name = name,
                currencyId = currencyId,
                iconId = IconRepository.unknownCategoryIconId(),
                colorId = Id.Known("blue"),
                initialBalance = Amount(BigDecimal.ZERO),
                category = AccountCategory.CASH,
            ),
        )
    }

    private suspend fun insertFoodCategory(id: Id.Known = Id.Known("test-category")) {
        categoryRepository.insert(
            CategoryRepository.CategoryInsert(
                id = id,
                parentCategoryId = Id.Unknown,
                name = "Food",
                iconId = IconRepository.unknownCategoryIconId(),
                colorId = Id.Known("blue"),
                type = CategoryType.EXPENSE,
            ),
        )
    }

    /** A tiny, dateless expense so a freshly-seeded DB lands on Transactions, not Welcome. */
    private suspend fun insertBootstrapExpense(
        accountId: Id.Known = Id.Known("test-account"),
        currencyId: Id.Known = Id.Known("USD"),
        categoryId: Id.Known = Id.Known("test-category"),
    ) {
        val epoch = LocalDateTime(2020, 1, 1, 0, 0)
        transactionRepository.insert(
            TransactionRepository.Transaction.Expense(
                id = Id.Known("bootstrap"),
                amount = Amount(BigDecimal.ONE),
                accountId = accountId,
                currencyId = currencyId,
                dateTime = epoch,
                updatedDateTime = epoch,
                categoryId = categoryId,
                rate = Rate.Same,
            ),
        )
    }
}
