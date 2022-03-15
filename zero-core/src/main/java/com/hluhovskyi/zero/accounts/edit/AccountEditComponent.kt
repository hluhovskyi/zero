package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
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
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountEditComponent> {

        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountEditScope
        fun viewModel(): AccountEditViewModel = DefaultAccountEditViewModel()

        @Provides
        @AccountEditScope
        fun viewProvider(
            viewModel: AccountEditViewModel
        ): ViewProvider = AccountEditViewProvider(
            viewModel = viewModel
        )
    }
}