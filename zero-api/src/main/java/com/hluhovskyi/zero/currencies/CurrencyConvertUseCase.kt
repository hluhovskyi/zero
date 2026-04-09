package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate

interface CurrencyConvertUseCase {

    suspend fun getRate(fromId: Id.Known, toId: Id.Known): Rate

    suspend fun convertToPrimary(amount: Amount, currencyId: Id.Known): Amount
}
