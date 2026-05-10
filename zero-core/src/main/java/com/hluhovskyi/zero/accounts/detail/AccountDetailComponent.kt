package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.AccountUseCase
import com.hluhovskyi.zero.accounts.DefaultAccountUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.DisplayConfig
import com.hluhovskyi.zero.transactions.OnTransactionSelectedHandler
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountDetailScope

private const val TAG = "AccountDetailComponent"

@AccountDetailScope
@dagger.Component(
    modules = [AccountDetailComponent.Module::class],
    dependencies = [AccountDetailComponent.Dependencies::class],
)
abstract class AccountDetailComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountDetailViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val transactionComponentBuilder: TransactionComponent.Builder

        val accountRepository: AccountRepository
        val transactionRepository: TransactionRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase

        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerAccountDetailComponent.builder()
            .dependencies(dependencies)
            .onBackHandler(OnBackHandler.Noop)
            .onTransactionSelectedHandler(OnTransactionSelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountDetailComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun accountId(id: Id.Known): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder

        @BindsInstance
        fun onTransactionSelectedHandler(handler: OnTransactionSelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountDetailScope
        fun accountUseCase(
            accountRepository: AccountRepository,
            transactionRepository: TransactionRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            currencyConvertUseCase: CurrencyConvertUseCase,
        ): AccountUseCase = DefaultAccountUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
        )

        @Provides
        @AccountDetailScope
        fun accountDetailSpendingUseCase(
            transactionRepository: TransactionRepository,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): AccountDetailSpendingUseCase = DefaultAccountDetailSpendingUseCase(
            transactionRepository = transactionRepository,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @AccountDetailScope
        fun transactionComponent(
            builder: TransactionComponent.Builder,
            accountId: Id.Known,
            onTransactionSelectedHandler: OnTransactionSelectedHandler,
        ): TransactionComponent = builder
            .transactionFilter(TransactionFilter.forAccount(accountId))
            .displayConfig(DisplayConfig(showSearchBar = false, showFilterButton = false))
            .onTransactionSelectHandler(onTransactionSelectedHandler)
            .build()

        @Provides
        @AccountDetailScope
        fun viewModel(
            accountId: Id.Known,
            accountUseCase: AccountUseCase,
            accountDetailSpendingUseCase: AccountDetailSpendingUseCase,
            onBackHandler: OnBackHandler,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): AccountDetailViewModel = DefaultAccountDetailViewModel(
            accountId = accountId,
            accountUseCase = accountUseCase,
            accountDetailSpendingUseCase = accountDetailSpendingUseCase,
            onBackHandler = onBackHandler,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @AccountDetailScope
        fun viewProvider(
            viewModel: AccountDetailViewModel,
            transactionComponent: TransactionComponent,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = AccountDetailViewProvider(
            viewModel = viewModel,
            transactionComponent = transactionComponent,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
