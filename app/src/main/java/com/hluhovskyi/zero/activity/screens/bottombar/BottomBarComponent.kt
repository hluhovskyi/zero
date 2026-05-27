package com.hluhovskyi.zero.activity.screens.bottombar

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BottomBarScope

private const val TAG = "BottomBarComponent"

@BottomBarScope
@dagger.Component(
    modules = [BottomBarComponent.Module::class],
    dependencies = [BottomBarComponent.Dependencies::class],
)
internal abstract class BottomBarComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: BottomBarViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val androidUriResourceFactory: AndroidUriResourceFactory
        val budgetQueryUseCase: BudgetQueryUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerBottomBarComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BottomBarComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun navigator(navigator: Navigator): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BottomBarScope
        fun viewModel(
            androidUriResourceFactory: AndroidUriResourceFactory,
            navigator: Navigator,
            budgetQueryUseCase: BudgetQueryUseCase,
        ): BottomBarViewModel = DefaultBottomBarViewModel(
            androidUriResourceFactory = androidUriResourceFactory,
            navigator = navigator,
            budgetQueryUseCase = budgetQueryUseCase,
        )

        @Provides
        @BottomBarScope
        fun viewProvider(
            viewModel: BottomBarViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = BottomBarViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
