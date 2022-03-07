package com.hluhovskyi.zero

import androidx.room.TypeConverter
import java.math.BigDecimal

object BigDecimalConverters {

    @TypeConverter
    fun bigDecimalToLong(value: BigDecimal): Long = value.longValueExact()

    @TypeConverter
    fun longToBigDecimal(value: Long): BigDecimal = BigDecimal.valueOf(value)
}