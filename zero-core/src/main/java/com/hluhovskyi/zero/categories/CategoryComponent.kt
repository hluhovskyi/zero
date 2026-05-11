package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryScope

private const val TAG = "CategoryComponent"

@CategoryScope
@dagger.Component(
    dependencies = [CategoryComponent.Dependencies::class],
    modules = [CategoryComponent.Module::class],
)
abstract class CategoryComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoryQueryUseCase: CategoriesQueryUseCase
        val amountFormatter: AmountFormatter
        val transactionRepository: TransactionRepository
        val currencyConvertUseCase: CurrencyConvertUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {

        fun queryUseCase(
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            transactionRepository: TransactionRepository,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): CategoriesQueryUseCase = DefaultCategoriesQueryUseCase(
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            transactionRepository = transactionRepository,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        fun builder(dependencies: Dependencies): Builder = DaggerCategoryComponent.builder()
            .dependencies(dependencies)
            .onCategorySelectedHandler(OnCategorySelectedHandler.Noop)
            .onAddCategoryHandler(OnAddCategoryHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onCategorySelectedHandler(handler: OnCategorySelectedHandler): Builder

        @BindsInstance
        fun onAddCategoryHandler(handler: OnAddCategoryHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryScope
        fun categorySpendingUseCase(
            transactionRepository: TransactionRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): CategorySpendingUseCase = DefaultCategorySpendingUseCase(
            transactionRepository = transactionRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @CategoryScope
        fun viewModel(
            categoriesQueryUseCase: CategoriesQueryUseCase,
            categorySpendingUseCase: CategorySpendingUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onCategorySelectedHandler: OnCategorySelectedHandler,
        ): CategoryViewModel = DefaultCategoryViewModel(
            categoriesQueryUseCase = categoriesQueryUseCase,
            categorySpendingUseCase = categorySpendingUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onCategorySelectedHandler = onCategorySelectedHandler,
        )

        @Provides
        @CategoryScope
        fun viewProvider(
            viewModel: CategoryViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
            onAddCategoryHandler: OnAddCategoryHandler,
        ): ViewProvider = CategoryViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddCategory = onAddCategoryHandler,
        )
    }
}
