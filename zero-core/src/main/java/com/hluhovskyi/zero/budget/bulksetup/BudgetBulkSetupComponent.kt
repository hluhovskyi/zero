package com.hluhovskyi.zero.budget.bulksetup

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BulkBudgetSaveUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
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
private annotation class BulkSetupPeriodStart

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class BulkSetupPeriodEnd

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BudgetBulkSetupScope

private const val TAG = "BudgetBulkSetupComponent"

@BudgetBulkSetupScope
@dagger.Component(
    modules = [BudgetBulkSetupComponent.Module::class],
    dependencies = [BudgetBulkSetupComponent.Dependencies::class],
)
abstract class BudgetBulkSetupComponent : AttachableViewComponent {

    internal abstract val viewModel: BudgetBulkSetupViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dispatcherProvider: DispatcherProvider
        val budgetQueryUseCase: BudgetQueryUseCase
        val bulkBudgetSaveUseCase: BulkBudgetSaveUseCase
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBudgetBulkSetupComponent.builder()
            .dependencies(dependencies)
            .onBulkSavedHandler(OnBulkBudgetSavedHandler.Noop)
            .onBackHandler(OnBackHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BudgetBulkSetupComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun periodStart(@BulkSetupPeriodStart periodStart: LocalDate): Builder

        @BindsInstance
        fun periodEnd(@BulkSetupPeriodEnd periodEnd: LocalDate): Builder

        @BindsInstance
        fun onBulkSavedHandler(handler: OnBulkBudgetSavedHandler): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BudgetBulkSetupScope
        internal fun viewModel(
            @BulkSetupPeriodStart periodStart: LocalDate,
            @BulkSetupPeriodEnd periodEnd: LocalDate,
            budgetQueryUseCase: BudgetQueryUseCase,
            bulkBudgetSaveUseCase: BulkBudgetSaveUseCase,
            onBulkSavedHandler: OnBulkBudgetSavedHandler,
            onBackHandler: OnBackHandler,
            dispatcherProvider: DispatcherProvider,
        ): BudgetBulkSetupViewModel = DefaultBudgetBulkSetupViewModel(
            periodStart = periodStart,
            periodEnd = periodEnd,
            budgetQueryUseCase = budgetQueryUseCase,
            bulkBudgetSaveUseCase = bulkBudgetSaveUseCase,
            onBulkSavedHandler = onBulkSavedHandler,
            onBackHandler = onBackHandler,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @BudgetBulkSetupScope
        fun viewProvider(
            viewModel: BudgetBulkSetupViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = BudgetBulkSetupViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
