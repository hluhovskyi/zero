package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs

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
 * The headline change shown next to the net-worth number, over the 1-year window. [rising]
 * drives the chip's direction (green ▲ up / red ▼ down); the magnitude is always non-negative,
 * since the arrow + colour carry the sign. Null only when there's no movement to show.
 */
sealed interface NetWorthChange {
    val rising: Boolean

    /** Solvent net worth with a real baseline → a percentage move (e.g. 12 → "12%"). */
    data class Percent(val magnitude: Int, override val rising: Boolean) : NetWorthChange

    /** Underwater or zero-baseline (where a percentage is undefined) → an absolute amount move. */
    data class Delta(val magnitude: Amount, override val rising: Boolean) : NetWorthChange
}

/**
 * Net-worth change over [trend] (first → last). A percentage when solvent with a non-zero
 * baseline; otherwise (underwater, or a zero baseline where a percentage is undefined) the
 * absolute amount moved. Null for fewer than two points or a flat window (no movement).
 */
internal fun netWorthChange(trend: List<Amount>): NetWorthChange? {
    if (trend.size < 2) return null
    val first = trend.first().value
    val last = trend.last().value
    val delta = last - first
    if (delta.signum() == 0) return null
    val rising = delta.signum() > 0
    return if (last.signum() >= 0 && first.signum() != 0) {
        val percent = (delta.toDouble() / first.abs().toDouble() * 100).toInt()
        NetWorthChange.Percent(abs(percent), rising)
    } else {
        NetWorthChange.Delta(Amount(delta.abs()), rising)
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
