package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Image

interface AccountEditViewModel : AttachableActionStateModel<AccountEditViewModel.Action, AccountEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        data class ChangeBalance(val balance: String) : Action
        data class ChangeDetails(val details: String) : Action
        data class ChangeCategory(val category: AccountCategory) : Action
        object SelectIcon : Action
        data class SelectCurrency(val currency: Currency) : Action
        object OpenCurrencyPicker : Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val balance: String = "",
        val details: String = "",
        val category: AccountCategory = AccountCategory.OTHER,
        val currencies: List<Currency> = emptyList(),
        val selectedCurrency: Currency? = null,
        val selectedIcon: Image = Image.empty(),
        val colorScheme: ColorScheme = ColorScheme.Grey,
        val isEditMode: Boolean = false,
    )
}
