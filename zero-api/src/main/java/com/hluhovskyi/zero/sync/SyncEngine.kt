package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri

interface SyncEngine {
    suspend fun export(userId: Id.Known): SyncSnapshot
    suspend fun import(snapshot: SyncSnapshot, userId: Id.Known)
    suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot
    suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot
}
