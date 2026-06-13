package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.OnBackHandler
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
private annotation class CashFlowReportScope

private const val TAG = "CashFlowReportComponent"

/**
 * The cash-flow report screen, opened from the Analytics hub's cash-flow card. Composes the detail
 * use case (reused for totals + monthly buckets, both windows) with its own income-by-category
 * aggregation in [CashFlowReportUseCase]; the shared [SpendingBreakdownUseCase] comes from
 * AnalyticsComponent.
 */
@CashFlowReportScope
@dagger.Component(
    modules = [CashFlowReportComponent.Module::class],
    dependencies = [CashFlowReportComponent.Dependencies::class],
)
abstract class CashFlowReportComponent : AttachableViewComponent {

    internal abstract val viewModel: CashFlowReportViewModel

    override val tag: String = TAG

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val transactionRepository: TransactionRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase

        // Shared aggregation from AnalyticsComponent.
        val spendingBreakdownUseCase: SpendingBreakdownUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val zonedClock: ZonedClock
        val dispatcherProvider: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCashFlowReportComponent.builder()
            .dependencies(dependencies)
            .onBackHandler(OnBackHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CashFlowReportComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CashFlowReportScope
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
        @CashFlowReportScope
        fun cashFlowReportUseCase(
            analyticsDetailUseCase: AnalyticsDetailUseCase,
            transactionRepository: TransactionRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyConvertUseCase: CurrencyConvertUseCase,
        ): CashFlowReportUseCase = DefaultCashFlowReportUseCase(
            analyticsDetailUseCase = analyticsDetailUseCase,
            transactionRepository = transactionRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
        )

        @Provides
        @CashFlowReportScope
        fun viewModel(
            cashFlowReportUseCase: CashFlowReportUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onBackHandler: OnBackHandler,
            zonedClock: ZonedClock,
            dispatcherProvider: DispatcherProvider,
        ): CashFlowReportViewModel = DefaultCashFlowReportViewModel(
            cashFlowReportUseCase = cashFlowReportUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onBackHandler = onBackHandler,
            zonedClock = zonedClock,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @CashFlowReportScope
        fun viewProvider(
            viewModel: CashFlowReportViewModel,
            amountFormatter: AmountFormatter,
            imageLoader: ImageLoader,
        ): ViewProvider = CashFlowReportViewProvider(
            viewModel = viewModel,
            amountFormatter = amountFormatter,
            imageLoader = imageLoader,
        )
    }
}
