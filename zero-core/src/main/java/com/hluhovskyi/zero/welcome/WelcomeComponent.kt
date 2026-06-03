package com.hluhovskyi.zero.welcome

import com.hluhovskyi.zero.backup.OnRestoreSelectedHandler
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.settings.OnImportSelectedHandler
import com.hluhovskyi.zero.transactions.OnAddTransactionHandler
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class WelcomeScope

private const val TAG = "WelcomeComponent"

@WelcomeScope
@dagger.Component(
    modules = [WelcomeComponent.Module::class],
    dependencies = [WelcomeComponent.Dependencies::class],
)
abstract class WelcomeComponent : AttachableViewComponent {

    internal abstract val viewModel: WelcomeViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val dispatchers: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerWelcomeComponent.builder()
            .dependencies(dependencies)
            .onImportSelectedHandler(OnImportSelectedHandler.Noop)
            .onRestoreSelectedHandler(OnRestoreSelectedHandler.Noop)
            .onAddTransactionHandler(OnAddTransactionHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<WelcomeComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onImportSelectedHandler(handler: OnImportSelectedHandler): Builder

        @BindsInstance
        fun onRestoreSelectedHandler(handler: OnRestoreSelectedHandler): Builder

        @BindsInstance
        fun onAddTransactionHandler(handler: OnAddTransactionHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @WelcomeScope
        fun viewModel(
            onImportSelected: OnImportSelectedHandler,
            onRestoreSelected: OnRestoreSelectedHandler,
            dispatchers: DispatcherProvider,
        ): WelcomeViewModel = DefaultWelcomeViewModel(
            onImportSelected = onImportSelected,
            onRestoreSelected = onRestoreSelected,
            dispatchers = dispatchers,
        )

        @Provides
        @WelcomeScope
        fun viewProvider(
            viewModel: WelcomeViewModel,
            onAddTransaction: OnAddTransactionHandler,
        ): ViewProvider = WelcomeViewProvider(
            viewModel = viewModel,
            onAddTransaction = onAddTransaction,
        )
    }
}
