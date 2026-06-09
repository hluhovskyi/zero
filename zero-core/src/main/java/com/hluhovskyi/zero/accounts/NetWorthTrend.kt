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
internal fun TransactionRepository.Transaction.netWorthContribution(): Pair<Id.Known, Amount>? = when (this) {
    is TransactionRepository.Transaction.Income -> currencyId to amount
    is TransactionRepository.Transaction.Expense -> currencyId to (Amount.zero() - amount)
    is TransactionRepository.Transaction.Transfer -> null
}

/**
 * The headline change shown next to the net-worth number. Absent (null) when there is nothing
 * to celebrate — fewer than two points, a zero starting point, or no growth/improvement.
 */
sealed interface NetWorthChange {
    /** Net worth grew over the window, by [percent] (always > 0). */
    data class Growth(val percent: Int) : NetWorthChange

    /** Net worth is underwater but improved (debt shrank) over the window, by [delta] (> 0). */
    data class Improvement(val delta: Amount) : NetWorthChange
}

/** Net-worth change over [trend] (first → last), or null when there's nothing meaningful to show. */
internal fun netWorthChange(trend: List<Amount>): NetWorthChange? {
    if (trend.size < 2) return null
    val first = trend.first().value
    val last = trend.last().value
    if (first.signum() == 0) return null
    return if (last.signum() < 0) {
        (trend.last() - trend.first()).takeIf { it.value.signum() > 0 }
            ?.let(NetWorthChange::Improvement)
    } else {
        ((last - first).toDouble() / first.abs().toDouble() * 100).toInt()
            .takeIf { it > 0 }
            ?.let(NetWorthChange::Growth)
    }
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
    // One month before the first activity is the starting baseline (net worth before any delta).
    val baseline = minOf(monthlyDeltas.keys.min(), anchorMonthIndex) - 1
    val start = maxOf(anchorMonthIndex - (maxPoints - 1), baseline)
    val newestToOldest = ArrayList<Amount>(anchorMonthIndex - start + 1)
    var nw = currentNetWorth
    for (month in anchorMonthIndex downTo start) {
        newestToOldest.add(nw)
        nw -= monthlyDeltas[month] ?: Amount.zero()
    }
    return newestToOldest.asReversed().toList()
}
