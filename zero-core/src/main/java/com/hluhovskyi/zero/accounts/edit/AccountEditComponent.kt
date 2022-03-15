package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountEditScope

@AccountEditScope
@dagger.Component(
    modules = [AccountEditComponent.Module::class],
    dependencies = [AccountEditComponent.Dependencies::class]
)
abstract class AccountEditComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountEditViewModel

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountEditComponent.builder()
            .dependencies(dependencies)
            .idGenerator(IdGenerator.UUID)
            .accountRepository(AccountRepository.Noop)
            .currencyRepository(CurrencyRepository.Noop)
            .onAccountSavedHandler(OnAccountSavedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun accountRepository(accountRepository: AccountRepository): Builder

        @BindsInstance
        fun currencyRepository(currencyRepository: CurrencyRepository): Builder

        @BindsInstance
        fun idGenerator(idGenerator: IdGenerator): Builder

        @BindsInstance
        fun onAccountSavedHandler(handler: OnAccountSavedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountEditScope
        fun viewModel(
            idGenerator: IdGenerator,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            onAccountSavedHandler: OnAccountSavedHandler,
        ): AccountEditViewModel = DefaultAccountEditViewModel(
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            onAccountSavedHandler = onAccountSavedHandler,
        )

        @Provides
        @AccountEditScope
        fun viewProvider(
            viewModel: AccountEditViewModel
        ): ViewProvider = AccountEditViewProvider(
            viewModel = viewModel
        )
    }
}