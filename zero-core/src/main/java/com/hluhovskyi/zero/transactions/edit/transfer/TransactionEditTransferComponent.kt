package com.hluhovskyi.zero.transactions.edit.transfer

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
private annotation class TransactionEditTransferScope

private const val TAG = "TransactionEditTransferComponent"

@TransactionEditTransferScope
@dagger.Component(
    modules = [TransactionEditTransferComponent.Module::class],
    dependencies = [TransactionEditTransferComponent.Dependencies::class]
)
abstract class TransactionEditTransferComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditTransferComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditTransferComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionEditUseCase(useCase: TransactionEditUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionEditTransferScope
        fun viewModel(
            useCase: TransactionEditUseCase
        ): TransactionEditTransferViewModel = DefaultTransactionEditTransferViewModel(
            useCase = useCase
        )

        @Provides
        @TransactionEditTransferScope
        fun viewProvider(
            viewModel: TransactionEditTransferViewModel
        ): ViewProvider = TransactionEditTransferViewProvider(
            viewModel = viewModel
        )
    }
}