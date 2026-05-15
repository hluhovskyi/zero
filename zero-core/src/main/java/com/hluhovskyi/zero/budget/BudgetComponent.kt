package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetScope

private const val TAG = "BudgetComponent"

@BudgetScope
@dagger.Component(
    modules = [BudgetComponent.Module::class],
    dependencies = [BudgetComponent.Dependencies::class],
)
abstract class BudgetComponent : AttachableViewComponent {

    internal abstract val viewModel: BudgetViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dispatcherProvider: DispatcherProvider
        val budgetQueryUseCase: BudgetQueryUseCase
        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {

        fun queryUseCase(
            categoriesQueryUseCase: CategoriesQueryUseCase,
            budgetRepository: BudgetRepository,
            categorySpendingUseCase: CategorySpendingUseCase,
        ): BudgetQueryUseCase = DefaultBudgetQueryUseCase(
            categoriesQueryUseCase = categoriesQueryUseCase,
            budgetRepository = budgetRepository,
            categorySpendingUseCase = categorySpendingUseCase,
        )

        fun builder(dependencies: Dependencies): Builder = DaggerBudgetComponent.builder()
            .dependencies(dependencies)
            .onCategoryTappedHandler(OnCategoryTappedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BudgetComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onCategoryTappedHandler(handler: OnCategoryTappedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BudgetScope
        internal fun periodResolver(
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): PeriodResolver = DefaultPeriodResolver(clock = clock, zoneProvider = zoneProvider)

        @Provides
        @BudgetScope
        internal fun viewModel(
            budgetQueryUseCase: BudgetQueryUseCase,
            periodResolver: PeriodResolver,
            onCategoryTappedHandler: OnCategoryTappedHandler,
            dispatcherProvider: DispatcherProvider,
        ): BudgetViewModel = DefaultBudgetViewModel(
            budgetQueryUseCase = budgetQueryUseCase,
            periodResolver = periodResolver,
            onCategoryTappedHandler = onCategoryTappedHandler,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @BudgetScope
        fun viewProvider(
            viewModel: BudgetViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = BudgetViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
