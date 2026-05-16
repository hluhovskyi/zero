package com.hluhovskyi.zero.testbridge

import com.hluhovskyi.zero.CleanupJob
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

class DefaultDatabaseTestBridge(
    private val cleanupJob: CleanupJob,
    private val currentUserRepository: CurrentUserRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
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
            )
        )

        categoryRepository.insert(
            CategoryRepository.CategoryInsert(
                id = categoryId,
                parentCategoryId = Id.Unknown,
                name = "Food",
                iconId = iconId,
                colorId = colorId,
                type = CategoryType.EXPENSE,
            )
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
            )
        )
    }
}
