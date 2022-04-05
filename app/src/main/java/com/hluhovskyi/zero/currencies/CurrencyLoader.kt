package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate

internal interface CurrencyLoader {

    suspend fun availableCurrencies(): Set<Id.Known>

    suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate>
}
