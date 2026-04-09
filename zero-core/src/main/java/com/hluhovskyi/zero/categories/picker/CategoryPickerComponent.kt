package com.hluhovskyi.zero.categories.picker

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.OnCategorySelectedHandler
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryPickerScope

private const val TAG = "CategoryPickerComponent"

@CategoryPickerScope
@dagger.Component(
    dependencies = [CategoryPickerComponent.Dependencies::class],
    modules = [CategoryPickerComponent.Module::class],
)
abstract class CategoryPickerComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryPickerViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoriesQueryUseCase: CategoriesQueryUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerCategoryPickerComponent.builder()
            .dependencies(dependencies)
            .onCategorySelectedHandler(OnCategorySelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onCategorySelectedHandler(handler: OnCategorySelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryPickerScope
        fun viewModel(
            categoriesQueryUseCase: CategoriesQueryUseCase,
            onCategorySelectedHandler: OnCategorySelectedHandler,
        ): CategoryPickerViewModel = DefaultCategoryPickerViewModel(
            categoriesQueryUseCase = categoriesQueryUseCase,
            onCategorySelectedHandler = onCategorySelectedHandler,
        )

        @Provides
        @CategoryPickerScope
        fun viewProvider(
            viewModel: CategoryPickerViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = CategoryPickerViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
