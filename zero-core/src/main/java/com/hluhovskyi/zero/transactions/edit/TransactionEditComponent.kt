package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
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
import com.hluhovskyi.zero.transactions.edit.common.DefaultTransactionEditExpenseIncomeViewModel
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditExpenseIncomeViewModel
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditExpenseIncomeViewProvider
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
        fun expenseIncomeViewModel(
            useCase: TransactionEditUseCase,
        ): TransactionEditExpenseIncomeViewModel = DefaultTransactionEditExpenseIncomeViewModel(
            useCase = useCase,
        )

        @Provides
        @TransactionEditScope
        fun expenseIncomeViewProvider(
            viewModel: TransactionEditExpenseIncomeViewModel,
            imageLoader: ImageLoader,
        ): TransactionEditExpenseIncomeViewProvider = TransactionEditExpenseIncomeViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )

        @Provides
        @TransactionEditScope
        fun expenseIncomeComponent(
            expenseIncomeViewProvider: TransactionEditExpenseIncomeViewProvider,
        ): AttachableViewComponent = object : AttachableViewComponent {
            override val tag: String = "TransactionEditExpenseIncomeComponent"
            override val viewProvider: ViewProvider = expenseIncomeViewProvider
            override fun attach(): Closeable = Closeables.empty()
        }

        @Provides
        @TransactionEditScope
        fun viewProvider(
            viewModel: TransactionEditViewModel,
            expenseIncomeComponent: AttachableViewComponent,
            transferComponentBuilder: TransactionEditTransferComponent.Builder,
            logger: Logger,
        ): ViewProvider = TransactionEditViewProvider(
            viewModel = viewModel,
            expenseIncomeComponent = object : Buildable<AttachableViewComponent> {
                override fun build() = expenseIncomeComponent
            }.logging(logger),
            transferComponent = transferComponentBuilder.logging(logger),
        )

        @Provides
        @TransactionEditScope
        fun transactionEditTransferComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase,
        ): TransactionEditTransferComponent.Builder = TransactionEditTransferComponent.builder(component)
            .transactionEditUseCase(useCase)
    }
}
