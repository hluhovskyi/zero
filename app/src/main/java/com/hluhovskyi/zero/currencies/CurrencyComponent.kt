package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.DatabaseComponent
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.resource.ResourceResolver
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CurrencyScope

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
        val androidUriResourceFactory: AndroidUriResourceFactory
        val localeProvider: LocaleProvider
        val exchangeRateService: ExchangeRateService
        val configurationRepository: ConfigurationRepository
        val zonedClock: ZonedClock
        val incorrectStateDetector: IncorrectStateDetector
        val databaseComponent: DatabaseComponent
        val logger: Logger
    }

    companion object {

        fun builder(dependencies: Dependencies): CurrencyComponent = DaggerCurrencyComponent.builder()
            .dependencies(dependencies)
            .build()
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        fun build(): CurrencyComponent
    }

    @dagger.Module
    object Module {

        @Provides
        @CurrencyScope
        internal fun currencyLoader(
            resourceResolver: ResourceResolver,
            androidUriResourceFactory: AndroidUriResourceFactory,
            localeProvider: LocaleProvider,
            exchangeRateService: ExchangeRateService,
            configurationRepository: ConfigurationRepository,
            zonedClock: ZonedClock,
            logger: Logger,
        ): CurrencyLoader = CompositeCurrencyLoader(
            delegate = PredefinedCurrencyLoader(
                resourceResolver = resourceResolver,
                androidUriResourceFactory = androidUriResourceFactory,
                localeProvider = localeProvider,
                logger = logger,
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
            databaseComponent: DatabaseComponent,
        ): CurrencyRepository {
            val baseRepository = JavaCurrencyRepository(
                localeProvider = localeProvider,
                currencyLoader = currencyLoader,
            )
            return databaseComponent.transform(baseRepository)
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
