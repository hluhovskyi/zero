package com.hluhovskyi.zero.transactions.preview

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Qualifier
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionPreviewScope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionId

private const val TAG = "TransactionPreviewComponent"

@TransactionPreviewScope
@dagger.Component(
    dependencies = [TransactionPreviewComponent.Dependencies::class],
    modules = [TransactionPreviewComponent.Module::class]
)
abstract class TransactionPreviewComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: TransactionPreviewViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val dispatcherProvider: DispatcherProvider
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter

        val transactionRepository: TransactionRepository
        val iconRepository: IconRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionPreviewComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionPreviewComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionId(@TransactionId transactionId: Id): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionPreviewScope
        fun viewModel(
            @TransactionId transactionId: Id,
            transactionRepository: TransactionRepository,
            iconRepository: IconRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            dispatcherProvider: DispatcherProvider,
        ): TransactionPreviewViewModel = DefaultTransactionPreviewViewModel(
            transactionId = transactionId,
            transactionRepository = transactionRepository,
            dispatchers = dispatcherProvider
        )

        @Provides
        @TransactionPreviewScope
        fun viewProvider(
            viewModel: TransactionPreviewViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = TransactionPreviewViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
