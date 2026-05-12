package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.firstOrDefault
import com.hluhovskyi.zero.config.write
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase

internal class DefaultPresetsUseCase(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val configurationRepository: ConfigurationRepository,
) : PresetsUseCase {

    override suspend fun seed() {
        val seeded = configurationRepository.firstOrDefault(PresetsConfigurationKey.PresetsSeeded)
        if (seeded) return

        val currencyId = currencyPrimaryUseCase.getPrimaryCurrency().id
        categoryRepository.insert(presetCategories())
        accountRepository.insert(presetAccounts(currencyId))
        configurationRepository.write(PresetsConfigurationKey.PresetsSeeded, true)
    }

    private fun presetCategories(): List<CategoryRepository.CategoryInsert> = listOf(
        categoryInsert(name = "Food & Drink", iconId = "grocery", colorId = "orange", type = CategoryType.EXPENSE),
        categoryInsert(name = "Transport", iconId = "car", colorId = "teal", type = CategoryType.EXPENSE),
        categoryInsert(name = "Shopping", iconId = "shopping_cart", colorId = "pink", type = CategoryType.EXPENSE),
        categoryInsert(name = "Entertainment", iconId = "game_controller", colorId = "purple", type = CategoryType.EXPENSE),
        categoryInsert(name = "Health", iconId = "health", colorId = "red", type = CategoryType.EXPENSE),
        categoryInsert(name = "Salary", iconId = "salary", colorId = "blue", type = CategoryType.INCOME),
        categoryInsert(name = "Other Income", iconId = null, colorId = "grey", type = CategoryType.INCOME),
    )

    private fun presetAccounts(currencyId: Id.Known): List<AccountRepository.AccountInsert> = listOf(
        AccountRepository.AccountInsert(
            name = "Bank",
            currencyId = currencyId,
            iconId = Id("bank"),
            colorId = Id("blue"),
            initialBalance = Amount.zero(),
            category = AccountCategory.BANK,
        ),
        AccountRepository.AccountInsert(
            name = "Cash",
            currencyId = currencyId,
            iconId = Id("cash"),
            colorId = Id("green"),
            initialBalance = Amount.zero(),
            category = AccountCategory.CASH,
        ),
    )

    private fun categoryInsert(
        name: String,
        iconId: String?,
        colorId: String,
        type: CategoryType,
    ) = CategoryRepository.CategoryInsert(
        parentCategoryId = Id.Unknown,
        name = name,
        iconId = iconId?.let { Id(it) } ?: Id.Unknown,
        colorId = Id(colorId),
        type = type,
    )
}
