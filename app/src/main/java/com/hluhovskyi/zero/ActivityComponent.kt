package com.hluhovskyi.zero

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
import dagger.BindsInstance
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ActivityScope

@ActivityScope
@dagger.Component(
    modules = [ActivityComponent.Module::class],
    dependencies = [ActivityComponent.Dependencies::class]
)
abstract class ActivityComponent :
    TransactionComponent.Dependencies,
    TransactionEditComponent.Dependencies {

    abstract val transactionComponentBuilder: TransactionComponent.Builder
    abstract val transactionEditComponentBuilder: TransactionEditComponent.Builder

    interface Dependencies {

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val transactionRepository: TransactionRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerActivityComponent.builder()
            .dependencies(dependencies)
            .logger(Logger.Noop)
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun logger(logger: Logger): Builder

        fun build(): ActivityComponent
    }

    @dagger.Module
    object Module {

        @Provides
        @ActivityScope
        fun transactionEditComponentBuilder(
            component: ActivityComponent,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            transactionRepository: TransactionRepository,
            logger: Logger,
        ): TransactionEditComponent.Builder = TransactionEditComponent.builder(component)
            .accountRepository(accountRepository)
            .currencyRepository(currencyRepository)
            .transactionRepository(transactionRepository)
            .logger(logger)

        @Provides
        @ActivityScope
        fun transactionComponentBuilder(
            component: ActivityComponent,
            transactionRepository: TransactionRepository,
        ): TransactionComponent.Builder = TransactionComponent.builder(component)
            .transactionRepository(transactionRepository)
    }
}