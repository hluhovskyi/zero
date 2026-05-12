package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id

interface AccountViewModel : AttachableActionStateModel<AccountViewModel.Action, AccountViewModel.State> {

    sealed interface Action {
        data class Select(val accountId: Id.Known) : Action
        data class Edit(val accountId: Id.Known) : Action
        data class Archive(val accountId: Id.Known) : Action
        data class Unarchive(val accountId: Id.Known) : Action
    }

    data class State(
        val balance: Amount = Amount.zero(),
        val currency: Currency? = null,
        val accounts: List<Account> = emptyList(),
    )
}
