package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountScope

@AccountScope
@dagger.Component(
    modules = [AccountComponent.Module::class],
    dependencies = [AccountComponent.Dependencies::class]
)
abstract class AccountComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {

    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountComponent.builder()
            .dependencies(dependencies)
            .accountRepository(AccountRepository.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun accountRepository(accountRepository: AccountRepository): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountScope
        fun viewModel(
            accountRepository: AccountRepository,
        ): AccountViewModel = DefaultAccountViewModel(
            accountRepository = accountRepository
        )

        @Provides
        @AccountScope
        fun viewProvider(
            viewModel: AccountViewModel
        ): ViewProvider = AccountViewProvider(
            viewModel = viewModel
        )
    }
}