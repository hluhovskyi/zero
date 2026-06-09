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

/** y-fractions plus the zero crossing, for a signed series whose domain always includes 0. */
internal data class SignedScale(val fractions: List<Float>, val zeroFraction: Float)

/**
 * Scale a signed series so that 0 is always inside the range (so under/over zero reads correctly).
 * Fraction 1f == top (series max, clamped ≥ 0), 0f == bottom (series min, clamped ≤ 0).
 * [SignedScale.zeroFraction] is where the zero baseline sits in that same 0..1 space.
 */
internal fun signedScale(values: List<Float>): SignedScale {
    if (values.isEmpty()) return SignedScale(emptyList(), 0.5f)
    val lo = minOf(0f, values.min())
    val hi = maxOf(0f, values.max())
    val span = (hi - lo).takeIf { it != 0f } ?: 1f
    return SignedScale(values.map { (it - lo) / span }, (0f - lo) / span)
}
