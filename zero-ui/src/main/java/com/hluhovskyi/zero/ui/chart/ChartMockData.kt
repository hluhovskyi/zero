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

    val flow12: List<Triple<String, Float, Float>> = listOf(
        Triple("May", 4900f, 3700f), Triple("Jun", 5200f, 3850f),
        Triple("Jul", 4950f, 4020f), Triple("Aug", 5050f, 3680f),
        Triple("Sep", 5250f, 3990f), Triple("Oct", 5000f, 4500f),
        Triple("Nov", 5050f, 4900f), Triple("Dec", 5600f, 4820f),
        Triple("Jan", 5050f, 3640f), Triple("Feb", 5050f, 3910f),
        Triple("Mar", 5300f, 4180f), Triple("Apr", 5050f, 3760f),
    )

    // 1 month → weekly buckets (biweekly pay → lumpy income).
    val weekly: List<Triple<String, Float, Float>> = listOf(
        Triple("W1", 2525f, 1040f),
        Triple("W2", 0f, 880f),
        Triple("W3", 2525f, 1180f),
        Triple("W4", 0f, 640f),
        Triple("W5", 0f, 210f),
    )

    val netWorth6: List<Float> = listOf(18200f, 19000f, 20400f, 21500f, 22600f, 23900f)
    val netWorth12: List<Float> = listOf(
        15800f, 16200f, 16050f, 16900f, 17500f, 17300f,
        18200f, 19000f, 20400f, 21500f, 22600f, 23900f,
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
