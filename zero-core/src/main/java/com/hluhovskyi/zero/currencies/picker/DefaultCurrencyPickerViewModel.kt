package com.hluhovskyi.zero.currencies.picker

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCurrencyPickerViewModel(
    private val currencyRepository: CurrencyRepository,
    private val onCurrencyPickedHandler: OnCurrencyPickedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : CurrencyPickerViewModel {

    private val mutableState = MutableStateFlow(CurrencyPickerViewModel.State())
    override val state: Flow<CurrencyPickerViewModel.State> = mutableState

    override fun perform(action: CurrencyPickerViewModel.Action) {
        when (action) {
            is CurrencyPickerViewModel.Action.SelectCurrency -> {
                onCurrencyPickedHandler.onPicked(action.currency)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            currencyRepository.query(CurrencyRepository.Criteria.All())
                .collectLatest { currencies ->
                    mutableState.update { state ->
                        state.copy(currencies = currencies)
                    }
                }
        }
    }
}
