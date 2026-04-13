package com.hluhovskyi.zero.sync

class LastWriteWinsResolver<T : SyncEntity> : ConflictResolver<T> {

    override fun resolve(local: T?, incoming: T?): List<T> = when {
        local == null -> listOfNotNull(incoming)
        incoming == null -> listOf(local)
        incoming.updatedDateTime > local.updatedDateTime -> listOf(incoming)
        else -> listOf(local)
    }
}
