package com.hluhovskyi.zero.imports.accounts

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ImportAccountPickerScope

private const val TAG = "ImportAccountPickerComponent"

@ImportAccountPickerScope
@dagger.Component(
    modules = [ImportAccountPickerComponent.Module::class],
    dependencies = [ImportAccountPickerComponent.Dependencies::class],
)
internal abstract class ImportAccountPickerComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: ImportAccountPickerViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerImportAccountPickerComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportAccountPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(importUseCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportAccountPickerScope
        fun viewModel(
            importUseCase: ImportUseCase,
        ): ImportAccountPickerViewModel = DefaultImportAccountPickerViewModel(
            importUseCase = importUseCase,
        )

        @Provides
        @ImportAccountPickerScope
        fun viewProvider(
            viewModel: ImportAccountPickerViewModel,
        ): ViewProvider = ImportAccountPickerViewProvider(
            viewModel = viewModel,
        )
    }
}
