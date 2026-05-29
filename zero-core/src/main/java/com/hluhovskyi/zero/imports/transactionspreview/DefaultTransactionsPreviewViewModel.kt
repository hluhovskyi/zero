package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.imports.ImportAccount
import com.hluhovskyi.zero.imports.ImportCategory
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
            val accountById = buildMap<Id.Known, ImportAccount> {
                previewState.accounts.forEach {
                    put(it.id, it)
                    it.existingId?.let { existing -> put(existing, it) }
                }
            }
            val categoryById = buildMap<Id.Known, ImportCategory> {
                previewState.categories.forEach {
                    put(it.id, it)
                    it.existingId?.let { existing -> put(existing, it) }
                }
            }

            val groups = LinkedHashMap<String, MutableList<TransactionsPreviewViewModel.DisplayTransaction>>()
            for (transaction in previewState.transactions) {
                val display = transaction.toDisplay(accountById, categoryById)
                groups.getOrPut(display.date) { mutableListOf() }.add(display)
            }

            TransactionsPreviewViewModel.State(
                groups = groups.map { (date, txList) ->
                    TransactionsPreviewViewModel.DateGroup(dateLabel = date, transactions = txList)
                },
                totalCount = previewState.totalCount,
            )
        }

    private fun ImportTransaction.toDisplay(
        accountById: Map<Id.Known, ImportAccount>,
        categoryById: Map<Id.Known, ImportCategory>,
    ): TransactionsPreviewViewModel.DisplayTransaction {
        val accountName = accountById[accountId]?.name ?: accountId.value
        val date = dateFormatter.format(
            date = dateTime.date,
            dayConfig = DateFormatter.DayConfig.WithoutZero,
            monthConfig = DateFormatter.MonthConfig.Readable,
            yearConfig = DateFormatter.YearConfig.SkipCurrent,
        )
        return when (this) {
            is ImportTransaction.Expense -> {
                val category = categoryId?.let { categoryById[it] }
                TransactionsPreviewViewModel.DisplayTransaction(
                    id = id.value,
                    primaryText = categoryName,
                    amount = "-${amountFormatter.format(Amount(amount.value.abs()), currencyId.value)}",
                    accountName = accountName,
                    date = date,
                    colorScheme = category?.colorScheme,
                    icon = category?.icon,
                    type = TransactionsPreviewViewModel.DisplayTransaction.Type.EXPENSE,
                )
            }
            is ImportTransaction.Income -> {
                val category = categoryId?.let { categoryById[it] }
                TransactionsPreviewViewModel.DisplayTransaction(
                    id = id.value,
                    primaryText = categoryName,
                    amount = "+${amountFormatter.format(Amount(amount.value.abs()), currencyId.value)}",
                    accountName = accountName,
                    date = date,
                    colorScheme = category?.colorScheme,
                    icon = category?.icon,
                    type = TransactionsPreviewViewModel.DisplayTransaction.Type.INCOME,
                )
            }
            is ImportTransaction.Transfer -> TransactionsPreviewViewModel.DisplayTransaction(
                id = id.value,
                primaryText = null,
                amount = amountFormatter.format(Amount(amount.value.abs()), currencyId.value),
                accountName = accountName,
                date = date,
                colorScheme = null,
                icon = null,
                type = TransactionsPreviewViewModel.DisplayTransaction.Type.TRANSFER,
            )
        }
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
