package com.hluhovskyi.zero.budget.over

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import dagger.BindsInstance
import dagger.Provides
import kotlinx.datetime.LocalDate
import java.io.Closeable
import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetOverCategoryId

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetOverPeriodStart

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetOverPeriodEnd

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetOverScope

private const val TAG = "BudgetOverComponent"

@BudgetOverScope
@dagger.Component(
    modules = [BudgetOverComponent.Module::class],
    dependencies = [BudgetOverComponent.Dependencies::class],
)
abstract class BudgetOverComponent : AttachableViewComponent {

    internal abstract val viewModel: BudgetOverViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val budgetRepository: BudgetRepository
        val budgetQueryUseCase: BudgetQueryUseCase
        val dispatcherProvider: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBudgetOverComponent.builder()
            .dependencies(dependencies)
            .initialMode(null)
            .onReallocateCompletedHandler(OnReallocateCompletedHandler.Noop)
            .onIncreaseCompletedHandler(OnIncreaseCompletedHandler.Noop)
            .onBackHandler(OnBackHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BudgetOverComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryId(@BudgetOverCategoryId id: Id.Known): Builder

        @BindsInstance
        fun periodStart(@BudgetOverPeriodStart start: LocalDate): Builder

        @BindsInstance
        fun periodEnd(@BudgetOverPeriodEnd end: LocalDate): Builder

        @BindsInstance
        fun initialMode(mode: BudgetOverViewModel.Mode?): Builder

        @BindsInstance
        fun onReallocateCompletedHandler(handler: OnReallocateCompletedHandler): Builder

        @BindsInstance
        fun onIncreaseCompletedHandler(handler: OnIncreaseCompletedHandler): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BudgetOverScope
        internal fun viewModel(
            @BudgetOverCategoryId categoryId: Id.Known,
            @BudgetOverPeriodStart periodStart: LocalDate,
            @BudgetOverPeriodEnd periodEnd: LocalDate,
            initialMode: BudgetOverViewModel.Mode?,
            budgetQueryUseCase: BudgetQueryUseCase,
            budgetRepository: BudgetRepository,
            onReallocateCompletedHandler: OnReallocateCompletedHandler,
            onIncreaseCompletedHandler: OnIncreaseCompletedHandler,
            onBackHandler: OnBackHandler,
            dispatcherProvider: DispatcherProvider,
        ): BudgetOverViewModel = DefaultBudgetOverViewModel(
            categoryId = categoryId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            initialMode = initialMode,
            budgetQueryUseCase = budgetQueryUseCase,
            budgetRepository = budgetRepository,
            onReallocateCompletedHandler = onReallocateCompletedHandler,
            onIncreaseCompletedHandler = onIncreaseCompletedHandler,
            onBackHandler = onBackHandler,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @BudgetOverScope
        fun viewProvider(
            viewModel: BudgetOverViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = BudgetOverViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
