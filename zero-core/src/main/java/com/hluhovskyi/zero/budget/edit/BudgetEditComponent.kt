package com.hluhovskyi.zero.budget.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
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
private annotation class BudgetEditCategoryId

data class BudgetEditPeriod(val from: LocalDate, val to: LocalDate)

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetEditScope

private const val TAG = "BudgetEditComponent"

@BudgetEditScope
@dagger.Component(
    modules = [BudgetEditComponent.Module::class],
    dependencies = [BudgetEditComponent.Dependencies::class],
)
abstract class BudgetEditComponent : AttachableViewComponent {

    internal abstract val viewModel: BudgetEditViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val budgetRepository: BudgetRepository
        val dispatcherProvider: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBudgetEditComponent.builder()
            .dependencies(dependencies)
            .onBudgetSavedHandler(OnBudgetSavedHandler.Noop)
            .onBackHandler(OnBackHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BudgetEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryId(@BudgetEditCategoryId id: Id.Known): Builder

        @BindsInstance
        fun period(period: BudgetEditPeriod): Builder

        @BindsInstance
        fun onBudgetSavedHandler(handler: OnBudgetSavedHandler): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BudgetEditScope
        internal fun viewModel(
            @BudgetEditCategoryId categoryId: Id.Known,
            period: BudgetEditPeriod,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            budgetRepository: BudgetRepository,
            onBudgetSavedHandler: OnBudgetSavedHandler,
            onBackHandler: OnBackHandler,
            dispatcherProvider: DispatcherProvider,
        ): BudgetEditViewModel = DefaultBudgetEditViewModel(
            categoryId = categoryId,
            periodStart = period.from,
            periodEnd = period.to,
            categoriesQueryUseCase = categoriesQueryUseCase,
            budgetRepository = budgetRepository,
            onBudgetSavedHandler = onBudgetSavedHandler,
            onBackHandler = onBackHandler,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @BudgetEditScope
        fun viewProvider(
            viewModel: BudgetEditViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = BudgetEditViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
