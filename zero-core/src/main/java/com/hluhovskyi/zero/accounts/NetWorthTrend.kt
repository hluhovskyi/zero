package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime

/** Trailing months kept in the net-worth trend (matches the design's 12-month window). */
internal const val NET_WORTH_TREND_MONTHS = 12

/** Month bucket key: stable, comparable, gap-free across year boundaries. */
internal fun LocalDateTime.monthIndex(): Int = year * 12 + (monthNumber - 1)

/**
 * A transaction's effect on total net worth, in its own currency (caller converts to primary).
 * Income adds, Expense subtracts, Transfer is net-zero across accounts → `null`.
 */
internal fun TransactionRepository.Transaction.netWorthContribution(): Pair<Id.Known, Amount>? =
    when (this) {
        is TransactionRepository.Transaction.Income -> currencyId to amount
        is TransactionRepository.Transaction.Expense -> currencyId to (Amount.zero() - amount)
        is TransactionRepository.Transaction.Transfer -> null
    }

/**
 * Net worth at each month boundary, oldest → newest, last == [currentNetWorth].
 * Walks [monthlyDeltas] (signed primary deltas keyed by [monthIndex]) backward from the anchor.
 * Window: from the earliest delta month (clamped to the anchor) up to the anchor, capped to the
 * most recent [maxPoints] months. Empty deltas → a single current point (chart shows a lone dot).
 */
internal fun reconstructNetWorthTrend(
    currentNetWorth: Amount,
    monthlyDeltas: Map<Int, Amount>,
    anchorMonthIndex: Int,
    maxPoints: Int = NET_WORTH_TREND_MONTHS,
): List<Amount> {
    if (monthlyDeltas.isEmpty()) return listOf(currentNetWorth)
    val earliest = minOf(monthlyDeltas.keys.min(), anchorMonthIndex)
    val start = maxOf(anchorMonthIndex - (maxPoints - 1), earliest)
    val newestToOldest = ArrayList<Amount>(anchorMonthIndex - start + 1)
    var nw = currentNetWorth
    for (month in anchorMonthIndex downTo start) {
        newestToOldest.add(nw)
        nw -= monthlyDeltas[month] ?: Amount.zero()
    }
    return newestToOldest.asReversed().toList()
}
