package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionRepository
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
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerHomeComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<HomeComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun welcomeComponentBuilder(builder: WelcomeComponent.Builder): Builder

        @BindsInstance
        fun transactionComponentBuilder(builder: TransactionComponent.Builder): Builder
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
        ): WelcomeComponent = builder.build()

        @Provides
        @HomeScope
        fun transactionComponent(
            builder: TransactionComponent.Builder,
        ): TransactionComponent = builder.build()

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
