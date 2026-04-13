package com.hluhovskyi.zero.sync

fun interface ConflictResolver<T : SyncEntity> {
    /**
     * Resolves a conflict between local and incoming versions of an entity.
     *
     * [local] is null when the entity only exists in the incoming set (new on remote).
     * [incoming] is null when the entity only exists locally (not on remote yet).
     *
     * Returns:
     *   empty list  — both discarded
     *   [one]       — one version wins
     *   [a, b]      — both survive as distinct entities (content-dedup use case)
     */
    fun resolve(local: T?, incoming: T?): List<T>
}
