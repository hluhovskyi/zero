package com.hluhovskyi.zero

import android.content.Context
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.StubAccountRepository
import com.hluhovskyi.zero.activity.ActivityComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.StubCategoryRepository
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.CrashingIncorrectStateDetector
import com.hluhovskyi.zero.common.DefaultAndroidUriResourceFactory
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.StubCurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.Provides
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
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
    DatabaseComponent.Dependencies {

    abstract val activityComponentBuilder: ActivityComponent.Builder

    interface Dependencies {
        val context: Context
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerApplicationComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        fun build(): ApplicationComponent
    }

    @dagger.Module(
        includes = [
            DatabaseModule::class
        ]
    )
    object Module {

        @Provides
        @ApplicationScope
        fun logger(): Logger = TimberLogger

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
        fun imageLoader(
            context: Context
        ): ImageLoader = ImageLoader.factory(context).create()

        @Provides
        @ApplicationScope
        fun androidUriResourceFactory(
            context: Context
        ): AndroidUriResourceFactory = DefaultAndroidUriResourceFactory(
            packageName = context.packageName
        )

        @Provides
        @ApplicationScope
        fun activityComponentBuilder(
            component: ApplicationComponent,
            logger: Logger
        ): ActivityComponent.Builder = ActivityComponent.builder(component)
            .logger(logger)

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
    fun accountRepository(): AccountRepository = StubAccountRepository()

    @Provides
    @ApplicationScope
    fun currencyRepository(): CurrencyRepository = StubCurrencyRepository()

    @Provides
    @ApplicationScope
    fun categoryRepository(
        androidUriResourceFactory: AndroidUriResourceFactory
    ): CategoryRepository = StubCategoryRepository(
        uriFactory = androidUriResourceFactory
    )
}