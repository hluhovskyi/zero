package com.hluhovskyi.zero.users

import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "RoomCurrentUserRepository"

internal class RoomCurrentUserRepository(
    private val idGenerator: IdGenerator,
    private val currentUserRoom: () -> CurrentUserRoom,
    logger: Logger,
) : CurrentUserRepository {

    private val logger = logger.withTag(TAG)

    override fun query(): Flow<User> = flow {
        logger.d("query")
        val room = currentUserRoom()
        val savedEntity = room.selectFirst()
        val entity = if (savedEntity == null) {
            val userId = idGenerator()
            logger.d("query, generated userId=$userId")
            val target = CurrentUserEntity(userId = userId)
            room.insert(target)
            target
        } else {
            savedEntity
        }
        emit(User(id = entity.userId))
    }
}