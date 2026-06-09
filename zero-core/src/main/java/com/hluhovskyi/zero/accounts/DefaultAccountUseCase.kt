package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEmpty

internal class DefaultAccountUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    currencyRepository: CurrencyRepository,
    iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
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
        transactionRepository.query(TransactionRepository.Criteria.All())
            .onEmpty { emit(emptyList()) },
    ) { accounts, accountIdToBalance, idToCurrency, idToIcon, allTransactions ->
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

        val deltasByMonth: Map<Int, Amount> = allTransactions
            .mapNotNull { transaction ->
                transaction.netWorthContribution()?.let { (currencyId, signed) ->
                    transaction.dateTime.monthIndex() to
                        currencyConvertUseCase.convertToPrimary(signed, currencyId)
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, deltas) -> deltas.fold(Amount.zero(), Amount::plus) }
        val anchorMonthIndex = clock.localDateTime(zoneProvider.timeZone()).monthIndex()

        AccountUseCase.State(
            balance = balance,
            assets = assets,
            liabilities = liabilities,
            currency = currencyPrimaryUseCase.getPrimaryCurrency(),
            accounts = resultAccounts,
            netWorthTrend = reconstructNetWorthTrend(balance, deltasByMonth, anchorMonthIndex),
        )
    }

    override fun perform(action: AccountUseCase.Action) {
    }
}
