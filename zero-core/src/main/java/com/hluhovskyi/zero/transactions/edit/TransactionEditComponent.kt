package com.hluhovskyi.zero.transactions.edit

import android.util.Log
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionEditScope

private const val TAG = "TransactionEditComponent"

fun interface OnTransactionSavedHandler {
    fun onSaved()

    object Noop : OnTransactionSavedHandler {
        override fun onSaved() = Unit
    }
}

@TransactionEditScope
@dagger.Component(
    modules = [TransactionEditComponent.Module::class],
    dependencies = [TransactionEditComponent.Dependencies::class]
)
abstract class TransactionEditComponent : AttachableViewComponent {

    internal abstract val viewModel: TransactionEditViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditComponent.builder()
            .dependencies(dependencies)
            .idGenerator(IdGenerator.UUID)
            .accountRepository(AccountRepository.Noop)
            .currencyRepository(CurrencyRepository.Noop)
            .transactionRepository(TransactionRepository.Noop)
            .onTransactionSavedHandler(OnTransactionSavedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun idGenerator(idGenerator: IdGenerator): Builder

        @BindsInstance
        fun accountRepository(accountRepository: AccountRepository): Builder

        @BindsInstance
        fun currencyRepository(currencyRepository: CurrencyRepository): Builder

        @BindsInstance
        fun transactionRepository(transactionRepository: TransactionRepository): Builder

        @BindsInstance
        fun logger(logger: Logger): Builder

        @BindsInstance
        fun onTransactionSavedHandler(handler: OnTransactionSavedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionEditScope
        fun viewProvider(
            viewModel: TransactionEditViewModel
        ): ViewProvider = TransactionEditViewProvider(
            viewModel = viewModel
        )

        @Provides
        @TransactionEditScope
        fun viewModel(
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            transactionRepository: TransactionRepository,
            idGenerator: IdGenerator,
            onTransactionSavedHandler: OnTransactionSavedHandler,
            logger: Logger
        ): TransactionEditViewModel = DefaultTransactionEditViewModel(
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            transactionRepository = transactionRepository,
            idGenerator = idGenerator,
            onTransactionSavedHandler = onTransactionSavedHandler,
            logger = logger
        )
    }
}
