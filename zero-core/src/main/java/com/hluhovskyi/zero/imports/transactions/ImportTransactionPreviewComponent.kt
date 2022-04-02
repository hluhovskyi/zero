package com.hluhovskyi.zero.imports.transactions

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ImportTransactionPreviewScope

private const val TAG = "ImportTransactionPreviewComponent"

@ImportTransactionPreviewScope
@dagger.Component(
    dependencies = [ImportTransactionPreviewComponent.Dependencies::class],
    modules = [ImportTransactionPreviewComponent.Module::class]
)
internal abstract class ImportTransactionPreviewComponent : AttachableViewComponent {

    override val tag: String = TAG

    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerImportTransactionPreviewComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportTransactionPreviewComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(importUseCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportTransactionPreviewScope
        fun viewModel(
            importUseCase: ImportUseCase
        ): ImportTransactionPreviewViewModel = DefaultImportTransactionPreviewViewModel(
            importUseCase = importUseCase
        )

        @Provides
        @ImportTransactionPreviewScope
        fun viewProvider(
            viewModel: ImportTransactionPreviewViewModel
        ): ViewProvider = ImportTransactionPreviewViewProvider(
            viewModel = viewModel
        )
    }
}
