package com.hluhovskyi.zero

interface CleanupJob {
    suspend fun clearAllTables()
}
