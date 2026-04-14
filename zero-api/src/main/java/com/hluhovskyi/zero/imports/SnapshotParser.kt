package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncSnapshot

interface SnapshotParser {
    val source: Source
    suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
}
