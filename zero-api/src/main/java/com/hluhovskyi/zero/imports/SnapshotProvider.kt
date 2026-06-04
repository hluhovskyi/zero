package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncSnapshot

sealed interface SnapshotProvider {

    val source: Source

    /** Decodes a user-picked file into a snapshot. */
    interface File : SnapshotProvider {
        suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
    }

    /** Fetches a snapshot from a remote backend; may sign in and hit the network. */
    interface Remote : SnapshotProvider {
        suspend fun load(): SyncSnapshot
    }
}
