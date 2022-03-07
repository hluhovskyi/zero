package com.hluhovskyi.zero.transactions.edit

import android.util.Log
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.IdGenerator
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

private const val TAG = "TransactionEditCompone"

@TransactionEditScope
@dagger.Component(
    modules = [TransactionEditComponent.Module::class],
    dependencies = [TransactionEditComponent.Dependencies::class]
)
abstract class TransactionEditComponent : AttachableViewComponent {

    override fun attach(): Closeable {
        Log.d(TAG, "attach")
        return object : Closeable {
            override fun close() {
                Log.d(TAG, "close")
            }
        }
    }

    interface Dependencies {

    }

    companion object {

        fun factory(): TransactionEditComponent.Factory = DaggerTransactionEditComponent.factory()
    }

    @dagger.Component.Factory
    interface Factory {

        fun create(
            dependencies: Dependencies,
            @BindsInstance idGenerator: IdGenerator = IdGenerator.UUID,
            @BindsInstance accountRepository: AccountRepository = AccountRepository.Noop,
            @BindsInstance currencyRepository: CurrencyRepository = CurrencyRepository.Noop,
            @BindsInstance transactionRepository: TransactionRepository = TransactionRepository.Noop
        ): TransactionEditComponent
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
            transactionRepository: TransactionRepository
        ): TransactionEditViewModel = DefaultTransactionEditViewModel(
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            transactionRepository = transactionRepository
        )
    }
}
