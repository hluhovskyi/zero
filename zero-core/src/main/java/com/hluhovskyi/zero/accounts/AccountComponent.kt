package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
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
    dependencies = [AccountComponent.Dependencies::class]
)
abstract class AccountComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val iamgeLoader: ImageLoader
        val amountFormatter: AmountFormatter

        val accountRepository: AccountRepository
        val transactionRepository: TransactionRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountComponent> {

        fun dependencies(dependencies: Dependencies): Builder
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
        ): AccountUseCase = DefaultAccountUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
        )

        @Provides
        @AccountScope
        fun viewModel(
            useCase: AccountUseCase
        ): AccountViewModel = DefaultAccountViewModel(
            useCase = useCase
        )

        @Provides
        @AccountScope
        fun viewProvider(
            viewModel: AccountViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = AccountViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}