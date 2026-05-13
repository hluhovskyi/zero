package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.icons.IconRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditId

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditInitialType

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditScope

private const val TAG = "CategoryEditComponent"

@CategoryEditScope
@dagger.Component(
    dependencies = [CategoryEditComponent.Dependencies::class],
    modules = [CategoryEditComponent.Module::class],
)
abstract class CategoryEditComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryEditViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoryRepository: CategoryRepository
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
        val configurationRepository: ConfigurationRepository
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCategoryEditComponent.builder()
            .dependencies(dependencies)
            .initialType(CategoryType.EXPENSE)
            .categoryEditIconUseCase(CategoryEditIconUseCase.Noop)
            .categoryEditColorUseCase(CategoryEditColorUseCase.Noop)
            .onCategorySavedHandler(OnCategorySavedHandler.Noop)
            .onDiscardHandler(OnDiscardHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryId(@CategoryEditId categoryId: Id): Builder

        @BindsInstance
        fun initialType(@CategoryEditInitialType type: CategoryType): Builder

        @BindsInstance
        fun categoryEditIconUseCase(useCase: CategoryEditIconUseCase): Builder

        @BindsInstance
        fun categoryEditColorUseCase(useCase: CategoryEditColorUseCase): Builder

        @BindsInstance
        fun onCategorySavedHandler(handler: OnCategorySavedHandler): Builder

        @BindsInstance
        fun onDiscardHandler(handler: OnDiscardHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryEditScope
        fun viewModel(
            @CategoryEditId categoryId: Id,
            @CategoryEditInitialType initialType: CategoryType,
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            onCategorySavedHandler: OnCategorySavedHandler,
            configurationRepository: ConfigurationRepository,
        ): CategoryEditViewModel = DefaultCategoryEditViewModel(
            categoryId = categoryId,
            initialType = initialType,
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            categoryEditIconUseCase = categoryEditIconUseCase,
            categoryEditColorUseCase = categoryEditColorUseCase,
            onCategorySavedHandler = onCategorySavedHandler,
            configurationRepository = configurationRepository,
        )

        @Provides
        @CategoryEditScope
        fun viewProvider(
            viewModel: CategoryEditViewModel,
            onDiscardHandler: OnDiscardHandler,
            imageLoader: ImageLoader,
        ): ViewProvider = CategoriesEditViewProvider(
            viewModel = viewModel,
            onDiscard = onDiscardHandler,
            imageLoader = imageLoader,
        )
    }
}
