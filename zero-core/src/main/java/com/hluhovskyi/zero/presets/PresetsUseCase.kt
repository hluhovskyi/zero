package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase

interface PresetsUseCase {
    suspend fun seed()

    companion object {
        fun create(
            categoryRepository: CategoryRepository,
            accountRepository: AccountRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            configurationRepository: ConfigurationRepository,
        ): PresetsUseCase = DefaultPresetsUseCase(
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            configurationRepository = configurationRepository,
        )
    }
}
