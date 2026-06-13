package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.analytics.MonthlyCashFlowUseCase
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEmpty
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal class DefaultAccountUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    currencyRepository: CurrencyRepository,
    iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val monthlyCashFlowUseCase: MonthlyCashFlowUseCase,
    private val zonedClock: ZonedClock,
) : AccountUseCase {

    override val state: Flow<AccountUseCase.State> = combine(
        accountRepository.query(AccountRepository.Criteria.All()),
        transactionRepository.query(TransactionRepository.Criteria.AccountBalanceDeltas())
            .onEmpty { emit(emptyMap()) },
        currencyRepository.query(CurrencyRepository.Criteria.All())
            .onEmptyReturnEmptyList()
            .associateById(),
        iconRepository.query(IconRepository.Criteria.All())
            .onEmptyReturnEmptyList()
            .associateById(),
        monthlyCashFlowUseCase.query(netWorthTrendRange()),
    ) { accounts, accountIdToBalance, idToCurrency, idToIcon, cashFlow ->
        val resultAccounts = accounts.map { account ->
            val colorScheme = (account.colorId as? Id.Known)
                ?.let { colorRepository.schemeFor(it) }
                ?: ColorScheme.Grey
            Account(
                id = account.id,
                name = account.name,
                balance = account.initialBalance + (accountIdToBalance[account.id] ?: Amount.zero()),
                currencySymbol = idToCurrency[account.currencyId]?.symbol.orEmpty(),
                icon = idToIcon[account.iconId]?.image
                    ?: iconRepository.iconFor(account.category).image,
                colorScheme = colorScheme,
                category = account.category,
                details = account.details,
                archivedAt = account.archivedAt,
            )
        }
        var balance = Amount.zero()
        var assets = Amount.zero()
        var liabilities = Amount.zero()
        accounts.forEach { account ->
            val converted = currencyConvertUseCase.convertToPrimary(
                amount = account.initialBalance + (accountIdToBalance[account.id] ?: Amount.zero()),
                currencyId = account.currencyId,
            )
            balance += converted
            if (converted >= 0L) {
                assets += converted
            } else {
                liabilities -= converted
            }
        }

        // Net worth is the running cumulative of monthly cash flow, reconstructed from the live total.
        val netWorthTrend = reconstructNetWorthTrend(balance, cashFlow.map { it.net })

        AccountUseCase.State(
            balance = balance,
            assets = assets,
            liabilities = liabilities,
            currency = currencyPrimaryUseCase.getPrimaryCurrency(),
            accounts = resultAccounts,
            netWorthTrend = netWorthTrend,
            netWorthChange = netWorthChange(netWorthTrend),
        )
    }

    /** Trailing [NET_WORTH_TREND_MONTHS]-month window ending today, for the net-worth trend. */
    private fun netWorthTrendRange(): DateRange {
        val today = zonedClock.localDateTime().date
        val start = LocalDate(today.year, today.monthNumber, 1).minus(NET_WORTH_TREND_MONTHS - 1, DateTimeUnit.MONTH)
        return DateRange(start = start, end = today)
    }

    override fun perform(action: AccountUseCase.Action) {
    }
}
