package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot

class ZeroBackupParser(
    private val syncEngine: SyncEngine,
) : SnapshotParser {

    override val source: Source = KnownSource.ZeroBackup

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot = syncEngine.loadSnapshot(uri)
}
