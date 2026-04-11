package com.hluhovskyi.zero.currencies.picker

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.currencies.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
            is CurrencyPickerViewModel.Action.UpdateSearchQuery -> {
                mutableState.update { it.copy(searchQuery = action.query) }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val allCurrencies = currencyRepository.query(CurrencyRepository.Criteria.All())
            combine(allCurrencies, mutableState) { currencies, state ->
                currencies.filter { it.matchesQuery(state.searchQuery) }
            }.collectLatest { filtered ->
                mutableState.update { it.copy(currencies = filtered) }
            }
        }
    }

    private fun Currency.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) || id.value.lowercase().contains(q)
    }
}
