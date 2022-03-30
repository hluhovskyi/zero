package com.hluhovskyi.zero.config

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Id

@Entity
data class ConfigurationEntity(
    @PrimaryKey val name: String,
    val userId: Id.Known,
    val value: String
)