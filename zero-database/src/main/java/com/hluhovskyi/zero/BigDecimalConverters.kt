package com.hluhovskyi.zero

import androidx.room.TypeConverter
import java.math.BigDecimal

object BigDecimalConverters {

    @TypeConverter
    fun bigDecimalToLong(value: BigDecimal): Double = value.toDouble()

    @TypeConverter
    fun longToBigDecimal(value: Double): BigDecimal = BigDecimal.valueOf(value)
}