package com.hluhovskyi.zero.transactions.edit.expense

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
private annotation class TransactionEditExpenseScope

private const val TAG = "TransactionEditExpenseComponent"

@TransactionEditExpenseScope
@dagger.Component(
    dependencies = [TransactionEditExpenseComponent.Dependencies::class],
    modules = [TransactionEditExpenseComponent.Module::class],
)
abstract class TransactionEditExpenseComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {
        val imageLoader: ImageLoader
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditExpenseComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditExpenseComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionEditUseCase(useCase: TransactionEditUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionEditExpenseScope
        fun viewModel(
            useCase: TransactionEditUseCase,
        ): TransactionEditExpenseViewModel = DefaultTransactionEditExpenseViewModel(
            useCase = useCase,
        )

        @Provides
        @TransactionEditExpenseScope
        fun viewProvider(
            viewModel: TransactionEditExpenseViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = TransactionEditExpenseViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
