package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultAccountViewModelTest {

    @Mock private lateinit var accountUseCase: AccountUseCase
    @Mock private lateinit var accountRepository: AccountRepository

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun cpu() = dispatcher
    }

    @Test
    fun `Edit action calls onEditAccountHandler with correct id`() = runTest {
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
        val editHandler = mock<OnEditAccountHandler>()
        val accountId = Id.Known("acc-1")

        val vm = DefaultAccountViewModel(
            useCase = accountUseCase,
            dispatchers = dispatchers,
            onEditAccountHandler = editHandler,
            accountRepository = accountRepository,
        )
        vm.attach()
        vm.perform(AccountViewModel.Action.Edit(accountId))

        verify(editHandler).onEdit(accountId)
    }

    @Test
    fun `Archive action calls repository archive with account id`() = runTest {
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
        val accountId = Id.Known("acc-1")

        val vm = DefaultAccountViewModel(
            useCase = accountUseCase,
            dispatchers = dispatchers,
            accountRepository = accountRepository,
        )
        vm.attach()
        vm.perform(AccountViewModel.Action.Archive(accountId))

        verify(accountRepository).archive(accountId)
    }

    @Test
    fun `Unarchive action calls repository unarchive with account id`() = runTest {
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
        val accountId = Id.Known("acc-1")

        val vm = DefaultAccountViewModel(
            useCase = accountUseCase,
            dispatchers = dispatchers,
            accountRepository = accountRepository,
        )
        vm.attach()
        vm.perform(AccountViewModel.Action.Unarchive(accountId))

        verify(accountRepository).unarchive(accountId)
    }
}
