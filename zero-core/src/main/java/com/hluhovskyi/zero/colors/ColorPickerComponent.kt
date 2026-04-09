package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ColorPickerScope

private const val TAG = "ColorPickerComponent"

@ColorPickerScope
@dagger.Component(
    modules = [ColorPickerComponent.Module::class],
    dependencies = [ColorPickerComponent.Dependencies::class],
)
abstract class ColorPickerComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: ColorPickerViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val colorRepository: ColorRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerColorPickerComponent.builder()
            .dependencies(dependencies)
            .onColorSelectedHandler(OnColorSelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ColorPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onColorSelectedHandler(handler: OnColorSelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ColorPickerScope
        fun viewModel(
            colorRepository: ColorRepository,
            onColorSelectedHandler: OnColorSelectedHandler,
        ): ColorPickerViewModel = DefaultColorPickerViewModel(
            colorRepository = colorRepository,
            onColorSelectedHandler = onColorSelectedHandler,
        )

        @Provides
        @ColorPickerScope
        fun viewProvider(
            viewModel: ColorPickerViewModel,
        ): ViewProvider = ColorPickerViewProvider(
            viewModel = viewModel,
        )
    }
}
