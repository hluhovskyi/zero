package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.accounts.AccountRepository
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
    private val onEditHandler: OnAccountDetailEditHandler = OnAccountDetailEditHandler.Noop,
    private val accountRepository: AccountRepository = AccountRepository.Noop,
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
            AccountDetailViewModel.Action.Edit -> coroutineScope.launch(Dispatchers.Main) {
                onEditHandler.onEdit()
            }
            AccountDetailViewModel.Action.Archive -> coroutineScope.launch(Dispatchers.Main) {
                accountRepository.archive(accountId)
            }
            AccountDetailViewModel.Action.Unarchive -> coroutineScope.launch(Dispatchers.Main) {
                accountRepository.unarchive(accountId)
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
                            isArchived = account.archivedAt != null,
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
