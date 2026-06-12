package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountConfigurationKey
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.write
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DefaultAccountEditViewModel(
    private val accountId: Id = Id.Unknown,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val iconRepository: IconRepository,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val accountEditIconUseCase: AccountEditIconUseCase,
    private val accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
    private val onAccountSavedHandler: OnAccountSavedHandler,
    private val configurationRepository: ConfigurationRepository = ConfigurationRepository.Noop,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    AccountEditViewModel {

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: StateFlow<AccountEditViewModel.State> = mutableState
        .map { state -> state.toViewModelState() }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(1000),
            initialValue = mutableState.value.toViewModelState(),
        )

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
            is AccountEditViewModel.Action.OpenCurrencyPicker -> {
                accountEditCurrencyUseCase.perform(
                    AccountEditCurrencyUseCase.Action.Request(
                        selectedCurrencyId = mutableState.value.selectedCurrency?.id ?: Id.Unknown,
                    ),
                )
            }
            is AccountEditViewModel.Action.SelectIcon -> {
                accountEditIconUseCase.perform(
                    AccountEditIconUseCase.Action.Request(
                        iconId = mutableState.value.iconId,
                        colorId = mutableState.value.colorId,
                    ),
                )
            }
            is AccountEditViewModel.Action.Save -> scope.launch(dispatchers.io()) {
                val state = mutableState.value
                val selectedCurrency = state.selectedCurrency ?: return@launch
                val isNewAccount = accountId !is Id.Known
                accountRepository.insert(
                    AccountRepository.AccountInsert(
                        id = accountId,
                        name = state.name,
                        currencyId = selectedCurrency.id,
                        iconId = state.iconId,
                        colorId = state.colorId,
                        initialBalance = Amount(state.balance.toDoubleOrNull() ?: 0.0),
                        category = state.category,
                        details = state.details.takeIf { it.isNotBlank() },
                    ),
                )
                if (isNewAccount) {
                    configurationRepository.write(AccountConfigurationKey.HasAddedAccount, true)
                }
                withContext(context = dispatchers.main()) {
                    onAccountSavedHandler.onSaved()
                }
            }
        }
    }

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            if (accountId is Id.Known) {
                val account = accountRepository.query(AccountRepository.Criteria.ById(accountId))
                    .firstOrNull()
                    ?.firstOrNull()
                if (account != null) {
                    mutableState.update { state ->
                        state.copy(
                            name = account.name,
                            balance = account.initialBalance.value.toPlainString(),
                            details = account.details.orEmpty(),
                            category = account.category,
                            iconId = account.iconId,
                            targetCurrencyId = account.currencyId,
                        )
                    }
                }
            }

            val primaryCurrency = runCatching { currencyPrimaryUseCase.getPrimaryCurrency() }.getOrNull()
            launch {
                iconRepository.query(IconRepository.Criteria.ById(mutableState.value.iconId))
                    .collect { icon ->
                        mutableState.update { state ->
                            if (state.icon == Image.empty()) state.copy(icon = icon.image) else state
                        }
                    }
            }
            launch {
                currencyRepository.query(CurrencyRepository.Criteria.InUse())
                    .collectLatest { currencies ->
                        mutableState.update { state ->
                            state.copy(
                                currencies = currencies,
                                selectedCurrency = state.selectedCurrency
                                    ?: currencies.firstOrNull { it.id == state.targetCurrencyId }
                                    ?: currencies.firstOrNull { it.id == primaryCurrency?.id }
                                    ?: primaryCurrency
                                    ?: currencies.firstOrNull(),
                            )
                        }
                    }
            }
            launch(dispatchers.main()) {
                accountEditIconUseCase.state.collect { iconState ->
                    when (iconState) {
                        is AccountEditIconUseCase.State.Picked -> mutableState.update { state ->
                            state.copy(
                                iconId = iconState.icon.id,
                                icon = iconState.icon.image,
                                colorScheme = iconState.colorScheme ?: state.colorScheme,
                            )
                        }
                        is AccountEditIconUseCase.State.ColorChanged -> mutableState.update { state ->
                            state.copy(
                                colorId = iconState.colorId,
                                colorScheme = iconState.colorScheme,
                            )
                        }
                    }
                }
            }
            launch(dispatchers.main()) {
                accountEditCurrencyUseCase.state
                    .filterIsInstance<AccountEditCurrencyUseCase.State.Picked>()
                    .collectLatest { currencyState ->
                        mutableState.update { state ->
                            state.copy(selectedCurrency = currencyState.currency)
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
        val colorId: Id = Id.Unknown,
        val colorScheme: ColorScheme = ColorScheme.Grey,
        val targetCurrencyId: Id = Id.Unknown,
    )

    private fun CompositeState.toViewModelState() = AccountEditViewModel.State(
        name = name,
        balance = balance,
        details = details,
        category = category,
        currencies = currencies,
        selectedCurrency = selectedCurrency,
        selectedIcon = icon,
        colorScheme = colorScheme,
        isEditMode = accountId is Id.Known,
    )
}
