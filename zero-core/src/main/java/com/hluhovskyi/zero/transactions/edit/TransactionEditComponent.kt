package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditExpenseIncomeComponent
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
    dependencies = [TransactionEditComponent.Dependencies::class],
)
abstract class TransactionEditComponent :
    AttachableViewComponent,
    TransactionEditExpenseIncomeComponent.Dependencies,
    TransactionEditTransferComponent.Dependencies {

    internal abstract val useCase: TransactionEditUseCase

    override val tag: String = TAG
    override fun attach(): Closeable = useCase.attach()

    interface Dependencies {
        val idGenerator: IdGenerator
        val clock: Clock
        val zoneProvider: ZoneProvider
        val logger: Logger
        val incorrectStateDetector: IncorrectStateDetector
        val imageLoader: ImageLoader

        val categoriesQueryUseCase: CategoriesQueryUseCase

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val currencyConvertUseCase: CurrencyConvertUseCase
        val transactionRepository: TransactionRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditComponent.builder()
            .dependencies(dependencies)
            .transactionId(Id.Unknown)
            .onTransactionSavedHandler(OnTransactionSavedHandler.Noop)
            .onEditCategoriesHandler(OnEditCategoriesHandler.Noop)
            .onDiscardHandler(OnDiscardHandler.Noop)
            .transactionEditCategoryUseCase(TransactionEditCategoryUseCase.Noop)
            .transactionEditCurrencyUseCase(TransactionEditCurrencyUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionId(transactionId: Id): Builder

        @BindsInstance
        fun onTransactionSavedHandler(handler: OnTransactionSavedHandler): Builder

        @BindsInstance
        fun onEditCategoriesHandler(handler: OnEditCategoriesHandler): Builder

        @BindsInstance
        fun onDiscardHandler(handler: OnDiscardHandler): Builder

        @BindsInstance
        fun transactionEditCategoryUseCase(useCase: TransactionEditCategoryUseCase): Builder

        @BindsInstance
        fun transactionEditCurrencyUseCase(useCase: TransactionEditCurrencyUseCase): Builder
    }

    @dagger.Module
    internal object Module {

        @Provides
        @TransactionEditScope
        fun useCase(
            transactionId: Id,
            accountRepository: AccountRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyRepository: CurrencyRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            transactionRepository: TransactionRepository,
            idGenerator: IdGenerator,
            onTransactionSavedHandler: OnTransactionSavedHandler,
            onEditCategoriesHandler: OnEditCategoriesHandler,
            onDiscardHandler: OnDiscardHandler,
            transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
            transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
            incorrectStateDetector: IncorrectStateDetector,
            clock: Clock,
            zoneProvider: ZoneProvider,
            logger: Logger,
        ): TransactionEditUseCase = DefaultTransactionEditUseCase(
            transactionId = transactionId,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            transactionRepository = transactionRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            idGenerator = idGenerator,
            onTransactionSavedHandler = onTransactionSavedHandler,
            onEditCategoriesHandler = onEditCategoriesHandler,
            onDiscardHandler = onDiscardHandler,
            transactionEditCategoryUseCase = transactionEditCategoryUseCase,
            transactionEditCurrencyUseCase = transactionEditCurrencyUseCase,
            incorrectStateDetector = incorrectStateDetector,
            clock = clock,
            zoneProvider = zoneProvider,
            logger = logger,
        )

        @Provides
        @TransactionEditScope
        fun viewModel(
            useCase: TransactionEditUseCase,
        ): TransactionEditViewModel = DefaultTransactionEditViewModel(
            useCase = useCase,
        )

        @Provides
        @TransactionEditScope
        fun viewProvider(
            viewModel: TransactionEditViewModel,
            expenseIncomeComponentBuilder: TransactionEditExpenseIncomeComponent.Builder,
            transferComponentBuilder: TransactionEditTransferComponent.Builder,
            logger: Logger,
        ): ViewProvider = TransactionEditViewProvider(
            viewModel = viewModel,
            expenseIncomeComponent = expenseIncomeComponentBuilder.logging(logger),
            transferComponent = transferComponentBuilder.logging(logger),
        )

        @Provides
        @TransactionEditScope
        fun transactionEditExpenseIncomeComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase,
        ): TransactionEditExpenseIncomeComponent.Builder = TransactionEditExpenseIncomeComponent.builder(component)
            .transactionEditUseCase(useCase)

        @Provides
        @TransactionEditScope
        fun transactionEditTransferComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase,
        ): TransactionEditTransferComponent.Builder = TransactionEditTransferComponent.builder(component)
            .transactionEditUseCase(useCase)
    }
}
