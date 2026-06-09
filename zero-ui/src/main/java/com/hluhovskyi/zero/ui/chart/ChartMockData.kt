@file:Suppress("ZeroThemeBypass")

package com.hluhovskyi.zero.ui.chart

import androidx.compose.ui.graphics.Color

/** Mock data for the debug Charts gallery. Numbers taken straight from the Analytics design. */
internal object ChartMockData {

    // Cash flow (in, out) per month — oldest → newest.
    val flow6: List<Triple<String, Float, Float>> = listOf(
        Triple("Nov", 5050f, 3980f),
        Triple("Dec", 5600f, 4820f),
        Triple("Jan", 5050f, 3640f),
        Triple("Feb", 5050f, 3910f),
        Triple("Mar", 5300f, 4180f),
        Triple("Apr", 5050f, 3760f),
    )

    val netWorth6: List<Float> = listOf(18200f, 19000f, 20400f, 21500f, 22600f, 23900f)

    // Net worth underwater (improving but still negative) — for the signed chart, all below zero.
    val netWorthNegative: List<Float> = listOf(
        -14200f, -15100f, -13800f, -14600f, -12900f, -13400f,
        -11800f, -12500f, -10900f, -11600f, -9800f, -8400f,
    )

    // Net worth that climbs from underwater up through zero into the black.
    val netWorthCrossing: List<Float> = listOf(
        -3200f, -2400f, -2600f, -1500f, -800f, 300f, 1100f, 900f, 2200f, 3000f, 4100f, 5200f,
    )

    // Single-series category spend per month (label, amount) — for the trend bar chart.
    val categoryTrend: List<Pair<String, Float>> = listOf(
        "Nov" to 220f,
        "Dec" to 290f,
        "Jan" to 240f,
        "Feb" to 260f,
        "Mar" to 280f,
        "Apr" to 290f,
    )

    // Donut category split (name, dark-mapped swatch, spend). Dark-mapped because the app is dark.
    val categories: List<Triple<String, Color, Float>> = listOf(
        Triple("Housing", Color(0xFF9FAAE5), 9000f),
        Triple("Groceries", Color(0xFF9CC0FF), 2450f),
        Triple("Food & Drink", Color(0xFFFF9AA0), 2210f),
        Triple("Transport", Color(0xFFFFB272), 1580f),
        Triple("Shopping", Color(0xFFF58FB1), 1680f),
        Triple("Utilities", Color(0xFFF2C674), 1260f),
        Triple("Other", Color(0xFFBABCC6), 4090f),
    )
}
