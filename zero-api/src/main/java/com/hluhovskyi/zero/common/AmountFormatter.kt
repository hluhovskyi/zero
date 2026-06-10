package com.hluhovskyi.zero.common

interface AmountFormatter {

    fun format(amount: Amount, currencySymbol: String = "", style: Style = Style.Full): String

    enum class Style {
        /** Exact value with grouping and two decimals, e.g. `1,234.50`. */
        Full,

        /** Compact magnitude for tight spaces, e.g. `2.2K`, `4.5M`, `105.7B`. */
        Short,
    }
}
