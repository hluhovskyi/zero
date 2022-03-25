package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface TransactionEditUseCase :
    AttachableActionStateModel<TransactionEditUseCase.Action, TransactionEditUseCase.State> {

    sealed interface Action {
        data class SwitchTransaction(val type: TransactionEditType) : Action
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        data class SelectCurrency(val currency: TransactionEditCurrency) : Action
        data class SelectCategory(val category: TransactionEditCategory) : Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeRate(val rate: String) : Action
        object EditCategories : Action
        object Save : Action
    }

    sealed interface State {

        data class Expense(
            val accounts: List<TransactionEditAccount> = emptyList(),
            val selectedAccount: TransactionEditAccount? = null,
            val categories: List<TransactionEditCategory> = emptyList(),
            val selectedCategory: TransactionEditCategory? = null,
            val currencies: List<TransactionEditCurrency> = emptyList(),
            val selectedCurrency: TransactionEditCurrency? = null,
            val amount: String = "",
            val rate: String = "",
        ) : State

        data class Income(
            val accounts: List<TransactionEditAccount> = emptyList(),
            val selectedAccount: TransactionEditAccount? = null,
            val categories: List<TransactionEditCategory> = emptyList(),
            val selectedCategory: TransactionEditCategory? = null,
            val currencies: List<TransactionEditCurrency> = emptyList(),
            val selectedCurrency: TransactionEditCurrency? = null,
            val amount: String = "",
            val rate: String = "",
        ) : State

        data class Transfer(
            val accounts: List<TransactionEditAccount> = emptyList(),
            val selectedAccount: TransactionEditAccount? = null,
            val targetAccounts: List<TransactionEditAccount> = emptyList(),
            val selectedTargetAccount: TransactionEditAccount? = null,
            val amount: String = "",
        ) : State
    }

    object Noop : TransactionEditUseCase {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeables.empty()
    }
}