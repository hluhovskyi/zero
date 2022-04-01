package com.hluhovskyi.zero.imports.filepicker

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
private annotation class ImportFilePickerScope

private const val TAG = "ImportFilePickerComponent"

@ImportFilePickerScope
@dagger.Component(
    modules = [ImportFilePickerComponent.Module::class],
    dependencies = [ImportFilePickerComponent.Dependencies::class],
)
internal abstract class ImportFilePickerComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerImportFilePickerComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportFilePickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(importUseCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportFilePickerScope
        fun viewModel(
            importUseCase: ImportUseCase
        ): ImportFilePickerViewModel = DefaultImportFilePickerViewModel(
            importUseCase = importUseCase
        )

        @Provides
        @ImportFilePickerScope
        fun viewProvider(
            viewModel: ImportFilePickerViewModel
        ): ViewProvider = ImportFilePickerViewProvider(
            viewModel = viewModel
        )
    }
}