package com.hluhovskyi.zero

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.AccountsQueryUseCase
import com.hluhovskyi.zero.activity.ActivityComponent
import com.hluhovskyi.zero.activity.CurrentActivityTracker
import com.hluhovskyi.zero.auth.AuthComponent
import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.backup.BackupClient
import com.hluhovskyi.zero.backup.BackupComponent
import com.hluhovskyi.zero.backup.BackupUseCase
import com.hluhovskyi.zero.backup.DriveComponent
import com.hluhovskyi.zero.budget.BudgetComponent
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.PredefinedMaterialColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Attachable
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
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyLoader
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.JavaCurrencyRepository
import com.hluhovskyi.zero.currencies.LocaleBasedCurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.PredefinedCurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.PredefinedCurrencyLoader
import com.hluhovskyi.zero.export.DefaultExportWriter
import com.hluhovskyi.zero.export.ExportWriter
import com.hluhovskyi.zero.feedback.DeviceInfo
import com.hluhovskyi.zero.feedback.FeedbackService
import com.hluhovskyi.zero.http.HttpExecutor
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.icons.PredefinedIconRepository
import com.hluhovskyi.zero.imports.ImportComponent
import com.hluhovskyi.zero.imports.SnapshotParser
import com.hluhovskyi.zero.imports.ZenMoneySnapshotParser
import com.hluhovskyi.zero.imports.ZeroBackupParser
import com.hluhovskyi.zero.presets.PresetsComponent
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceResolverComponent
import com.hluhovskyi.zero.security.AndroidSecureKeyValueStore
import com.hluhovskyi.zero.security.SecureKeyValueStore
import com.hluhovskyi.zero.settings.SettingsComponent
import com.hluhovskyi.zero.sync.SyncComponent
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    AuthComponent.Dependencies,
    CrashComponent.Dependencies,
    DatabaseComponent.Dependencies,
    RemoteComponent.Dependencies,
    ResourceResolverComponent.Dependencies,
    SettingsComponent.Dependencies,
    ImportComponent.Dependencies {

    abstract val activityComponentBuilder: ActivityComponent.Builder
    abstract val attachable: Attachable
    abstract val logger: Logger
    abstract val currentActivityTracker: CurrentActivityTracker
    abstract val databaseComponent: DatabaseComponent
    abstract override val feedbackService: FeedbackService
    abstract override val deviceInfo: DeviceInfo

    interface Dependencies {

        val context: Context
        val application: Application
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
            CrashModule::class,
            DatabaseModule::class,
            RemoteModule::class,
            AuthModule::class,
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
        fun deviceInfo(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            osVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
        )

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
        fun zonedClock(
            zoneProvider: ZoneProvider,
        ): ZonedClock = ZoneBasedClock(zoneProvider = zoneProvider)

        @Provides
        @ApplicationScope
        fun clock(zonedClock: ZonedClock): Clock = zonedClock

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
            context = context,
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
        fun accountsQueryUseCase(
            accountRepository: AccountRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
        ): AccountsQueryUseCase = AccountComponent.queryUseCase(
            accountRepository = accountRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
        )

        @Provides
        @ApplicationScope
        fun budgetQueryUseCase(
            categoriesQueryUseCase: CategoriesQueryUseCase,
            budgetRepository: BudgetRepository,
            transactionRepository: TransactionRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): BudgetQueryUseCase = BudgetComponent.queryUseCase(
            categoriesQueryUseCase = categoriesQueryUseCase,
            budgetRepository = budgetRepository,
            categorySpendingUseCase = CategoryComponent.spendingUseCase(
                transactionRepository = transactionRepository,
                currencyConvertUseCase = currencyConvertUseCase,
                clock = clock,
                zoneProvider = zoneProvider,
            ),
        )

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
        fun presetsComponent(
            categoryRepository: CategoryRepository,
            accountRepository: AccountRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            configurationRepository: ConfigurationRepository,
        ): PresetsComponent = PresetsComponent.create(
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            configurationRepository = configurationRepository,
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
                override val budgetSyncSource = databaseComponent.budgetSyncSource()
                override val budgetSyncSink = databaseComponent.budgetSyncSink()
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
        fun currentActivityTracker(application: Application): CurrentActivityTracker = CurrentActivityTracker(application)

        @Provides
        @ApplicationScope
        fun currentActivityProvider(
            tracker: CurrentActivityTracker,
        ): @JvmSuppressWildcards () -> Activity? = { tracker.current() }

        @Provides
        @ApplicationScope
        fun driveComponent(
            httpExecutor: HttpExecutor,
            oauthTokenProvider: OAuthTokenProvider,
        ): DriveComponent = DriveComponent.factory(
            object : DriveComponent.Dependencies {
                override val httpExecutor = httpExecutor
                override val oauthTokenProvider = oauthTokenProvider
            },
        ).create()

        @Provides
        @ApplicationScope
        fun backupClient(driveComponent: DriveComponent): BackupClient = driveComponent.backupClient

        @Provides
        @ApplicationScope
        fun backupComponent(
            syncEngine: SyncEngine,
            backupClient: BackupClient,
            currentUserRepository: CurrentUserRepository,
        ): BackupComponent = BackupComponent.factory(
            object : BackupComponent.Dependencies {
                override val syncEngine = syncEngine
                override val backupClient = backupClient
                override val currentUserRepository = currentUserRepository
                override val backupCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            },
        ).create()

        @Provides
        fun backupUseCase(backupComponent: BackupComponent): BackupUseCase = backupComponent.backupUseCase

        @Provides
        @ApplicationScope
        fun secureKeyValueStore(context: Context): SecureKeyValueStore = AndroidSecureKeyValueStore(context)

        @Provides
        @ApplicationScope
        fun exportWriter(context: Context): ExportWriter = DefaultExportWriter(context)

        @Provides
        fun importComponentBuilder(
            component: ApplicationComponent,
            syncEngine: SyncEngine,
            clock: Clock,
            idGenerator: IdGenerator,
            logger: Logger,
            resourceResolver: ResourceResolver,
        ): ImportComponent.Builder {
            val parsers: List<SnapshotParser> = listOf(
                ZeroBackupParser(syncEngine = syncEngine),
                ZenMoneySnapshotParser(
                    resourceResolver = resourceResolver,
                    idGenerator = idGenerator,
                    clock = clock,
                    logger = logger,
                ),
            )
            return ImportComponent.builder(component)
                .parsers(parsers)
        }

        @Provides
        fun settingsComponentBuilder(
            component: ApplicationComponent,
        ): SettingsComponent.Builder = SettingsComponent.builder(component)

        @Provides
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
    fun budgetRepository(
        databaseComponent: DatabaseComponent,
    ): BudgetRepository = databaseComponent.budgetRepository

    @Provides
    @ApplicationScope
    fun configurationRepository(
        databaseComponent: DatabaseComponent,
    ): ConfigurationRepository = databaseComponent.configurationRepository
}

@dagger.Module
internal object RemoteModule {

    @Provides
    @ApplicationScope
    fun remoteComponent(
        component: ApplicationComponent,
    ): RemoteComponent = RemoteComponent.builder(component)
        .feedbackEndpoint(BuildConfig.FEEDBACK_ENDPOINT)
        .integrityCloudProject(BuildConfig.FEEDBACK_INTEGRITY_PROJECT.toLongOrNull() ?: 0L)
        .build()

    @Provides
    @ApplicationScope
    fun feedbackService(
        remoteComponent: RemoteComponent,
    ): FeedbackService = remoteComponent.feedbackService

    @Provides
    @ApplicationScope
    fun httpExecutor(
        remoteComponent: RemoteComponent,
    ): HttpExecutor = remoteComponent.httpExecutor
}

@dagger.Module
internal object AuthModule {

    @Provides
    @ApplicationScope
    fun authComponent(component: ApplicationComponent): AuthComponent = AuthComponent.builder()
        .dependencies(component)
        .build()

    @Provides
    @ApplicationScope
    fun oauthTokenProvider(authComponent: AuthComponent): OAuthTokenProvider = authComponent.googleOAuthTokenProvider
}

@dagger.Module
internal object CrashModule {

    @Provides
    @ApplicationScope
    fun crashComponent(
        component: ApplicationComponent,
    ): CrashComponent = CrashComponent.builder(component)
        .versionName(BuildConfig.VERSION_NAME)
        .build()

    @Provides
    @ApplicationScope
    fun attachable(
        crashComponent: CrashComponent,
    ): Attachable = AttachApplicationComponent(crashComponent)
}
