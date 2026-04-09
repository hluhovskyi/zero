package com.hluhovskyi.zero.common

import java.math.BigDecimal

fun String.toBigDecimalOrZero(): BigDecimal = toBigDecimalOrNull() ?: BigDecimal.ZERO
