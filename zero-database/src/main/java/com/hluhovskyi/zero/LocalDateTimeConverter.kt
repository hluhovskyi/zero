package com.hluhovskyi.zero

import androidx.room.TypeConverter
import java.time.LocalDateTime


internal object LocalDateTimeConverter {

    @TypeConverter
    fun localDateTimeToString(dateTime: LocalDateTime): String =
        dateTime.toString()

    @TypeConverter
    fun stringToLocalDateTime(rawDateTime: String): LocalDateTime =
        LocalDateTime.parse(rawDateTime)
}