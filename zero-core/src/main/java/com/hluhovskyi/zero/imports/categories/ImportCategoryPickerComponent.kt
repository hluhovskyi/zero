package com.hluhovskyi.zero.imports.categories

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention
private annotation class ImportCategoriesPickerScope

private const val TAG = "ImportCategoriesPickerComponent"

@ImportCategoriesPickerScope
@dagger.Component(
    modules = [ImportCategoriesPickerComponent.Module::class],
    dependencies = [ImportCategoriesPickerComponent.Dependencies::class],
)
internal abstract class ImportCategoriesPickerComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: ImportCategoryPickerViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerImportCategoriesPickerComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportCategoriesPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(importUseCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportCategoriesPickerScope
        fun viewModel(
            importUseCase: ImportUseCase,
        ): ImportCategoryPickerViewModel = DefaultImportCategoryPickerViewModel(
            importUseCase = importUseCase,
        )

        @Provides
        @ImportCategoriesPickerScope
        fun viewProvider(
            viewModel: ImportCategoryPickerViewModel,
        ): ViewProvider = ImportCategoryPickerViewProvider(
            viewModel = viewModel,
        )
    }
}
