package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.accounts.ImportAccountPickerComponent
import com.hluhovskyi.zero.imports.filepicker.ImportFilePickerComponent
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ImportScope

private const val TAG = "ImportComponent"

@ImportScope
@dagger.Component(
    modules = [ImportComponent.Module::class],
    dependencies = [ImportComponent.Dependencies::class],
)
abstract class ImportComponent : AttachableViewComponent,
    ImportFilePickerComponent.Dependencies,
    ImportAccountPickerComponent.Dependencies {

    override val tag: String = TAG

    internal abstract val useCase: ImportUseCase
    override fun attach(): Closeable = useCase.attach()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerImportComponent.builder()
            .dependencies(dependencies)
            .importSourceUseCase(ImportSourceUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importSourceUseCase(importSourceUseCase: ImportSourceUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportScope
        fun useCase(
            importSourceUseCase: ImportSourceUseCase
        ): ImportUseCase = DefaultImportUseCase(
            importSourceUseCase = importSourceUseCase
        )

        @Provides
        @ImportScope
        fun viewModel(
            importUseCase: ImportUseCase
        ): ImportViewModel = DefaultImportViewModel(
            importUseCase = importUseCase
        )

        @Provides
        @ImportScope
        internal fun filePickerComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase
        ): ImportFilePickerComponent.Builder = ImportFilePickerComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun accountPickerComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase
        ): ImportAccountPickerComponent.Builder = ImportAccountPickerComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun viewProvider(
            viewModel: ImportViewModel,
            filePickerComponentBuilder: ImportFilePickerComponent.Builder,
            accountPickerComponentBuilder: ImportAccountPickerComponent.Builder,
        ): ViewProvider = ImportViewProvider(
            viewModel = viewModel,
            filePicker = filePickerComponentBuilder,
            accountPicker = accountPickerComponentBuilder
        )
    }
}