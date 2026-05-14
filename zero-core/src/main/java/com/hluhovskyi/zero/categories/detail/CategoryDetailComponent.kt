package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.categories.DefaultCategorySpendingUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.DisplayConfig
import com.hluhovskyi.zero.transactions.OnTransactionSelectedHandler
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryDetailScope

private const val TAG = "CategoryDetailComponent"

@CategoryDetailScope
@dagger.Component(
    modules = [CategoryDetailComponent.Module::class],
    dependencies = [CategoryDetailComponent.Dependencies::class],
)
abstract class CategoryDetailComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryDetailViewModel

    override val tag: String = TAG

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val transactionRepository: TransactionRepository
        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCategoryDetailComponent.builder()
            .dependencies(dependencies)
            .onEditHandler(OnCategoryDetailEditHandler.Noop)
            .onBackHandler(OnBackHandler.Noop)
            .onTransactionSelectedHandler(OnTransactionSelectedHandler.Noop)
            .onCreateTransactionHandler(OnCategoryDetailCreateTransactionHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryDetailComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryId(id: Id.Known): Builder

        @BindsInstance
        fun transactionComponentBuilder(builder: TransactionComponent.Builder): Builder

        @BindsInstance
        fun onEditHandler(handler: OnCategoryDetailEditHandler): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder

        @BindsInstance
        fun onTransactionSelectedHandler(handler: OnTransactionSelectedHandler): Builder

        @BindsInstance
        fun onCreateTransactionHandler(handler: OnCategoryDetailCreateTransactionHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryDetailScope
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
        @CategoryDetailScope
        fun viewModel(
            categoryId: Id.Known,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            categorySpendingUseCase: CategorySpendingUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onEditHandler: OnCategoryDetailEditHandler,
            onBackHandler: OnBackHandler,
            onCreateTransactionHandler: OnCategoryDetailCreateTransactionHandler,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): CategoryDetailViewModel = DefaultCategoryDetailViewModel(
            categoryId = categoryId,
            categoriesQueryUseCase = categoriesQueryUseCase,
            categorySpendingUseCase = categorySpendingUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onEditHandler = onEditHandler,
            onBackHandler = onBackHandler,
            onCreateTransactionHandler = onCreateTransactionHandler,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @CategoryDetailScope
        fun transactionComponent(
            builder: TransactionComponent.Builder,
            categoryId: Id.Known,
            onTransactionSelectedHandler: OnTransactionSelectedHandler,
        ): TransactionComponent = builder
            .transactionFilter(TransactionFilter.forCategory(categoryId))
            .displayConfig(DisplayConfig(showSearchBar = false, showFilterButton = false))
            .onTransactionSelectHandler(onTransactionSelectedHandler)
            .build()

        @Provides
        @CategoryDetailScope
        fun viewProvider(
            viewModel: CategoryDetailViewModel,
            transactionComponent: TransactionComponent,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = CategoryDetailViewProvider(
            viewModel = viewModel,
            transactionComponent = transactionComponent,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
