package com.hluhovskyi.zero.transactions.edit.income

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.OnShowAllCategoriesHandler
import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionEditIncomeScope

private const val TAG = "TransactionEditIncomeComponent"

@TransactionEditIncomeScope
@dagger.Component(
    modules = [TransactionEditIncomeComponent.Module::class],
    dependencies = [TransactionEditIncomeComponent.Dependencies::class]
)
abstract class TransactionEditIncomeComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {
        val imageLoader: ImageLoader
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditIncomeComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditIncomeComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionEditUseCase(useCase: TransactionEditUseCase): Builder

        @BindsInstance
        fun onShowAllCategoriesHandler(handler: OnShowAllCategoriesHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionEditIncomeScope
        fun viewModel(
            transactionEditUseCase: TransactionEditUseCase
        ): TransactionEditIncomeViewModel = DefaultTransactionEditIncomeViewModel(
            useCase = transactionEditUseCase
        )

        @Provides
        @TransactionEditIncomeScope
        fun viewProvider(
            viewModel: TransactionEditIncomeViewModel,
            imageLoader: ImageLoader,
            onShowAllCategoriesHandler: OnShowAllCategoriesHandler,
        ): ViewProvider = TransactionEditIncomeViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            onShowAllCategoriesHandler = onShowAllCategoriesHandler,
        )
    }
}