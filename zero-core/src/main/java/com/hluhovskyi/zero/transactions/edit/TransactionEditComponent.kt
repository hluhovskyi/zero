package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
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
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter

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
            .duplicateFromTransactionId(Id.Unknown)
            .preSelectedCategoryId(Id.Unknown)
            .preSelectedAccountId(Id.Unknown)
            .onTransactionSavedHandler(OnTransactionSavedHandler.Noop)
            .onEditCategoriesHandler(OnEditCategoriesHandler.Noop)
            .onDiscardHandler(OnDiscardHandler.Noop)
            .onDuplicateHandler(OnDuplicateHandler.Noop)
            .transactionEditCategoryUseCase(TransactionEditCategoryUseCase.Noop)
            .transactionEditCurrencyUseCase(TransactionEditCurrencyUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionId(transactionId: Id): Builder

        @BindsInstance
        fun duplicateFromTransactionId(@DuplicateFromTransactionId id: Id): Builder

        @BindsInstance
        fun preSelectedCategoryId(@PreSelectedCategoryId id: Id): Builder

        @BindsInstance
        fun preSelectedAccountId(@PreSelectedAccountId id: Id): Builder

        @BindsInstance
        fun onTransactionSavedHandler(handler: OnTransactionSavedHandler): Builder

        @BindsInstance
        fun onEditCategoriesHandler(handler: OnEditCategoriesHandler): Builder

        @BindsInstance
        fun onDiscardHandler(handler: OnDiscardHandler): Builder

        @BindsInstance
        fun onDuplicateHandler(handler: OnDuplicateHandler): Builder

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
            @DuplicateFromTransactionId duplicateFromTransactionId: Id,
            @PreSelectedCategoryId preSelectedCategoryId: Id,
            @PreSelectedAccountId preSelectedAccountId: Id,
            accountRepository: AccountRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyRepository: CurrencyRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            transactionRepository: TransactionRepository,
            idGenerator: IdGenerator,
            amountFormatter: AmountFormatter,
            onTransactionSavedHandler: OnTransactionSavedHandler,
            onEditCategoriesHandler: OnEditCategoriesHandler,
            onDiscardHandler: OnDiscardHandler,
            onDuplicateHandler: OnDuplicateHandler,
            transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
            transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
            incorrectStateDetector: IncorrectStateDetector,
            clock: Clock,
            zoneProvider: ZoneProvider,
            logger: Logger,
        ): TransactionEditUseCase = DefaultTransactionEditUseCase(
            transactionId = transactionId,
            duplicateFromTransactionId = duplicateFromTransactionId,
            preSelectedCategoryId = preSelectedCategoryId,
            preSelectedAccountId = preSelectedAccountId,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            transactionRepository = transactionRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            idGenerator = idGenerator,
            amountFormatter = amountFormatter,
            onTransactionSavedHandler = onTransactionSavedHandler,
            onEditCategoriesHandler = onEditCategoriesHandler,
            onDiscardHandler = onDiscardHandler,
            onDuplicateHandler = onDuplicateHandler,
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
            transactionId: Id,
            @DuplicateFromTransactionId duplicateFromTransactionId: Id,
            amountFormatter: AmountFormatter,
            dateFormatter: DateFormatter,
        ): TransactionEditViewModel = DefaultTransactionEditViewModel(
            useCase = useCase,
            isEditMode = transactionId is Id.Known,
            isDuplicateMode = duplicateFromTransactionId is Id.Known,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
        )

        @Provides
        @TransactionEditScope
        fun viewProvider(
            viewModel: TransactionEditViewModel,
            expenseIncomeComponentBuilder: TransactionEditExpenseIncomeComponent.Builder,
            transferComponentBuilder: TransactionEditTransferComponent.Builder,
            transactionId: Id,
            logger: Logger,
        ): ViewProvider = TransactionEditViewProvider(
            viewModel = viewModel,
            expenseIncomeComponent = expenseIncomeComponentBuilder.logging(logger),
            transferComponent = transferComponentBuilder.logging(logger),
            isNewTransaction = transactionId is Id.Unknown,
        )

        @Provides
        fun transactionEditExpenseIncomeComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase,
            transactionId: Id,
        ): TransactionEditExpenseIncomeComponent.Builder = TransactionEditExpenseIncomeComponent.builder(component)
            .transactionEditUseCase(useCase)
            .isNewTransaction(transactionId is Id.Unknown)

        @Provides
        fun transactionEditTransferComponentBuilder(
            component: TransactionEditComponent,
            useCase: TransactionEditUseCase,
            transactionId: Id,
        ): TransactionEditTransferComponent.Builder = TransactionEditTransferComponent.builder(component)
            .transactionEditUseCase(useCase)
            .isNewTransaction(transactionId is Id.Unknown)
    }
}
