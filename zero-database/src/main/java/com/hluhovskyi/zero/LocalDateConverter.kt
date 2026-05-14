package com.hluhovskyi.zero

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

internal object LocalDateConverter {

    @TypeConverter
    fun localDateToString(date: LocalDate): String = date.toString()

    @TypeConverter
    fun stringToLocalDate(rawDate: String): LocalDate = LocalDate.parse(rawDate)
}
