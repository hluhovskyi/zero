package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.accounts.AccountRoom
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.transactions.TransactionRoom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
internal class InUseCurrencyRepository(
    private val accountRoom: () -> AccountRoom,
    private val transactionRoom: () -> TransactionRoom,
    private val baseRepository: CurrencyRepository,
    private val currentUserId: Flow<Id.Known>,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : CurrencyRepository {

    override fun <T> query(criteria: CurrencyRepository.Criteria<T>): Flow<T> = when (criteria) {
        is CurrencyRepository.Criteria.InUse -> queryInUse()
        else -> baseRepository.query(criteria)
    }.uncheckedCast()

    private fun queryInUse(): Flow<List<Currency>> = currentUserId.flatMapLatest { userId ->
        val timeZone = zoneProvider.timeZone()
        val thirtyDaysAgo = clock.localDateTime(timeZone)
            .toInstant(timeZone)
            .minus(30, DateTimeUnit.DAY, timeZone)
            .toLocalDateTime(timeZone)
            .toString()

        combine(
            accountRoom().selectInUseCurrencyIds(userId.value),
            transactionRoom().selectInUseCurrencyIds(userId.value, thirtyDaysAgo),
        ) { accountCurrencies, transactionCurrencies ->
            (accountCurrencies + transactionCurrencies).distinct()
        }
    }.flatMapLatest { ids ->
        baseRepository.query(CurrencyRepository.Criteria.All())
            .map { allCurrencies ->
                allCurrencies.filter { it.id in ids }
            }
    }
}
