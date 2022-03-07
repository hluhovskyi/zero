package com.hluhovskyi.zero

import android.content.Context
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.StubAccountRepository
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.StubCurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.Provides
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
        component: ApplicationComponent
    ): DatabaseComponent = DatabaseComponent.builder(component)
        .build()

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
}