package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

interface SyncEntity {
    val id: Id.Known
    val updatedDateTime: LocalDateTime
    val deletedAt: LocalDateTime?
}
