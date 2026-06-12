package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AnalyticsDetailScope

private const val TAG = "AnalyticsDetailComponent"

/**
 * The Analytics hub screen. Owns the detail-specific [AnalyticsDetailUseCase] (cash flow composed with
 * the shared [SpendingBreakdownUseCase]) and drives the ViewModel + view from it; navigation out is
 * delegated to the injected handlers.
 */
@AnalyticsDetailScope
@dagger.Component(
    dependencies = [AnalyticsDetailComponent.Dependencies::class],
    modules = [AnalyticsDetailComponent.Module::class],
)
abstract class AnalyticsDetailComponent : AttachableViewComponent {

    internal abstract val viewModel: AnalyticsViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val spendingBreakdownUseCase: SpendingBreakdownUseCase
        val transactionRepository: TransactionRepository
        val currencyConvertUseCase: CurrencyConvertUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val amountFormatter: AmountFormatter
        val imageLoader: ImageLoader
        val zonedClock: ZonedClock
        val dispatcherProvider: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerAnalyticsDetailComponent.builder()
            .dependencies(dependencies)
            .onSeeAllCategoriesHandler(OnSeeAllCategoriesHandler.Noop)
            .onCashFlowTrendsSelectedHandler(OnCashFlowTrendsSelectedHandler.Noop)
            .onAnalyticsCategorySelectedHandler(OnAnalyticsCategorySelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AnalyticsDetailComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onSeeAllCategoriesHandler(handler: OnSeeAllCategoriesHandler): Builder

        @BindsInstance
        fun onCashFlowTrendsSelectedHandler(handler: OnCashFlowTrendsSelectedHandler): Builder

        @BindsInstance
        fun onAnalyticsCategorySelectedHandler(handler: OnAnalyticsCategorySelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AnalyticsDetailScope
        fun analyticsDetailUseCase(
            transactionRepository: TransactionRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            spendingBreakdownUseCase: SpendingBreakdownUseCase,
        ): AnalyticsDetailUseCase = DefaultAnalyticsDetailUseCase(
            transactionRepository = transactionRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            spendingBreakdownUseCase = spendingBreakdownUseCase,
        )

        @Provides
        @AnalyticsDetailScope
        fun viewModel(
            analyticsDetailUseCase: AnalyticsDetailUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onSeeAllCategoriesHandler: OnSeeAllCategoriesHandler,
            onCashFlowTrendsSelectedHandler: OnCashFlowTrendsSelectedHandler,
            onAnalyticsCategorySelectedHandler: OnAnalyticsCategorySelectedHandler,
            zonedClock: ZonedClock,
            dispatcherProvider: DispatcherProvider,
        ): AnalyticsViewModel = DefaultAnalyticsViewModel(
            analyticsDetailUseCase = analyticsDetailUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onSeeAllCategoriesHandler = onSeeAllCategoriesHandler,
            onCashFlowTrendsSelectedHandler = onCashFlowTrendsSelectedHandler,
            onAnalyticsCategorySelectedHandler = onAnalyticsCategorySelectedHandler,
            zonedClock = zonedClock,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @AnalyticsDetailScope
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
