package com.hluhovskyi.zero.transactions.edit.common

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionEditExpenseIncomeScope

private const val TAG = "TransactionEditExpenseIncomeComponent"

@TransactionEditExpenseIncomeScope
@dagger.Component(
    dependencies = [TransactionEditExpenseIncomeComponent.Dependencies::class],
    modules = [TransactionEditExpenseIncomeComponent.Module::class],
)
abstract class TransactionEditExpenseIncomeComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {
        val imageLoader: ImageLoader
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditExpenseIncomeComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditExpenseIncomeComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionEditUseCase(useCase: TransactionEditUseCase): Builder

        @BindsInstance
        fun isNewTransaction(value: Boolean): Builder
    }

    @dagger.Module
    internal object Module {

        @Provides
        @TransactionEditExpenseIncomeScope
        fun viewModel(
            useCase: TransactionEditUseCase,
        ): TransactionEditExpenseIncomeViewModel = DefaultTransactionEditExpenseIncomeViewModel(
            useCase = useCase,
        )

        @Provides
        @TransactionEditExpenseIncomeScope
        fun viewProvider(
            viewModel: TransactionEditExpenseIncomeViewModel,
            imageLoader: ImageLoader,
            isNewTransaction: Boolean,
        ): ViewProvider = TransactionEditExpenseIncomeViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            isNewTransaction = isNewTransaction,
        )
    }
}
