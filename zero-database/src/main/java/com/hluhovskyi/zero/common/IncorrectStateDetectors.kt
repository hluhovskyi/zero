package com.hluhovskyi.zero.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

suspend fun IncorrectStateDetector.requireCurrentUserId(
    userId: Flow<Id.Known>,
    block: suspend (Id.Known) -> Unit
) {
    asyncRequireNonNull(
        value = userId.firstOrNull(),
        message = "Current user id is empty",
        block = block
    )
}