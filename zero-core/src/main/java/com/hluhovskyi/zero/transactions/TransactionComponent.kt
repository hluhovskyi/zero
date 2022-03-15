package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionScope

@TransactionScope
@dagger.Component(
    modules = [TransactionComponent.Module::class],
    dependencies = [TransactionComponent.Dependencies::class]
)
abstract class TransactionComponent : AttachableViewComponent {

    internal abstract val viewModel: TransactionViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionComponent.builder()
            .dependencies(dependencies)
            .transactionRepository(TransactionRepository.Noop)
            .accountRepository(AccountRepository.Noop)
            .currencyRepository(CurrencyRepository.Noop)
            .categoryRepository(CategoryRepository.Noop)
            .imageLoader(ImageLoader.empty())
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionRepository(transactionRepository: TransactionRepository): Builder

        @BindsInstance
        fun accountRepository(accountRepository: AccountRepository): Builder

        @BindsInstance
        fun currencyRepository(currencyRepository: CurrencyRepository): Builder

        @BindsInstance
        fun categoryRepository(categoryRepository: CategoryRepository): Builder

        @BindsInstance
        fun imageLoader(imageLoader: ImageLoader): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionScope
        fun viewModel(
            transactionRepository: TransactionRepository,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            categoryRepository: CategoryRepository
        ): TransactionViewModel = DefaultTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            categoryRepository = categoryRepository,
        )

        @Provides
        @TransactionScope
        fun viewProvider(
            viewModel: TransactionViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = TransactionViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

