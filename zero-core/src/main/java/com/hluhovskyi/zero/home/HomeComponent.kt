package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.settings.OnImportSelectedHandler
import com.hluhovskyi.zero.transactions.DisplayConfig
import com.hluhovskyi.zero.transactions.OnTransactionSelectedHandler
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.filter.TransactionFilterUseCase
import com.hluhovskyi.zero.user.DefaultNewUserUseCase
import com.hluhovskyi.zero.user.NewUserUseCase
import com.hluhovskyi.zero.welcome.WelcomeComponent
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class HomeScope

private const val TAG = "HomeComponent"

@HomeScope
@dagger.Component(
    modules = [HomeComponent.Module::class],
    dependencies = [HomeComponent.Dependencies::class],
)
abstract class HomeComponent : AttachableViewComponent {

    internal abstract val viewModel: HomeViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val transactionRepository: TransactionRepository
        val welcomeComponentBuilder: WelcomeComponent.Builder
        val transactionComponentBuilder: TransactionComponent.Builder
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerHomeComponent.builder()
            .dependencies(dependencies)
            .onImportSelectedHandler(OnImportSelectedHandler.Noop)
            .onTransactionSelectedHandler(OnTransactionSelectedHandler.Noop)
            .transactionFilterUseCase(TransactionFilterUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<HomeComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onImportSelectedHandler(handler: OnImportSelectedHandler): Builder

        @BindsInstance
        fun onTransactionSelectedHandler(handler: OnTransactionSelectedHandler): Builder

        @BindsInstance
        fun transactionFilterUseCase(useCase: TransactionFilterUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @HomeScope
        fun newUserUseCase(
            transactionRepository: TransactionRepository,
        ): NewUserUseCase = DefaultNewUserUseCase(
            transactionRepository = transactionRepository,
        )

        @Provides
        @HomeScope
        fun welcomeComponent(
            builder: WelcomeComponent.Builder,
            onImportSelectedHandler: OnImportSelectedHandler,
        ): WelcomeComponent = builder
            .onImportSelectedHandler(onImportSelectedHandler)
            .build()

        @Provides
        @HomeScope
        fun transactionComponent(
            builder: TransactionComponent.Builder,
            onTransactionSelectedHandler: OnTransactionSelectedHandler,
            transactionFilterUseCase: TransactionFilterUseCase,
        ): TransactionComponent = builder
            .onTransactionSelectHandler(onTransactionSelectedHandler)
            .transactionFilterUseCase(transactionFilterUseCase)
            .displayConfig(DisplayConfig())
            .build()

        @Provides
        @HomeScope
        fun viewModel(
            newUserUseCase: NewUserUseCase,
        ): HomeViewModel = DefaultHomeViewModel(
            newUserUseCase = newUserUseCase,
        )

        @Provides
        @HomeScope
        fun viewProvider(
            viewModel: HomeViewModel,
            welcomeComponent: WelcomeComponent,
            transactionComponent: TransactionComponent,
        ): ViewProvider = HomeViewProvider(
            viewModel = viewModel,
            welcomeComponent = welcomeComponent,
            transactionComponent = transactionComponent,
        )
    }
}
