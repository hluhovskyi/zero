package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountEditScope

private const val TAG = "AccountEditComponent"

@AccountEditScope
@dagger.Component(
    modules = [AccountEditComponent.Module::class],
    dependencies = [AccountEditComponent.Dependencies::class],
)
abstract class AccountEditComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountEditViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val idGenerator: IdGenerator

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountEditComponent.builder()
            .dependencies(dependencies)
            .onAccountSavedHandler(OnAccountSavedHandler.Noop)
            .onCloseHandler(OnCloseHandler.Noop)
            .accountEditIconUseCase(AccountEditIconUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onAccountSavedHandler(handler: OnAccountSavedHandler): Builder

        @BindsInstance
        fun onCloseHandler(handler: OnCloseHandler): Builder

        @BindsInstance
        fun accountEditIconUseCase(useCase: AccountEditIconUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountEditScope
        fun viewModel(
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            accountEditIconUseCase: AccountEditIconUseCase,
            onAccountSavedHandler: OnAccountSavedHandler,
        ): AccountEditViewModel = DefaultAccountEditViewModel(
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            accountEditIconUseCase = accountEditIconUseCase,
            onAccountSavedHandler = onAccountSavedHandler,
        )

        @Provides
        @AccountEditScope
        fun viewProvider(
            viewModel: AccountEditViewModel,
            onCloseHandler: OnCloseHandler,
        ): ViewProvider = AccountEditViewProvider(
            viewModel = viewModel,
            onClose = onCloseHandler,
        )
    }
}
