package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.breakdown.DefaultSpendingBreakdownUseCase
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AnalyticsScope

private const val TAG = "AnalyticsComponent"

@AnalyticsScope
@dagger.Component(
    dependencies = [AnalyticsComponent.Dependencies::class],
    modules = [AnalyticsComponent.Module::class],
)
abstract class AnalyticsComponent : AttachableViewComponent {

    internal abstract val viewModel: AnalyticsViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val transactionRepository: TransactionRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val amountFormatter: AmountFormatter
        val imageLoader: ImageLoader
        val zonedClock: ZonedClock
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerAnalyticsComponent.builder()
            .dependencies(dependencies)
            .onSeeAllCategoriesHandler(OnSeeAllCategoriesHandler.Noop)
            .onAnalyticsCategorySelectedHandler(OnAnalyticsCategorySelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AnalyticsComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onSeeAllCategoriesHandler(handler: OnSeeAllCategoriesHandler): Builder

        @BindsInstance
        fun onAnalyticsCategorySelectedHandler(handler: OnAnalyticsCategorySelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AnalyticsScope
        fun spendingBreakdownUseCase(
            transactionRepository: TransactionRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyConvertUseCase: CurrencyConvertUseCase,
        ): SpendingBreakdownUseCase = DefaultSpendingBreakdownUseCase(
            transactionRepository = transactionRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
        )

        @Provides
        @AnalyticsScope
        fun analyticsUseCase(
            transactionRepository: TransactionRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            spendingBreakdownUseCase: SpendingBreakdownUseCase,
        ): AnalyticsUseCase = DefaultAnalyticsUseCase(
            transactionRepository = transactionRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            spendingBreakdownUseCase = spendingBreakdownUseCase,
        )

        @Provides
        @AnalyticsScope
        fun viewModel(
            analyticsUseCase: AnalyticsUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onSeeAllCategoriesHandler: OnSeeAllCategoriesHandler,
            onAnalyticsCategorySelectedHandler: OnAnalyticsCategorySelectedHandler,
            zonedClock: ZonedClock,
        ): AnalyticsViewModel = DefaultAnalyticsViewModel(
            analyticsUseCase = analyticsUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onSeeAllCategoriesHandler = onSeeAllCategoriesHandler,
            onAnalyticsCategorySelectedHandler = onAnalyticsCategorySelectedHandler,
            zonedClock = zonedClock,
        )

        @Provides
        @AnalyticsScope
        fun viewProvider(
            viewModel: AnalyticsViewModel,
            amountFormatter: AmountFormatter,
            imageLoader: ImageLoader,
        ): ViewProvider = AnalyticsViewProvider(
            viewModel = viewModel,
            amountFormatter = amountFormatter,
            imageLoader = imageLoader,
        )
    }
}
