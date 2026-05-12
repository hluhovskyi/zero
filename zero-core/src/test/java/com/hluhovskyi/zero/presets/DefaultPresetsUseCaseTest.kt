package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DefaultPresetsUseCaseTest {

    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase
    @Mock private lateinit var configurationRepository: ConfigurationRepository

    private val currencyId = Id.Known("usd")
    private val currency = Currency(id = currencyId, name = "US Dollar", symbol = "$")

    private lateinit var useCase: DefaultPresetsUseCase

    @Before
    fun setUp() {
        useCase = DefaultPresetsUseCase(
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            configurationRepository = configurationRepository,
        )
    }

    @Test
    fun `seed inserts preset categories and accounts when not yet seeded`() = runTest {
        whenever(configurationRepository.observe(PresetsConfigurationKey.PresetsSeeded, Boolean::class))
            .thenReturn(emptyFlow())
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(currency)

        useCase.seed()

        val categoriesCaptor = argumentCaptor<List<CategoryRepository.CategoryInsert>>()
        verify(categoryRepository).insert(categoriesCaptor.capture())
        val categories = categoriesCaptor.firstValue

        assertEquals(7, categories.size)

        val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE }
        val incomeCategories = categories.filter { it.type == CategoryType.INCOME }
        assertEquals(5, expenseCategories.size)
        assertEquals(2, incomeCategories.size)

        assertTrue(expenseCategories.any { it.name == "Food & Drink" && it.iconId == Id("grocery") && it.colorId == Id("orange") })
        assertTrue(expenseCategories.any { it.name == "Transport" && it.iconId == Id("car") && it.colorId == Id("teal") })
        assertTrue(expenseCategories.any { it.name == "Shopping" && it.iconId == Id("shopping_cart") && it.colorId == Id("pink") })
        assertTrue(expenseCategories.any { it.name == "Entertainment" && it.iconId == Id("game_controller") && it.colorId == Id("purple") })
        assertTrue(expenseCategories.any { it.name == "Health" && it.iconId == Id("health") && it.colorId == Id("red") })

        assertTrue(incomeCategories.any { it.name == "Salary" && it.iconId == Id("salary") && it.colorId == Id("blue") })
        assertTrue(incomeCategories.any { it.name == "Other Income" })

        val accountsCaptor = argumentCaptor<List<AccountRepository.AccountInsert>>()
        verify(accountRepository).insert(accountsCaptor.capture())
        val accounts = accountsCaptor.firstValue

        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.name == "Bank" && it.iconId == Id("bank") && it.colorId == Id("blue") && it.category == AccountCategory.BANK && it.currencyId == currencyId })
        assertTrue(accounts.any { it.name == "Cash" && it.iconId == Id("cash") && it.colorId == Id("green") && it.category == AccountCategory.CASH && it.currencyId == currencyId })
    }

    @Test
    fun `seed writes seeded flag after inserting`() = runTest {
        whenever(configurationRepository.observe(PresetsConfigurationKey.PresetsSeeded, Boolean::class))
            .thenReturn(emptyFlow())
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(currency)

        useCase.seed()

        verify(configurationRepository).write(PresetsConfigurationKey.PresetsSeeded, Boolean::class, true)
    }

    @Test
    fun `seed is a no-op when already seeded`() = runTest {
        whenever(configurationRepository.observe(PresetsConfigurationKey.PresetsSeeded, Boolean::class))
            .thenReturn(flowOf(true))

        useCase.seed()

        verify(categoryRepository, never()).insert(any<List<CategoryRepository.CategoryInsert>>())
        verify(accountRepository, never()).insert(any<List<AccountRepository.AccountInsert>>())
        verify(configurationRepository, never()).write(any(), any(), any())
    }
}
