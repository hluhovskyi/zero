package com.hluhovskyi.zero.currencies.picker

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CurrencyPickerScope

private const val TAG = "CurrencyPickerComponent"

@CurrencyPickerScope
@dagger.Component(
    dependencies = [CurrencyPickerComponent.Dependencies::class],
    modules = [CurrencyPickerComponent.Module::class],
)
abstract class CurrencyPickerComponent : AttachableViewComponent {

    internal abstract val viewModel: CurrencyPickerViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val currencyRepository: CurrencyRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerCurrencyPickerComponent.builder()
            .dependencies(dependencies)
            .onCurrencyPickedHandler(OnCurrencyPickedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CurrencyPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onCurrencyPickedHandler(handler: OnCurrencyPickedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CurrencyPickerScope
        fun viewModel(
            currencyRepository: CurrencyRepository,
            onCurrencyPickedHandler: OnCurrencyPickedHandler,
        ): CurrencyPickerViewModel = DefaultCurrencyPickerViewModel(
            currencyRepository = currencyRepository,
            onCurrencyPickedHandler = onCurrencyPickedHandler,
        )

        @Provides
        @CurrencyPickerScope
        fun viewProvider(
            viewModel: CurrencyPickerViewModel,
        ): ViewProvider = CurrencyPickerViewProvider(viewModel = viewModel)
    }
}
