package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Rate

internal class PredefinedCurrencyConvertUseCase(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val currencyLoader: CurrencyLoader,
    private val incorrectStateDetector: IncorrectStateDetector,
) : CurrencyConvertUseCase {

    override suspend fun getRate(fromId: Id.Known, toId: Id.Known): Rate {
        return currencyLoader.ratesFor(fromId)[toId] ?: incorrectStateDetector.assertOrValue(
            message = "No rate is available from ${fromId.value} to ${toId.value}",
            value = Rate.Same,
        )
    }

    override suspend fun convertToPrimary(amount: Amount, currencyId: Id.Known): Amount {
        val primary = currencyPrimaryUseCase.getPrimaryCurrency().id

        if (currencyId == primary) {
            return amount
        }

        val rate = getRate(
            fromId = currencyId,
            toId = primary
        )

        return amount.withRate(rate)
    }
}