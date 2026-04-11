package com.hluhovskyi.zero.currencies.picker

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id

interface CurrencyPickerViewModel : AttachableActionStateModel<CurrencyPickerViewModel.Action, CurrencyPickerViewModel.State> {

    sealed interface Action {
        data class SelectCurrency(val currency: Currency) : Action
        data class UpdateSearchQuery(val query: String) : Action
    }

    data class State(
        val currencies: List<Currency> = emptyList(),
        val searchQuery: String = "",
        val selectedCurrencyId: Id = Id.Unknown,
    )
}
