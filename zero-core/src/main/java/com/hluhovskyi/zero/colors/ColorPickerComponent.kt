package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.ViewProvider
import java.io.Closeable

private const val TAG = "ColorPickerComponent"

class ColorPickerComponent private constructor(
    colorRepository: ColorRepository,
    onColorSelectedHandler: OnColorSelectedHandler,
) : AttachableViewComponent {

    override val tag: String = TAG

    private val viewModel: ColorPickerViewModel by lazy {
        DefaultColorPickerViewModel(
            colorRepository = colorRepository,
            onColorSelectedHandler = onColorSelectedHandler,
        )
    }

    override val viewProvider: ViewProvider by lazy {
        ColorPickerViewProvider(viewModel = viewModel)
    }

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val colorRepository: ColorRepository
    }

    class Factory(private val dependencies: Dependencies) {

        fun create(
            onColorSelectedHandler: OnColorSelectedHandler = OnColorSelectedHandler.Noop,
        ): ColorPickerComponent = ColorPickerComponent(
            colorRepository = dependencies.colorRepository,
            onColorSelectedHandler = onColorSelectedHandler,
        )
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}
