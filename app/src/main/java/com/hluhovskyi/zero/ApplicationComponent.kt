package com.hluhovskyi.zero

import android.content.Context
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.activity.ActivityComponent
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.PredefinedMaterialColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.CrashingIncorrectStateDetector
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.DefaultAmountFormatter
import com.hluhovskyi.zero.common.DefaultAndroidUriResourceFactory
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.LocaleBasedDateFormatter
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.SystemLocaleProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.coroutines.KotlinDispatcherProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.SystemZoneProvider
import com.hluhovskyi.zero.common.time.ZoneBasedClock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyLoader
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.JavaCurrencyRepository
import com.hluhovskyi.zero.currencies.LocaleBasedCurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.PredefinedCurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.PredefinedCurrencyLoader
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.icons.PredefinedIconRepository
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceResolverComponent
import com.hluhovskyi.zero.settings.ExportWriter
import com.hluhovskyi.zero.sync.SyncComponent
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.Provides
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import javax.inject.Provider
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ApplicationScope

@ApplicationScope
@dagger.Component(
    modules = [ApplicationComponent.Module::class],
    dependencies = [ApplicationComponent.Dependencies::class],
)
abstract class ApplicationComponent :
    ActivityComponent.Dependencies,
    DatabaseComponent.Dependencies,
    ResourceResolverComponent.Dependencies {

    abstract val activityComponentBuilder: ActivityComponent.Builder
    abstract override val zoneProvider: ZoneProvider
    abstract val logger: Logger
    abstract override val serializer: SyncSerializer
    abstract override val exportWriter: ExportWriter
    abstract override val resourceResolver: ResourceResolver

    interface Dependencies {
        val context: Context
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerApplicationComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ApplicationComponent> {

        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module(
        includes = [
            DatabaseModule::class,
        ],
    )
    object Module {

        @Provides
        @ApplicationScope
        fun dispatcherProvider(): DispatcherProvider = KotlinDispatcherProvider

        @Provides
        @ApplicationScope
        fun logger(): Logger = TimberLogger()

        @Provides
        @ApplicationScope
        fun incorrectStateDetector(): IncorrectStateDetector = if (BuildConfig.DEBUG) {
            CrashingIncorrectStateDetector
        } else {
            // TODO: Report and collect incorrect state
            IncorrectStateDetector.ignoreIncorrect()
        }

        @Provides
        @ApplicationScope
        fun idGenerator(): IdGenerator = IdGenerator.UUID

        @Provides
        @ApplicationScope
        fun localeProvider(): LocaleProvider = SystemLocaleProvider

        @Provides
        @ApplicationScope
        fun zoneProvider(): ZoneProvider = SystemZoneProvider

        @Provides
        @ApplicationScope
        fun amountFormatter(): AmountFormatter = DefaultAmountFormatter()

        @Provides
        @ApplicationScope
        fun dateFormatter(
            localeProvider: LocaleProvider,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): DateFormatter = LocaleBasedDateFormatter(
            localeProvider = localeProvider,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @ApplicationScope
        fun clock(
            zoneProvider: ZoneProvider,
        ): Clock = ZoneBasedClock(zoneProvider = zoneProvider)

        @Provides
        @ApplicationScope
        fun imageLoader(
            context: Context,
        ): ImageLoader = ImageLoader.factory(context).create()

        @Provides
        @ApplicationScope
        fun resourceResolver(
            component: ApplicationComponent,
        ): ResourceResolver = ResourceResolverComponent.builder(component)
            .build()
            .resourceResolver

        @Provides
        @ApplicationScope
        fun androidUriResourceFactory(
            context: Context,
        ): AndroidUriResourceFactory = DefaultAndroidUriResourceFactory(
            packageName = context.packageName,
        )

        @Provides
        @ApplicationScope
        internal fun currencyLoader(
            resourceResolver: ResourceResolver,
            androidUriResourceFactory: AndroidUriResourceFactory,
            localeProvider: LocaleProvider,
            logger: Logger,
        ): CurrencyLoader = PredefinedCurrencyLoader(
            resourceResolver = resourceResolver,
            androidUriResourceFactory = androidUriResourceFactory,
            localeProvider = localeProvider,
            logger = logger,
        )

        @Provides
        @ApplicationScope
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
        @ApplicationScope
        fun currencyPrimaryUseCase(
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
        @ApplicationScope
        internal fun currencyConvertUseCase(
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            currencyLoader: CurrencyLoader,
            incorrectStateDetector: IncorrectStateDetector,
        ): CurrencyConvertUseCase = PredefinedCurrencyConvertUseCase(
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyLoader = currencyLoader,
            incorrectStateDetector = incorrectStateDetector,
        )

        @Provides
        @ApplicationScope
        fun iconRepository(
            androidUriResourceFactory: AndroidUriResourceFactory,
        ): IconRepository = PredefinedIconRepository(
            androidUriResourceFactory = androidUriResourceFactory,
        )

        @Provides
        @ApplicationScope
        fun colorsRepository(): ColorRepository = PredefinedMaterialColorRepository()

        @Provides
        @ApplicationScope
        fun categoryQueryUseCase(
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            transactionRepository: TransactionRepository,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): CategoriesQueryUseCase = CategoryComponent.queryUseCase(
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            transactionRepository = transactionRepository,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @ApplicationScope
        fun syncComponent(
            databaseComponent: DatabaseComponent,
            resourceResolver: ResourceResolver,
        ): SyncComponent = SyncComponent.factory(
            object : SyncComponent.Dependencies {
                override val categorySyncSource = databaseComponent.categorySyncSource()
                override val categorySyncSink = databaseComponent.categorySyncSink()
                override val accountSyncSource = databaseComponent.accountSyncSource()
                override val accountSyncSink = databaseComponent.accountSyncSink()
                override val transactionSyncSource = databaseComponent.transactionSyncSource()
                override val transactionSyncSink = databaseComponent.transactionSyncSink()
                override val resourceResolver = resourceResolver
            },
        ).create()

        @Provides
        fun syncEngine(syncComponent: SyncComponent): SyncEngine = syncComponent.syncEngine

        @Provides
        @ApplicationScope
        fun syncSerializer(syncComponent: SyncComponent): SyncSerializer = syncComponent.serializer

        @Provides
        @ApplicationScope
        fun exportWriter(context: Context): ExportWriter = ExportWriter { fileName, content ->
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val insertUri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues,
            ) ?: error("Could not create file in Downloads")
            context.contentResolver.openOutputStream(insertUri)?.use { output ->
                output.write(content.toByteArray())
            }
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(insertUri, contentValues, null, null)
        }

        @Provides
        @ApplicationScope
        fun activityComponentBuilder(
            component: ApplicationComponent,
            logger: Logger,
            idGenerator: IdGenerator,
        ): ActivityComponent.Builder = ActivityComponent.builder(component)
            .logger(logger)
            .idGenerator(idGenerator)
    }
}

@dagger.Module
internal object DatabaseModule {

    @Provides
    @ApplicationScope
    fun databaseComponent(
        component: ApplicationComponent,
        idGenerator: IdGenerator,
        logger: Logger,
        currentUserRepository: Provider<CurrentUserRepository>,
    ): DatabaseComponent = DatabaseComponent.builder(component)
        .idGenerator(idGenerator)
        .logger(logger)
        .currentUserId(
            channelFlow {
                currentUserRepository.get().query()
                    .map { user -> user.id }
                    .collectLatest(this::send)
            },
        )
        .build()

    @Provides
    @ApplicationScope
    fun currentUserRepository(
        databaseComponent: DatabaseComponent,
    ): CurrentUserRepository = databaseComponent.currentUserRepository

    @Provides
    @ApplicationScope
    fun transactionRepository(
        databaseComponent: DatabaseComponent,
    ): TransactionRepository = databaseComponent.transactionRepository

    @Provides
    @ApplicationScope
    fun accountRepository(
        databaseComponent: DatabaseComponent,
    ): AccountRepository = databaseComponent.accountRepository

    @Provides
    @ApplicationScope
    fun categoryRepository(
        databaseComponent: DatabaseComponent,
    ): CategoryRepository = databaseComponent.categoryRepository

    @Provides
    @ApplicationScope
    fun configurationRepository(
        databaseComponent: DatabaseComponent,
    ): ConfigurationRepository = databaseComponent.configurationRepository
}
