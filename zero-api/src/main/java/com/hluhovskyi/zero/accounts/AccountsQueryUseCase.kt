package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AccountsQueryUseCase {

    fun queryAll(): Flow<List<Account>>

    data class Account(
        override val id: Id.Known,
        val name: String,
        val colorScheme: ColorScheme,
        val icon: Image,
    ) : Identifiable

    object Noop : AccountsQueryUseCase {
        override fun queryAll(): Flow<List<Account>> = flowOf(emptyList())
    }
}
