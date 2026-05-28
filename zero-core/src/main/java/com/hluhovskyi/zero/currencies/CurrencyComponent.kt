package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.resource.ResourceResolver
import dagger.BindsInstance
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CurrencyScope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class ExchangeRatesUri

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class ExchangeRateOverridesUri

@CurrencyScope
@dagger.Component(
    modules = [CurrencyComponent.Module::class],
    dependencies = [CurrencyComponent.Dependencies::class],
)
interface CurrencyComponent {

    val currencyRepository: CurrencyRepository
    val currencyPrimaryUseCase: CurrencyPrimaryUseCase
    val currencyConvertUseCase: CurrencyConvertUseCase

    interface Dependencies {

        val resourceResolver: ResourceResolver
        val localeProvider: LocaleProvider
        val exchangeRateService: ExchangeRateService
        val configurationRepository: ConfigurationRepository
        val zonedClock: ZonedClock
        val incorrectStateDetector: IncorrectStateDetector
        val currencyRepositoryTransformer: CurrencyRepository.Transformer
        val logger: Logger
    }

    companion object {

        fun create(
            dependencies: Dependencies,
            ratesUri: Uri,
            overridesUri: Uri,
        ): CurrencyComponent = DaggerCurrencyComponent.builder()
            .dependencies(dependencies)
            .ratesUri(ratesUri)
            .overridesUri(overridesUri)
            .build()
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun ratesUri(@ExchangeRatesUri uri: Uri): Builder

        @BindsInstance
        fun overridesUri(@ExchangeRateOverridesUri uri: Uri): Builder

        fun build(): CurrencyComponent
    }

    @dagger.Module
    object Module {

        @Provides
        @CurrencyScope
        internal fun currencyLoader(
            resourceResolver: ResourceResolver,
            @ExchangeRatesUri ratesUri: Uri,
            @ExchangeRateOverridesUri overridesUri: Uri,
            exchangeRateService: ExchangeRateService,
            configurationRepository: ConfigurationRepository,
            zonedClock: ZonedClock,
        ): CurrencyLoader = CompositeCurrencyLoader(
            bundled = BundledExchangeRates(
                resourceResolver = resourceResolver,
                ratesUri = ratesUri,
                overridesUri = overridesUri,
            ),
            exchangeRateService = exchangeRateService,
            store = ConfigurationRateSnapshotStore(configurationRepository),
            clock = zonedClock,
        )

        @Provides
        @CurrencyScope
        internal fun currencyRepository(
            localeProvider: LocaleProvider,
            currencyLoader: CurrencyLoader,
            currencyRepositoryTransformer: CurrencyRepository.Transformer,
        ): CurrencyRepository {
            val baseRepository = JavaCurrencyRepository(
                localeProvider = localeProvider,
                currencyLoader = currencyLoader,
            )
            return currencyRepositoryTransformer.transform(baseRepository)
        }

        @Provides
        @CurrencyScope
        internal fun currencyPrimaryUseCase(
            configurationRepository: ConfigurationRepository,
            currencyRepository: CurrencyRepository,
            localeProvider: LocaleProvider,
            logger: Logger,
        ): CurrencyPrimaryUseCase = LocaleBasedCurrencyPrimaryUseCase(
            configurationRepository = configurationRepository,
            currencyRepository = currencyRepository,
            localeProvider = localeProvider,
            logger = logger,
        )

        @Provides
        @CurrencyScope
        internal fun currencyConvertUseCase(
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            currencyLoader: CurrencyLoader,
            incorrectStateDetector: IncorrectStateDetector,
        ): CurrencyConvertUseCase = PredefinedCurrencyConvertUseCase(
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyLoader = currencyLoader,
            incorrectStateDetector = incorrectStateDetector,
        )
    }
}
