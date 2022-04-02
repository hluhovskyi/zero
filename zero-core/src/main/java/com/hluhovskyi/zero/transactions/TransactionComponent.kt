package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionScope

private const val TAG = "TransactionComponent"

@TransactionScope
@dagger.Component(
    modules = [TransactionComponent.Module::class],
    dependencies = [TransactionComponent.Dependencies::class]
)
abstract class TransactionComponent : AttachableViewComponent {

    internal abstract val viewModel: TransactionViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter

        val transactionRepository: TransactionRepository
        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionComponent> {

        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionScope
        fun viewModel(
            transactionRepository: TransactionRepository,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
        ): TransactionViewModel = DefaultTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            categoriesQueryUseCase = categoriesQueryUseCase
        )

        @Provides
        @TransactionScope
        fun viewProvider(
            viewModel: TransactionViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = TransactionViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

