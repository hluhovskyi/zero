package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconCategory
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DefaultAccountEditViewModelTest {

    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var currencyRepository: CurrencyRepository
    @Mock private lateinit var iconRepository: IconRepository
    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase
    @Mock private lateinit var accountEditIconUseCase: AccountEditIconUseCase
    @Mock private lateinit var accountEditCurrencyUseCase: AccountEditCurrencyUseCase

    private val testDispatcher = UnconfinedTestDispatcher()
    private val iconId = Id.Known("icon-1")
    private val currencyId = Id.Known("cur-1")
    private val accountId = Id.Known("acc-1")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(accountRepository.query(any())).thenReturn(flowOf(emptyList()))
        whenever(currencyRepository.query<List<Currency>>(any())).thenReturn(flowOf(emptyList()))
        whenever(iconRepository.query<Icon>(any())).thenReturn(
            flowOf(Icon(id = iconId, image = Image.empty(), category = IconCategory.unknown()))
        )
        whenever(accountEditIconUseCase.state).thenReturn(flowOf())
        whenever(accountEditCurrencyUseCase.state).thenReturn(flowOf())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isEditMode is false when accountId is Unknown`() = runTest {
        val vm = createViewModel(Id.Unknown)
        vm.attach()
        runCurrent()
        assertFalse(vm.state.first().isEditMode)
    }

    @Test
    fun `isEditMode is true when accountId is Known`() = runTest {
        val vm = createViewModel(accountId)
        vm.attach()
        runCurrent()
        assertTrue(vm.state.first().isEditMode)
    }

    @Test
    fun `edit mode pre-populates state from account repository`() = runTest {
        val account = AccountRepository.Account(
            id = accountId,
            name = "Chase Sapphire",
            currencyId = currencyId,
            iconId = iconId,
            initialBalance = Amount(BigDecimal("5000.00")),
            category = AccountCategory.BANK,
            details = "Checking",
        )
        whenever(accountRepository.query(any())).thenReturn(flowOf(listOf(account)))

        val vm = createViewModel(accountId)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals("Chase Sapphire", state.name)
        assertEquals("5000.00", state.balance)
        assertEquals(AccountCategory.BANK, state.category)
        assertEquals("Checking", state.details)
    }

    private fun createViewModel(id: Id) = DefaultAccountEditViewModel(
        accountId = id,
        accountRepository = accountRepository,
        currencyRepository = currencyRepository,
        iconRepository = iconRepository,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        accountEditIconUseCase = accountEditIconUseCase,
        accountEditCurrencyUseCase = accountEditCurrencyUseCase,
        onAccountSavedHandler = OnAccountSavedHandler.Noop,
        coroutineScope = CoroutineScope(testDispatcher),
    )
}
