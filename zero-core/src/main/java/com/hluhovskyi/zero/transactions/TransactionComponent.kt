package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.filter.TransactionFilterUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionScope

private const val TAG = "TransactionComponent"

@TransactionScope
@dagger.Component(
    modules = [TransactionComponent.Module::class],
    dependencies = [TransactionComponent.Dependencies::class],
)
abstract class TransactionComponent : AttachableViewComponent {

    internal abstract val viewModel: TransactionViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
        val clock: Clock
        val zoneProvider: ZoneProvider

        val transactionRepository: TransactionRepository
        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionComponent.builder()
            .dependencies(dependencies)
            .onTransactionSelectHandler(OnTransactionSelectedHandler.Noop)
            .transactionFilter(TransactionFilter.All)
            .transactionFilterUseCase(TransactionFilterUseCase.Noop)
            .displayConfig(DisplayConfig())
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onTransactionSelectHandler(handler: OnTransactionSelectedHandler): Builder

        @BindsInstance
        fun transactionFilter(filter: TransactionFilter): Builder

        @BindsInstance
        fun transactionFilterUseCase(useCase: TransactionFilterUseCase): Builder

        @BindsInstance
        fun displayConfig(config: DisplayConfig): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionScope
        fun transactionFilterApplicator(clock: Clock, zoneProvider: ZoneProvider): TransactionFilterApplicator = DefaultTransactionFilterApplicator(clock, zoneProvider)

        @Provides
        @TransactionScope
        fun viewModel(
            transactionRepository: TransactionRepository,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            currencyConvertUseCase: CurrencyConvertUseCase,
            onTransactionSelectedHandler: OnTransactionSelectedHandler,
            filter: TransactionFilter,
            transactionFilterUseCase: TransactionFilterUseCase,
            transactionFilterApplicator: TransactionFilterApplicator,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): TransactionViewModel = DefaultTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
            onTransactionSelectedHandler = onTransactionSelectedHandler,
            filter = filter,
            transactionFilterUseCase = transactionFilterUseCase,
            transactionFilterApplicator = transactionFilterApplicator,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @TransactionScope
        fun viewProvider(
            viewModel: TransactionViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
            dateFormatter: DateFormatter,
            displayConfig: DisplayConfig,
        ): ViewProvider = TransactionViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
            displayConfig = displayConfig,
        )
    }
}
