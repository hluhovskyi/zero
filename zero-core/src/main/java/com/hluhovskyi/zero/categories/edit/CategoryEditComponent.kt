package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditScope

private const val TAG = "CategoryEditComponent"

@CategoryEditScope
@dagger.Component(
    dependencies = [CategoryEditComponent.Dependencies::class],
    modules = [CategoryEditComponent.Module::class]
)
abstract class CategoryEditComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryEditViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader

        val categoryRepository: CategoryRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerCategoryEditComponent.builder()
            .dependencies(dependencies)
            .categoryEditIconUseCase(CategoryEditIconUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryEditIconUseCase(useCase: CategoryEditIconUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryEditScope
        fun viewModel(
            categoryRepository: CategoryRepository,
            categoryEditIconUseCase: CategoryEditIconUseCase,
        ): CategoryEditViewModel = DefaultCategoryEditViewModel(
            categoryRepository = categoryRepository,
            categoryEditIconUseCase = categoryEditIconUseCase,
        )

        @Provides
        @CategoryEditScope
        fun viewProvider(
            viewModel: CategoryEditViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = CategoriesEditViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}