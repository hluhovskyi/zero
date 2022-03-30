package com.hluhovskyi.zero

import android.content.Context
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.activity.ActivityComponent
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.PredefinedMaterialColorRepository
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.CrashingIncorrectStateDetector
import com.hluhovskyi.zero.common.DefaultAndroidUriResourceFactory
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.SystemLocaleProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.SystemZoneProvider
import com.hluhovskyi.zero.common.time.ZoneBasedClock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.JavaCurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.icons.PredefinedIconRepository
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceResolverComponent
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
    dependencies = [ApplicationComponent.Dependencies::class]
)
abstract class ApplicationComponent :
    ActivityComponent.Dependencies,
    DatabaseComponent.Dependencies,
    ResourceResolverComponent.Dependencies,
    ZenMoneyImportComponent.Dependencies {

    abstract val activityComponentBuilder: ActivityComponent.Builder

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
            ImportModule::class,
        ]
    )
    object Module {

        @Provides
        @ApplicationScope
        fun logger(): Logger = TimberLogger()

        @Provides
        @ApplicationScope
        fun incorrectStateDetector(): IncorrectStateDetector =
            if (BuildConfig.DEBUG) {
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
        fun clock(
            zoneProvider: ZoneProvider
        ): Clock = ZoneBasedClock(zoneProvider = zoneProvider)

        @Provides
        @ApplicationScope
        fun imageLoader(
            context: Context
        ): ImageLoader = ImageLoader.factory(context).create()

        @Provides
        @ApplicationScope
        fun resourceResolver(
            component: ApplicationComponent
        ): ResourceResolver = ResourceResolverComponent.builder(component)
            .build()
            .resourceResolver

        @Provides
        @ApplicationScope
        fun androidUriResourceFactory(
            context: Context
        ): AndroidUriResourceFactory = DefaultAndroidUriResourceFactory(
            packageName = context.packageName
        )

        @Provides
        @ApplicationScope
        fun currencyRepository(
            localeProvider: LocaleProvider
        ): CurrencyRepository = JavaCurrencyRepository(
            localeProvider = localeProvider
        )

        @Provides
        @ApplicationScope
        fun iconRepository(
            androidUriResourceFactory: AndroidUriResourceFactory
        ): IconRepository = PredefinedIconRepository(
            androidUriResourceFactory = androidUriResourceFactory
        )

        @Provides
        @ApplicationScope
        fun colorsRepository(): ColorRepository = PredefinedMaterialColorRepository()

        @Provides
        @ApplicationScope
        fun categoriesQueryUseCase(
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
        ): CategoriesQueryUseCase = CategoryComponent.queryUseCase(
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository
        )

        @Provides
        @ApplicationScope
        fun activityComponentBuilder(
            component: ApplicationComponent,
            logger: Logger,
            idGenerator: IdGenerator
        ): ActivityComponent.Builder = ActivityComponent.builder(component)
            .logger(logger)
            .idGenerator(idGenerator)

    }
}

@dagger.Module
private object DatabaseModule {

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
            }
        )
        .build()

    @Provides
    @ApplicationScope
    fun currentUserRepository(
        databaseComponent: DatabaseComponent
    ): CurrentUserRepository = databaseComponent.currentUserRepository

    @Provides
    @ApplicationScope
    fun transactionRepository(
        databaseComponent: DatabaseComponent
    ): TransactionRepository = databaseComponent.transactionRepository

    @Provides
    @ApplicationScope
    fun accountRepository(
        databaseComponent: DatabaseComponent
    ): AccountRepository = databaseComponent.accountRepository

    @Provides
    @ApplicationScope
    fun categoryRepository(
        databaseComponent: DatabaseComponent,
    ): CategoryRepository = databaseComponent.categoryRepository
}

@dagger.Module
private object ImportModule {

    @Provides
    @ApplicationScope
    fun zenMoneyImportComponent(
        component: ApplicationComponent,
        androidUriResourceFactory: AndroidUriResourceFactory,
    ): ZenMoneyImportComponent.Builder = ZenMoneyImportComponent.builder(component)
        .importFileUri(androidUriResourceFactory.raw("zenmoney"))
}
