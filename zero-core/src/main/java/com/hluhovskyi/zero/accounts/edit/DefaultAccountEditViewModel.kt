package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultAccountEditViewModel(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val accountEditIconUseCase: AccountEditIconUseCase,
    private val onAccountSavedHandler: OnAccountSavedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : AccountEditViewModel {

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<AccountEditViewModel.State> = mutableState
        .map { state ->
            AccountEditViewModel.State(
                name = state.name,
                balance = state.balance,
                details = state.details,
                category = state.category,
                currencies = state.currencies,
                selectedCurrency = state.selectedCurrency,
                selectedIcon = state.icon,
            )
        }

    override fun perform(action: AccountEditViewModel.Action) {
        when (action) {
            is AccountEditViewModel.Action.ChangeName -> mutableState.update { state ->
                state.copy(name = action.name)
            }
            is AccountEditViewModel.Action.ChangeBalance -> mutableState.update { state ->
                state.copy(balance = action.balance)
            }
            is AccountEditViewModel.Action.ChangeDetails -> mutableState.update { state ->
                state.copy(details = action.details)
            }
            is AccountEditViewModel.Action.ChangeCategory -> mutableState.update { state ->
                state.copy(category = action.category)
            }
            is AccountEditViewModel.Action.SelectCurrency -> mutableState.update { state ->
                state.copy(selectedCurrency = action.currency)
            }
            is AccountEditViewModel.Action.SelectIcon -> {
                accountEditIconUseCase.perform(AccountEditIconUseCase.Action.Request)
            }
            is AccountEditViewModel.Action.Save -> coroutineScope.launch {
                val state = mutableState.value
                val selectedCurrency = state.selectedCurrency ?: return@launch
                accountRepository.insert(
                    AccountRepository.AccountInsert(
                        name = state.name,
                        currencyId = selectedCurrency.id,
                        iconId = state.iconId,
                        initialBalance = Amount(state.balance.toDoubleOrNull() ?: 0.0),
                        category = state.category,
                        details = state.details.takeIf { it.isNotBlank() },
                    ),
                )
                launch(context = Dispatchers.Main) {
                    onAccountSavedHandler.onSaved()
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val primaryCurrency = runCatching { currencyPrimaryUseCase.getPrimaryCurrency() }.getOrNull()
            launch {
                currencyRepository.query(CurrencyRepository.Criteria.InUse())
                    .collectLatest { currencies ->
                        mutableState.update { state ->
                            state.copy(
                                currencies = currencies,
                                selectedCurrency = state.selectedCurrency
                                    ?: currencies.firstOrNull { it.id == primaryCurrency?.id }
                                    ?: primaryCurrency
                                    ?: currencies.firstOrNull(),
                            )
                        }
                    }
            }
            launch(context = Dispatchers.Main) {
                accountEditIconUseCase.state
                    .filterIsInstance<AccountEditIconUseCase.State.Picked>()
                    .collectLatest { iconState ->
                        mutableState.update { state ->
                            state.copy(
                                iconId = iconState.icon.id,
                                icon = iconState.icon.image,
                            )
                        }
                    }
            }
        }
    }

    private data class CompositeState(
        val name: String = "",
        val balance: String = "",
        val details: String = "",
        val category: AccountCategory = AccountCategory.OTHER,
        val selectedCurrency: Currency? = null,
        val currencies: List<Currency> = emptyList(),
        val iconId: Id.Known = IconRepository.defaultAccountIconId(),
        val icon: Image = Image.empty(),
    )
}
