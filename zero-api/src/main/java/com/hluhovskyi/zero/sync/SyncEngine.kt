package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id

interface SyncEngine {
    suspend fun export(userId: Id.Known): SyncSnapshot
    suspend fun import(snapshot: SyncSnapshot, userId: Id.Known)
}
