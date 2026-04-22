package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionsPreviewScope

private const val TAG = "TransactionsPreviewComponent"

@TransactionsPreviewScope
@dagger.Component(
    modules = [TransactionsPreviewComponent.Module::class],
    dependencies = [TransactionsPreviewComponent.Dependencies::class],
)
internal abstract class TransactionsPreviewComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerTransactionsPreviewComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
            .imageLoader(ImageLoader.empty())
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionsPreviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder

        @BindsInstance
        fun imageLoader(imageLoader: ImageLoader): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionsPreviewScope
        fun viewModel(
            importUseCase: ImportUseCase,
            amountFormatter: AmountFormatter,
            dateFormatter: DateFormatter,
        ): TransactionsPreviewViewModel = DefaultTransactionsPreviewViewModel(
            importUseCase = importUseCase,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
        )

        @Provides
        @TransactionsPreviewScope
        fun viewProvider(
            viewModel: TransactionsPreviewViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = TransactionsPreviewViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
