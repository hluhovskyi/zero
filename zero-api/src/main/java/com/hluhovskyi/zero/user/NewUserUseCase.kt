package com.hluhovskyi.zero.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface NewUserUseCase {

    fun isNewUser(): Flow<Boolean>

    object Noop : NewUserUseCase {
        override fun isNewUser(): Flow<Boolean> = flowOf(false)
    }
}
