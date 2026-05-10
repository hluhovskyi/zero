package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionFilterSheetScope

private const val TAG = "TransactionFilterSheetComponent"

@TransactionFilterSheetScope
@dagger.Component(
    modules = [TransactionFilterSheetComponent.Module::class],
    dependencies = [TransactionFilterSheetComponent.Dependencies::class],
)
abstract class TransactionFilterSheetComponent : AttachableViewComponent {

    internal abstract val viewModel: TransactionFilterSheetViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val accountRepository: AccountRepository
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerTransactionFilterSheetComponent.builder()
            .dependencies(dependencies)
            .transactionFilterUseCase(TransactionFilterUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionFilterSheetComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionFilterUseCase(useCase: TransactionFilterUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionFilterSheetScope
        fun viewModel(
            transactionFilterUseCase: TransactionFilterUseCase,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            accountRepository: AccountRepository,
        ): TransactionFilterSheetViewModel = DefaultTransactionFilterSheetViewModel(
            transactionFilterUseCase = transactionFilterUseCase,
            categoriesQueryUseCase = categoriesQueryUseCase,
            accountRepository = accountRepository,
        )

        @Provides
        @TransactionFilterSheetScope
        fun viewProvider(
            viewModel: TransactionFilterSheetViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = TransactionFilterSheetViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
