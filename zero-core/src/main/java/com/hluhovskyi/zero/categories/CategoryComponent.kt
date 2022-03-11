package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
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

    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCategoryComponent.builder()
            .dependencies(dependencies)
            .imageLoader(ImageLoader.empty())
            .categoryRepository(CategoryRepository.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun imageLoader(imageLoader: ImageLoader): Builder

        @BindsInstance
        fun categoryRepository(categoryRepository: CategoryRepository): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryScope
        fun viewModel(
            categoryRepository: CategoryRepository
        ): CategoryViewModel = DefaultCategoryViewModel(
            categoryRepository = categoryRepository
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