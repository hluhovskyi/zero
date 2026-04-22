package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.ImageLoader
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
private annotation class CategoriesReviewScope

private const val TAG = "CategoriesReviewComponent"

@CategoriesReviewScope
@dagger.Component(
    modules = [CategoriesReviewComponent.Module::class],
    dependencies = [CategoriesReviewComponent.Dependencies::class],
)
internal abstract class CategoriesReviewComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCategoriesReviewComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
            .imageLoader(ImageLoader.empty())
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoriesReviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder

        @BindsInstance
        fun imageLoader(imageLoader: ImageLoader): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoriesReviewScope
        fun viewModel(importUseCase: ImportUseCase): CategoriesReviewViewModel = DefaultCategoriesReviewViewModel(importUseCase = importUseCase)

        @Provides
        @CategoriesReviewScope
        fun viewProvider(viewModel: CategoriesReviewViewModel, imageLoader: ImageLoader): ViewProvider = CategoriesReviewViewProvider(viewModel = viewModel, imageLoader = imageLoader)
    }
}
