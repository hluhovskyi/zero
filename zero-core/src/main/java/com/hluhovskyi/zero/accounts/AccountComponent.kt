package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountScope

private const val TAG = "AccountComponent"

@AccountScope
@dagger.Component(
    modules = [AccountComponent.Module::class],
    dependencies = [AccountComponent.Dependencies::class],
)
abstract class AccountComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val dispatchers: DispatcherProvider
        val iamgeLoader: ImageLoader
        val amountFormatter: AmountFormatter

        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase

        val accountRepository: AccountRepository
        val transactionRepository: TransactionRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountComponent.builder()
            .dependencies(dependencies)
            .onAddAccountHandler(OnAddAccountHandler.Noop)
            .onAccountSelectedHandler(OnAccountSelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onAddAccountHandler(handler: OnAddAccountHandler): Builder

        @BindsInstance
        fun onAccountSelectedHandler(handler: OnAccountSelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountScope
        fun useCase(
            accountRepository: AccountRepository,
            transactionRepository: TransactionRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
        ): AccountUseCase = DefaultAccountUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
        )

        @Provides
        @AccountScope
        fun viewModel(
            useCase: AccountUseCase,
            dispatcherProvider: DispatcherProvider,
            onAccountSelectedHandler: OnAccountSelectedHandler,
        ): AccountViewModel = DefaultAccountViewModel(
            useCase = useCase,
            dispatchers = dispatcherProvider,
            onAccountSelectedHandler = onAccountSelectedHandler,
        )

        @Provides
        @AccountScope
        fun viewProvider(
            viewModel: AccountViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
            onAddAccountHandler: OnAddAccountHandler,
        ): ViewProvider = AccountViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddAccount = onAddAccountHandler,
        )
    }
}
