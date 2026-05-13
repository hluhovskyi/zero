package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
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
        val imageLoader: ImageLoader

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val configurationRepository: ConfigurationRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountEditComponent.builder()
            .dependencies(dependencies)
            .onAccountSavedHandler(OnAccountSavedHandler.Noop)
            .onCloseHandler(OnCloseHandler.Noop)
            .accountEditIconUseCase(AccountEditIconUseCase.Noop)
            .accountEditCurrencyUseCase(AccountEditCurrencyUseCase.Noop)
            .accountId(Id.Unknown)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun accountId(id: Id): Builder

        @BindsInstance
        fun onAccountSavedHandler(handler: OnAccountSavedHandler): Builder

        @BindsInstance
        fun onCloseHandler(handler: OnCloseHandler): Builder

        @BindsInstance
        fun accountEditIconUseCase(useCase: AccountEditIconUseCase): Builder

        @BindsInstance
        fun accountEditCurrencyUseCase(useCase: AccountEditCurrencyUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountEditScope
        fun viewModel(
            accountId: Id,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            accountEditIconUseCase: AccountEditIconUseCase,
            accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
            onAccountSavedHandler: OnAccountSavedHandler,
            configurationRepository: ConfigurationRepository,
        ): AccountEditViewModel = DefaultAccountEditViewModel(
            accountId = accountId,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            accountEditIconUseCase = accountEditIconUseCase,
            accountEditCurrencyUseCase = accountEditCurrencyUseCase,
            onAccountSavedHandler = onAccountSavedHandler,
            configurationRepository = configurationRepository,
        )

        @Provides
        @AccountEditScope
        fun viewProvider(
            viewModel: AccountEditViewModel,
            onCloseHandler: OnCloseHandler,
            imageLoader: ImageLoader,
        ): ViewProvider = AccountEditViewProvider(
            viewModel = viewModel,
            onClose = onCloseHandler,
            imageLoader = imageLoader,
        )
    }
}
