package com.hluhovskyi.zero.analytics.breakdown

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class SpendingBreakdownScope

private const val TAG = "SpendingBreakdownComponent"

@SpendingBreakdownScope
@dagger.Component(
    modules = [SpendingBreakdownComponent.Module::class],
    dependencies = [SpendingBreakdownComponent.Dependencies::class],
)
abstract class SpendingBreakdownComponent : AttachableViewComponent {

    internal abstract val viewModel: SpendingBreakdownViewModel

    override val tag: String = TAG

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter

        // Shared aggregation from AnalyticsComponent.
        val spendingBreakdownUseCase: SpendingBreakdownUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val zonedClock: ZonedClock
        val dispatcherProvider: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerSpendingBreakdownComponent.builder()
            .dependencies(dependencies)
            .filter(TransactionFilter.All)
            .onBackHandler(OnBackHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<SpendingBreakdownComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun filter(filter: TransactionFilter): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @SpendingBreakdownScope
        fun viewModel(
            filter: TransactionFilter,
            spendingBreakdownUseCase: SpendingBreakdownUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onBackHandler: OnBackHandler,
            zonedClock: ZonedClock,
            dispatcherProvider: DispatcherProvider,
        ): SpendingBreakdownViewModel = DefaultSpendingBreakdownViewModel(
            filter = filter,
            spendingBreakdownUseCase = spendingBreakdownUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onBackHandler = onBackHandler,
            zonedClock = zonedClock,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @SpendingBreakdownScope
        fun viewProvider(
            viewModel: SpendingBreakdownViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
            dateFormatter: DateFormatter,
        ): ViewProvider = SpendingBreakdownViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
        )
    }
}
