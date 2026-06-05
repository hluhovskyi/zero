package com.hluhovskyi.zero.ui.chart

/** y-fraction in [0,1] where 1 == series max. Empty → empty; single/flat → all 0.5 (centered). */
internal fun normalizeToFractions(values: List<Float>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val min = values.min()
    val max = values.max()
    if (max == min) return List(values.size) { 0.5f }
    return values.map { (it - min) / (max - min) }
}

/** Bar width (dp) chosen so a grouped/single bar chart stays readable (~5–14 buckets). */
internal fun adaptiveBarWidthDp(bucketCount: Int): Int = when {
    bucketCount <= 6 -> 12
    bucketCount <= 9 -> 10
    bucketCount <= 12 -> 8
    bucketCount <= 16 -> 5
    else -> 3
}

internal fun adaptiveInnerGapDp(bucketCount: Int): Int = if (bucketCount <= 12) 3 else 1

internal fun shouldShowBarLabels(bucketCount: Int): Boolean = bucketCount <= 14
