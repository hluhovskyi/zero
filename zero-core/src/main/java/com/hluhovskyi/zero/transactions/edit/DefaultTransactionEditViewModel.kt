package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "DefaultTransactionEditViewModel"

internal class DefaultTransactionEditViewModel(
    private val useCase: TransactionEditUseCase,
    private val isEditMode: Boolean,
    private val isDuplicateMode: Boolean,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
) : TransactionEditViewModel {

    override val state: Flow<TransactionEditViewModel.State> = useCase.state
        .map { state ->
            val selectedCategory = when (state) {
                is TransactionEditUseCase.State.Expense -> state.selectedCategory
                is TransactionEditUseCase.State.Income -> state.selectedCategory
                is TransactionEditUseCase.State.Transfer -> null
            }
            TransactionEditViewModel.State(
                transactionTypes = TransactionEditType.values().toList(),
                selectedTransactionType = when (state) {
                    is TransactionEditUseCase.State.Expense -> TransactionEditType.EXPENSE
                    is TransactionEditUseCase.State.Income -> TransactionEditType.INCOME
                    is TransactionEditUseCase.State.Transfer -> TransactionEditType.TRANSFER
                },
                date = state.date,
                selectedCategory = selectedCategory,
                headerMode = when {
                    isDuplicateMode -> TransactionEditViewModel.HeaderMode.DuplicateFrom(
                        subtitle = formatSubtitle(state),
                    )
                    isEditMode -> TransactionEditViewModel.HeaderMode.Edit
                    else -> TransactionEditViewModel.HeaderMode.New
                },
                notes = when (state) {
                    is TransactionEditUseCase.State.Expense -> state.notes
                    is TransactionEditUseCase.State.Income -> state.notes
                    is TransactionEditUseCase.State.Transfer -> state.notes
                },
                amount = when (state) {
                    is TransactionEditUseCase.State.Expense -> state.amount
                    is TransactionEditUseCase.State.Income -> state.amount
                    is TransactionEditUseCase.State.Transfer -> state.amount
                },
                currencySymbol = when (state) {
                    is TransactionEditUseCase.State.Expense -> state.selectedCurrency?.currencySymbol ?: ""
                    is TransactionEditUseCase.State.Income -> state.selectedCurrency?.currencySymbol ?: ""
                    is TransactionEditUseCase.State.Transfer -> state.sourceCurrencySymbol
                },
                canPickCurrency = state !is TransactionEditUseCase.State.Transfer,
            )
        }

    override fun perform(action: TransactionEditViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditViewModel.Action.ChangeTransactionType ->
                TransactionEditUseCase.Action.SwitchTransaction(action.type)

            is TransactionEditViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)

            is TransactionEditViewModel.Action.PickCurrency ->
                TransactionEditUseCase.Action.ShowAllCurrencies

            is TransactionEditViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)

            is TransactionEditViewModel.Action.ChangeNotes ->
                TransactionEditUseCase.Action.ChangeNotes(action.notes)

            is TransactionEditViewModel.Action.Save ->
                TransactionEditUseCase.Action.Save

            is TransactionEditViewModel.Action.Discard ->
                TransactionEditUseCase.Action.Discard

            is TransactionEditViewModel.Action.Delete ->
                TransactionEditUseCase.Action.Delete

            is TransactionEditViewModel.Action.Duplicate ->
                TransactionEditUseCase.Action.Duplicate
        }
        useCase.perform(useCaseAction)
    }

    private fun formatSubtitle(state: TransactionEditUseCase.State): String {
        val snapshot = state.sourceSnapshot ?: return ""
        val dateText = dateFormatter.format(
            date = snapshot.date.date,
            dayConfig = DateFormatter.DayConfig.WithoutZero,
            monthConfig = DateFormatter.MonthConfig.Readable,
            yearConfig = DateFormatter.YearConfig.Default,
        )
        val amountValue = snapshot.amount.toBigDecimalOrNull()
        return if (amountValue != null) {
            val amountText = amountFormatter.format(Amount(amountValue), snapshot.currencySymbol)
            "$amountText · $dateText"
        } else {
            dateText
        }
    }
}
