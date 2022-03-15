package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.edit.CategoryEditComponent
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
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
    AttachableViewComponent,
    AccountComponent.Dependencies,
    AccountEditComponent.Dependencies,
    CategoryComponent.Dependencies,
    CategoryEditComponent.Dependencies,
    TransactionComponent.Dependencies,
    TransactionEditComponent.Dependencies {

    override fun attach(): Closeable = Closeables.empty()

    abstract val accountComponentBuilder: AccountComponent.Builder
    abstract val accountEditComponentBuilder: AccountEditComponent.Builder
    abstract val categoryComponentBuilder: CategoryComponent.Builder
    abstract val categoryEditComponentBuilder: CategoryEditComponent.Builder
    abstract val transactionComponentBuilder: TransactionComponent.Builder
    abstract val transactionEditComponentBuilder: TransactionEditComponent.Builder

    interface Dependencies {

        val imageLoader: ImageLoader
        val androidUriResourceFactory: AndroidUriResourceFactory

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val categoryRepository: CategoryRepository
        val transactionRepository: TransactionRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerActivityComponent.builder()
            .dependencies(dependencies)
            .logger(Logger.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ActivityComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun logger(logger: Logger): Builder
    }

    @dagger.Module(
        includes = [MainActivityModule::class]
    )
    object Module {

        @Provides
        @ActivityScope
        fun accountComponentBuilder(
            component: ActivityComponent,
            accountRepository: AccountRepository
        ): AccountComponent.Builder = AccountComponent.builder(component)
            .accountRepository(accountRepository)

        @Provides
        @ActivityScope
        fun accountEditComponentBuilder(
            component: ActivityComponent
        ): AccountEditComponent.Builder = AccountEditComponent.builder(component)

        @Provides
        @ActivityScope
        fun categoryComponentBuilder(
            component: ActivityComponent,
            categoryRepository: CategoryRepository,
            imageLoader: ImageLoader,
        ): CategoryComponent.Builder = CategoryComponent.builder(component)
            .categoryRepository(categoryRepository)
            .imageLoader(imageLoader)

        @Provides
        @ActivityScope
        fun categoryEditComponentBuilder(
            component: ActivityComponent
        ): CategoryEditComponent.Builder = CategoryEditComponent.builder(component)

        @Provides
        @ActivityScope
        fun transactionEditComponentBuilder(
            component: ActivityComponent,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            logger: Logger,
        ): TransactionEditComponent.Builder = TransactionEditComponent.builder(component)
            .accountRepository(accountRepository)
            .currencyRepository(currencyRepository)
            .transactionRepository(transactionRepository)
            .categoryRepository(categoryRepository)
            .logger(logger)

        @Provides
        @ActivityScope
        fun transactionComponentBuilder(
            component: ActivityComponent,
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            imageLoader: ImageLoader
        ): TransactionComponent.Builder = TransactionComponent.builder(component)
            .transactionRepository(transactionRepository)
            .categoryRepository(categoryRepository)
            .accountRepository(accountRepository)
            .currencyRepository(currencyRepository)
            .imageLoader(imageLoader)
    }
}

@dagger.Module
private object MainActivityModule {

    @Provides
    @ActivityScope
    fun viewModel(
        androidUriResourceFactory: AndroidUriResourceFactory
    ): MainActivityViewModel = DefaultMainActivityViewModel(
        androidUriResourceFactory = androidUriResourceFactory
    )

    @Provides
    @ActivityScope
    fun viewProvider(
        component: ActivityComponent,
        viewModel: MainActivityViewModel,
        imageLoader: ImageLoader
    ): ViewProvider = MainActivityViewProvider(
        activityComponent = component,
        viewModel = viewModel,
        imageLoader = imageLoader
    )
}