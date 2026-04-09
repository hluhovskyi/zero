package com.hluhovskyi.zero.categories

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Entity(
    indices = [Index("userId")],
)
data class CategoryEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val name: String,
    val iconId: String?,
    val colorId: String?,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
)
