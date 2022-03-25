package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.icons.IconRepository
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryScope

@CategoryScope
@dagger.Component(
    dependencies = [CategoryComponent.Dependencies::class],
    modules = [CategoryComponent.Module::class]
)
abstract class CategoryComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryViewModel

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader

        val categoryQueryUseCase: CategoriesQueryUseCase
    }

    companion object {

        fun queryUseCase(
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository
        ): CategoriesQueryUseCase = DefaultCategoriesQueryUseCase(
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository
        )

        fun builder(dependencies: Dependencies): Builder = DaggerCategoryComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryComponent> {

        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryScope
        fun viewModel(
            categoriesQueryUseCase: CategoriesQueryUseCase
        ): CategoryViewModel = DefaultCategoryViewModel(
            categoriesQueryUseCase
        )

        @Provides
        @CategoryScope
        fun viewProvider(
            viewModel: CategoryViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = CategoryViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}