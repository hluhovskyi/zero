package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id

interface CurrencyPrimaryUseCase {

    suspend fun getPrimaryCurrency(): Currency

    suspend fun setPrimaryCurrency(id: Id.Known)
}