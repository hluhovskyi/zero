package com.hluhovskyi.zero

interface CleanupJob {
    suspend fun clearAllTables()
    // Clears entity data but keeps the CurrentUserEntity row so that in-flight
    // Flow subscriptions (which captured the user ID via take(1)) continue
    // to see updates after test data is re-seeded.
    suspend fun clearExceptUser()
}
