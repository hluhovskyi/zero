package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.breakdown.DefaultSpendingBreakdownUseCase
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AnalyticsScope

/**
 * Use-case graph for the analytics feature — wires the reusable [SpendingBreakdownUseCase] from the
 * data layer, with no view of its own. Any feature can depend on it to reuse the same spend
 * aggregation; the hub screen and its detail use case live in [AnalyticsDetailComponent].
 */
@AnalyticsScope
@dagger.Component(
    modules = [AnalyticsComponent.Module::class],
    dependencies = [AnalyticsComponent.Dependencies::class],
)
interface AnalyticsComponent {

    val spendingBreakdownUseCase: SpendingBreakdownUseCase

    interface Dependencies {
        val transactionRepository: TransactionRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase
    }

    companion object {
        fun create(dependencies: Dependencies): AnalyticsComponent = DaggerAnalyticsComponent.builder()
            .dependencies(dependencies)
            .build()
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
    }
}
