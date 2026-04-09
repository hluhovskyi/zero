package com.hluhovskyi.zero

import androidx.room.TypeConverter
import com.hluhovskyi.zero.common.Id

object IdConverters {

    @TypeConverter
    fun idToString(id: Id.Known): String = id.value

    @TypeConverter
    fun stringToId(id: String): Id.Known = Id(id)
}
