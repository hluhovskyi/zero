package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import kotlin.math.abs

/** Trailing months kept in the net-worth trend (matches the design's 12-month window). */
internal const val NET_WORTH_TREND_MONTHS = 12

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
 * Net worth at the end of each month, oldest → newest, last == [currentNetWorth]. Walks the
 * ordered [monthlyNetDeltas] (per-month income − expense, oldest → newest) backward from the
 * live total. Leading months with no activity are trimmed, keeping the pre-activity baseline as
 * the first point. No activity at all → a single current point (chart shows a lone dot).
 */
internal fun reconstructNetWorthTrend(
    currentNetWorth: Amount,
    monthlyNetDeltas: List<Amount>,
): List<Amount> {
    val firstActive = monthlyNetDeltas.indexOfFirst { it.value.signum() != 0 }
    if (firstActive < 0) return listOf(currentNetWorth)
    val series = ArrayList<Amount>(monthlyNetDeltas.size)
    var nw = currentNetWorth
    for (i in monthlyNetDeltas.indices.reversed()) {
        series.add(nw)
        nw -= monthlyNetDeltas[i]
    }
    series.reverse() // oldest → newest; series[i] == net worth at the end of month i
    val start = (firstActive - 1).coerceAtLeast(0) // keep one pre-activity baseline point
    return series.subList(start, series.size).toList()
}
