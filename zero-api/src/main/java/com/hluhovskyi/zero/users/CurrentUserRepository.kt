package com.hluhovskyi.zero.users

import kotlinx.coroutines.flow.Flow

interface CurrentUserRepository {

    fun query(): Flow<User>
}