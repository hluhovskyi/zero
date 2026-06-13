package com.hluhovskyi.zero.analytics.breakdown

import com.hluhovskyi.zero.analytics.SpendingBreakdownUseCase
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import com.hluhovskyi.zero.transactions.toDateRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MIN_ACCOUNTS_FOR_DIMENSION = 2

internal class DefaultSpendingBreakdownViewModel(
    private val filter: TransactionFilter,
    private val spendingBreakdownUseCase: SpendingBreakdownUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onBackHandler: OnBackHandler,
    private val zonedClock: ZonedClock,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    SpendingBreakdownViewModel {

    private val mutableState = MutableStateFlow(SpendingBreakdownViewModel.State())
    override val state: StateFlow<SpendingBreakdownViewModel.State> = mutableState

    override fun perform(action: SpendingBreakdownViewModel.Action) {
        when (action) {
            SpendingBreakdownViewModel.Action.Back -> scope.launch(dispatchers.main()) {
                onBackHandler.onBack()
            }
            is SpendingBreakdownViewModel.Action.SelectDimension -> {
                mutableState.update { it.copy(selectedDimension = action.dimension) }
            }
        }
    }

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            val range = filter.period?.toDateRange(zonedClock.localDateTime().date)
            val criteria = TransactionFilterCriteria(
                from = range?.start,
                to = range?.end,
                categoryIds = filter.categoryIds,
                accountIds = filter.accountIds,
            )
            val currencySymbol = currencyPrimaryUseCase.getPrimaryCurrency().symbol

            spendingBreakdownUseCase.query(criteria).collectLatest { breakdown ->
                mutableState.update { state ->
                    state.copy(
                        totalAmount = breakdown.total,
                        currencySymbol = currencySymbol,
                        transactionCount = breakdown.transactionCount,
                        scopedAccountCount = breakdown.accounts.size,
                        context = SpendingBreakdownViewModel.Context(
                            categoryCount = filter.categoryIds?.size ?: 0,
                            accountCount = filter.accountIds?.size ?: 0,
                            type = filter.type.takeIf { it != TransactionFilter.TransactionType.All },
                            dateRange = range,
                        ),
                        showAccountDimension = breakdown.accounts.size >= MIN_ACCOUNTS_FOR_DIMENSION,
                        categoryRows = breakdown.categories.map { it.toRow(breakdown.total) },
                        accountRows = breakdown.accounts.map { it.toRow(breakdown.total) },
                    )
                }
            }
        }
    }

    private fun SpendingBreakdownUseCase.CategorySpend.toRow(total: Amount): SpendingBreakdownViewModel.Row = row(name, amount, transactionCount, colorScheme, icon, total)

    private fun SpendingBreakdownUseCase.AccountSpend.toRow(total: Amount): SpendingBreakdownViewModel.Row = row(name, amount, transactionCount, colorScheme, icon, total)

    private fun row(
        name: String,
        amount: Amount,
        transactionCount: Int,
        colorScheme: ColorScheme,
        icon: Image,
        total: Amount,
    ): SpendingBreakdownViewModel.Row {
        val fraction = if (total > 0L) (amount / total).toFloat() else 0f
        return SpendingBreakdownViewModel.Row(
            name = name,
            amount = amount,
            transactionCount = transactionCount,
            sharePercent = (fraction * 100).roundToInt(),
            shareFraction = fraction,
            colorScheme = colorScheme,
            icon = icon,
        )
    }
}
