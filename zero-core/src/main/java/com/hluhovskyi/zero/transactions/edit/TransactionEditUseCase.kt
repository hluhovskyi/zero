package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDateTime
import java.io.Closeable

interface TransactionEditUseCase : AttachableActionStateModel<TransactionEditUseCase.Action, TransactionEditUseCase.State> {

    sealed interface Action {
        data class SwitchTransaction(val type: TransactionEditType) : Action
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        data class SelectCurrency(val currency: TransactionEditCurrency) : Action
        data class SelectCategory(val category: TransactionEditCategory) : Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeRate(val rate: String) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object EditCategories : Action
        object Save : Action
        object Discard : Action
        object Delete : Action
        object Duplicate : Action
        data class ChangeTargetAmount(val amount: String) : Action
        object ResetRate : Action
        object SwapAccounts : Action
        object ShowAllCategories : Action
        object ShowAllCurrencies : Action
        data class ChangeNotes(val notes: String) : Action
    }

    /**
     * One flat read model. The per-type *shape* the View switches on lives at the ViewModel's
     * `Form`; here `transactionType` is just a field. Category lists are empty / null for transfer;
     * target-account fields are empty / null for expense and income.
     */
    data class State(
        val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val categories: List<TransactionEditCategory> = emptyList(),
        val selectedCategory: TransactionEditCategory? = null,
        /** True once the category was chosen from the full picker (retires the quick chips). */
        val categoryPickedFromPicker: Boolean = false,
        val currencies: List<TransactionEditCurrency> = emptyList(),
        val selectedCurrency: TransactionEditCurrency? = null,
        val amount: String = "",
        val rate: String = "",
        val rateAuto: Boolean = true,
        val targetAmount: String = "",
        val notes: String = "",
        val date: LocalDateTime,
        val sourceSnapshot: SourceSnapshot? = null,
        /** True once the user has performed at least one editing action on the form. */
        val isModified: Boolean = false,
    )

    /**
     * Snapshot of the source transaction captured once at load time. Used by the duplicate flow
     * to render a stable "Duplicate from $X · date" header that doesn't change as the user edits.
     */
    data class SourceSnapshot(
        val amount: String,
        val date: LocalDateTime,
        val currencySymbol: String,
    )

    object Noop : TransactionEditUseCase {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeables.empty()
    }
}
