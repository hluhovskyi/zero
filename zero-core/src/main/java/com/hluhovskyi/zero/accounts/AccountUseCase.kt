package com.hluhovskyi.zero.accounts

import kotlinx.coroutines.flow.Flow

interface AccountUseCase {

    val accounts: Flow<List<Account>>
}