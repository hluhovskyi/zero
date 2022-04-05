package com.hluhovskyi.zero.config

import androidx.room.Entity
import com.hluhovskyi.zero.common.Id

@Entity(
    primaryKeys = ["scope", "name"]
)
data class ConfigurationEntity(
    val scope: String,
    val name: String,
    val userId: Id.Known,
    val value: String
)