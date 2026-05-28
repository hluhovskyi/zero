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
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.math.BigDecimal
import kotlin.time.Duration.Companion.hours

fun DatabaseTestBridge(
    cleanupJob: CleanupJob,
    currentUserRepository: CurrentUserRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
): DatabaseTestBridge = DefaultDatabaseTestBridge(
    cleanupJob = cleanupJob,
    currentUserRepository = currentUserRepository,
    accountRepository = accountRepository,
    categoryRepository = categoryRepository,
    transactionRepository = transactionRepository,
    budgetRepository = budgetRepository,
)

internal class DefaultDatabaseTestBridge(
    private val cleanupJob: CleanupJob,
    private val currentUserRepository: CurrentUserRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
) : DatabaseTestBridge {

    override suspend fun clearData() {
        cleanupJob.clearAllTables()
    }

    override suspend fun seedDefaultSetup() {
        currentUserRepository.query().first()

        val iconId = IconRepository.unknownCategoryIconId()
        val colorId = Id.Known("blue")
        val currencyId = Id.Known("USD")
        val accountId = Id.Known("test-account")
        val categoryId = Id.Known("test-category")
        val epoch = LocalDateTime(2020, 1, 1, 0, 0)

        accountRepository.insert(
            AccountRepository.AccountInsert(
                id = accountId,
                name = "Wallet",
                currencyId = currencyId,
                iconId = iconId,
                colorId = colorId,
                initialBalance = Amount(BigDecimal.ZERO),
                category = AccountCategory.CASH,
            ),
        )

        categoryRepository.insert(
            CategoryRepository.CategoryInsert(
                id = categoryId,
                parentCategoryId = Id.Unknown,
                name = "Food",
                iconId = iconId,
                colorId = colorId,
                type = CategoryType.EXPENSE,
            ),
        )

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

    override suspend fun seedExpenses() {
        currentUserRepository.query().first()

        val iconId = IconRepository.unknownCategoryIconId()
        val colorId = Id.Known("blue")
        val currencyId = Id.Known("USD")
        val accountId = Id.Known("test-account")
        val categoryId = Id.Known("test-category")

        accountRepository.insert(
            AccountRepository.AccountInsert(
                id = accountId,
                name = "Wallet",
                currencyId = currencyId,
                iconId = iconId,
                colorId = colorId,
                initialBalance = Amount(BigDecimal.ZERO),
                category = AccountCategory.CASH,
            ),
        )

        categoryRepository.insert(
            CategoryRepository.CategoryInsert(
                id = categoryId,
                parentCategoryId = Id.Unknown,
                name = "Food",
                iconId = iconId,
                colorId = colorId,
                type = CategoryType.EXPENSE,
            ),
        )

        // Must be strictly in the future. The transaction list filters its live stream by
        // `updatedDateTime > attachTime`, where `attachTime` is captured by the screen's IO
        // coroutine that races this seed. A "now" stamp loses that race a meaningful share of
        // the time and the row never renders; a future buffer makes the seed unambiguously win.
        val seededAt = (Clock.System.now() + 1.hours).toLocalDateTime(TimeZone.currentSystemDefault())
        listOf("42" to "expense-42", "99" to "expense-99").forEach { (amount, id) ->
            transactionRepository.insert(
                TransactionRepository.Transaction.Expense(
                    id = Id.Known(id),
                    amount = Amount(BigDecimal(amount)),
                    accountId = accountId,
                    currencyId = currencyId,
                    dateTime = seededAt,
                    updatedDateTime = seededAt,
                    categoryId = categoryId,
                    rate = Rate.Same,
                ),
            )
        }
    }

    override suspend fun seedBudgetOverScenario() {
        // Triggering the user query materializes the default category presets
        // (Food & Drink, Transport, ...) which the scenario below relies on.
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
}
