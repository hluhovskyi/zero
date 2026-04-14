// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionComponent.kt
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import com.hluhovskyi.zero.imports.OnImportFinishedHandler
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class SourceSelectionScope

private const val TAG = "SourceSelectionComponent"

@SourceSelectionScope
@dagger.Component(
    modules = [SourceSelectionComponent.Module::class],
    dependencies = [SourceSelectionComponent.Dependencies::class],
)
internal abstract class SourceSelectionComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerSourceSelectionComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
            .onImportFinishedHandler(OnImportFinishedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<SourceSelectionComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder

        @BindsInstance
        fun onImportFinishedHandler(handler: OnImportFinishedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @SourceSelectionScope
        fun viewModel(
            importUseCase: ImportUseCase,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): SourceSelectionViewModel = DefaultSourceSelectionViewModel(
            importUseCase = importUseCase,
            onImportFinishedHandler = onImportFinishedHandler,
        )

        @Provides
        @SourceSelectionScope
        fun viewProvider(viewModel: SourceSelectionViewModel): ViewProvider = SourceSelectionViewProvider(viewModel = viewModel)
    }
}
