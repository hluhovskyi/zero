package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.firstOrDefault
import com.hluhovskyi.zero.config.write
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.Currency as JavaCurrency

internal class LocaleBasedCurrencyPrimaryUseCase(
    private val configurationRepository: ConfigurationRepository,
    private val currencyRepository: CurrencyRepository,
    private val localeProvider: LocaleProvider,
    logger: Logger,
    private val currencyProvider: (Locale) -> JavaCurrency = JavaCurrency::getInstance,
) : CurrencyPrimaryUseCase {

    private val logger = logger.withTag("LocaleBasedCurrencyPrimaryUseCase")

    private val cachedPrimaryCurrency = AtomicReference<Currency>(null)

    override suspend fun getPrimaryCurrency(): Currency {
        val cached = cachedPrimaryCurrency.get()
        if (cached != null) {
            return cached
        }

        val currency = configurationRepository.firstOrDefault(CurrencyConfigurationKey.PrimaryCurrency)
            .let { id -> id as? Id.Known }
            ?.let { primaryCurrencyId ->
                currencyRepository.query(CurrencyRepository.Criteria.ById(primaryCurrencyId))
                    .firstOrNull()
            }

        logger.d("getPrimaryCurrency, currency=$currency")

        val resultCurrency = currency ?: localeProvider.locale().let { locale ->
            currencyProvider(locale).toCurrency(locale).also {
                logger.d("getPrimaryCurrency, resolve initial, locale=$locale, currency=$it")
                configurationRepository.write(CurrencyConfigurationKey.PrimaryCurrency, it.id)
            }
        }

        cachedPrimaryCurrency.set(resultCurrency)

        return resultCurrency
    }

    override suspend fun setPrimaryCurrency(id: Id.Known) {
        configurationRepository.write(
            key = CurrencyConfigurationKey.PrimaryCurrency,
            value = id,
        ).also {
            cachedPrimaryCurrency.set(null)
        }
    }
}
