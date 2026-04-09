package com.hluhovskyi.zero.users

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Id

private const val INTERNAL_CURRENT_USER_ID = "INTERNAL_CURRENT_USER_ID"

@Entity
internal data class CurrentUserEntity(
    @PrimaryKey val id: String = INTERNAL_CURRENT_USER_ID,
    val userId: Id.Known,
)
