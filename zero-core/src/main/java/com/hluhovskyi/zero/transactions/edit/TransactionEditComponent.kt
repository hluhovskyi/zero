package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.edit.expense.TransactionEditExpenseComponent
import com.hluhovskyi.zero.transactions.edit.income.TransactionEditIncomeComponent
import com.hluhovskyi.zero.transactions.edit.transfer.TransactionEditTransferComponent
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionEditScope

private const val TAG = "TransactionEditComponent"

@TransactionEditScope
@dagger.Component(
    modules = [TransactionEditComponent.Module::class],
    dependencies = [TransactionEditComponent.Dependencies::class]
)
abstract class TransactionEditComponent : AttachableViewComponent,
    TransactionEditExpenseComponent.Dependencies,
    TransactionEditIncomeComponent.Dependencies,
    TransactionEditTransferComponent.Dependencies {

    internal abstract val useCase: TransactionEditUseCase

    override val tag: String = TAG
    override fun attach(): Closeable = useCase.attach()

    interface Dependencies {
        val idGenerator: IdGenerator
        val clock: Clock
        val logger: Logger
        val imageLoader: ImageLoader

        val categoriesQueryUseCase: CategoriesQueryUseCase

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val transactionRepository: TransactionRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditComponent.builder()
            .dependencies(dependencies)
            .onTransactionSavedHandler(OnTransactionSavedHandler.Noop)
            .onEditCategoriesHandler(OnEditCategoriesHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onTransactionSavedHandler(handler: OnTransactionSavedHandler): Builder

        @BindsInstance
        fun onEditCategoriesHandler(handler: OnEditCategoriesHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionEditScope
        fun useCase(
            accountRepository: AccountRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyRepository: CurrencyRepository,
            transactionRepository: TransactionRepository,
            idGenerator: IdGenerator,
            onTransactionSavedHandler: OnTransactionSavedHandler,
            onEditCategoriesHandler: OnEditCategoriesHandler,
            clock: Clock,
            logger: Logger,
        ): TransactionEditUseCase = DefaultTransactionEditUseCase(
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            transactionRepository = transactionRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            idGenerator = idGenerator,
            onTransactionSavedHandler = onTransactionSavedHandler,
            onEditCategoriesHandler = onEditCategoriesHandler,
            clock = clock,
            logger = logger
        )

        @Provides
        @TransactionEditScope
        fun viewModel(
            useCase: TransactionEditUseCase
        ): TransactionEditViewModel = DefaultTransactionEditViewModel(
            useCase = useCase
        )

        @Provides
        @TransactionEditScope
        fun viewProvider(
            viewModel: TransactionEditViewModel,
            expenseComponentBuilder: TransactionEditExpenseComponent.Builder,
            incomeComponentBuilder: TransactionEditIncomeComponent.Builder,
            transferComponentBuilder: TransactionEditTransferComponent.Builder,
            logger: Logger
        ): ViewProvider = TransactionEditViewProvider(
            viewModel = viewModel,
            expenseComponent = expenseComponentBuilder.logging(logger),
            incomeComponent = incomeComponentBuilder.logging(logger),
            transferComponent = transferComponentBuilder.logging(logger),
        )

        @Provides
        @TransactionEditScope
        fun transactionEditExpenseComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase,
        ): TransactionEditExpenseComponent.Builder =
            TransactionEditExpenseComponent.builder(component)
                .transactionEditUseCase(useCase)

        @Provides
        @TransactionEditScope
        fun transactionEditTransferComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase
        ): TransactionEditTransferComponent.Builder =
            TransactionEditTransferComponent.builder(component)
                .transactionEditUseCase(useCase)

        @Provides
        @TransactionEditScope
        fun transactionEditIncomeComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase
        ): TransactionEditIncomeComponent.Builder =
            TransactionEditIncomeComponent.builder(component)
                .transactionEditUseCase(useCase)
    }
}
