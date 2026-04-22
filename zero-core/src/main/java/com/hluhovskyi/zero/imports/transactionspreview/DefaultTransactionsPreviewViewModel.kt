package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.imports.ImportTransaction
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionsPreviewViewModel(
    private val importUseCase: ImportUseCase,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
) : TransactionsPreviewViewModel {

    override val state: Flow<TransactionsPreviewViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.TransactionsPreview>()
        .map { previewState ->
            TransactionsPreviewViewModel.State(
                transactions = previewState.transactions.map { transaction -> transaction.toDisplay() },
                totalCount = previewState.totalCount,
            )
        }

    private fun ImportTransaction.toDisplay(): TransactionsPreviewViewModel.DisplayTransaction = when (this) {
        is ImportTransaction.Expense -> TransactionsPreviewViewModel.DisplayTransaction(
            primaryText = categoryName ?: "Expense",
            amount = amountFormatter.format(amount, currencyId.value),
            accountName = accountId.value,
            date = dateFormatter.format(
                date = dateTime.date,
                dayConfig = DateFormatter.DayConfig.WithoutZero,
                monthConfig = DateFormatter.MonthConfig.Readable,
                yearConfig = DateFormatter.YearConfig.SkipCurrent,
            ),
            type = TransactionsPreviewViewModel.DisplayTransaction.Type.EXPENSE,
        )
        is ImportTransaction.Income -> TransactionsPreviewViewModel.DisplayTransaction(
            primaryText = categoryName ?: "Income",
            amount = amountFormatter.format(amount, currencyId.value),
            accountName = accountId.value,
            date = dateFormatter.format(
                date = dateTime.date,
                dayConfig = DateFormatter.DayConfig.WithoutZero,
                monthConfig = DateFormatter.MonthConfig.Readable,
                yearConfig = DateFormatter.YearConfig.SkipCurrent,
            ),
            type = TransactionsPreviewViewModel.DisplayTransaction.Type.INCOME,
        )
        is ImportTransaction.Transfer -> TransactionsPreviewViewModel.DisplayTransaction(
            primaryText = targetAccountId.value,
            amount = amountFormatter.format(amount, currencyId.value),
            accountName = accountId.value,
            date = dateFormatter.format(
                date = dateTime.date,
                dayConfig = DateFormatter.DayConfig.WithoutZero,
                monthConfig = DateFormatter.MonthConfig.Readable,
                yearConfig = DateFormatter.YearConfig.SkipCurrent,
            ),
            type = TransactionsPreviewViewModel.DisplayTransaction.Type.TRANSFER,
        )
    }

    override fun perform(action: TransactionsPreviewViewModel.Action) {
        when (action) {
            is TransactionsPreviewViewModel.Action.Confirm ->
                importUseCase.perform(ImportUseCase.Action.Confirm)
            is TransactionsPreviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
        }
    }
}
