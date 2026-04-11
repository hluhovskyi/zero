package com.hluhovskyi.zero.currencies.picker

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency

interface CurrencyPickerViewModel : AttachableActionStateModel<CurrencyPickerViewModel.Action, CurrencyPickerViewModel.State> {

    sealed interface Action {
        data class SelectCurrency(val currency: Currency) : Action
    }

    data class State(
        val currencies: List<Currency> = emptyList(),
    )
}
