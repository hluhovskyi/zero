package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime

/**
 * Maps a [TransactionFilter] to a SQL-level [TransactionRepository.Criteria.Filtered], resolving
 * the relative [TransactionFilter.period] to a concrete date range against today. Replaces the old
 * in-memory `TransactionFilterApplicator` — the database now does the filtering.
 */
class TransactionFilterCriteria(
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) {
    fun create(filter: TransactionFilter): TransactionRepository.Criteria.Filtered {
        val range = filter.period?.toDateRange(clock.localDateTime(zoneProvider.timeZone()).date)
        return TransactionRepository.Criteria.Filtered(
            from = range?.start,
            to = range?.end,
            type = filter.type.toCriteriaType(),
            categoryIds = filter.categoryIds,
            accountIds = filter.accountIds,
        )
    }

    private fun TransactionFilter.TransactionType.toCriteriaType(): TransactionRepository.Type? = when (this) {
        TransactionFilter.TransactionType.All -> null
        TransactionFilter.TransactionType.Expense -> TransactionRepository.Type.Expense
        TransactionFilter.TransactionType.Income -> TransactionRepository.Type.Income
        TransactionFilter.TransactionType.Transfer -> TransactionRepository.Type.Transfer
    }
}
