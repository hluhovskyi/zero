package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class IconPickerScope

@IconPickerScope
@dagger.Component(
    dependencies = [IconPickerComponent.Dependencies::class],
    modules = [IconPickerComponent.Module::class],
)
abstract class IconPickerComponent : AttachableViewComponent {

    internal abstract val viewModel: IconPickerViewModel

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader

        val iconRepository: IconRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerIconPickerComponent.builder()
            .dependencies(dependencies)
            .onIconSelectedHandler(OnIconSelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<IconPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onIconSelectedHandler(handler: OnIconSelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @IconPickerScope
        fun viewModel(
            iconRepository: IconRepository,
            onIconSelectedHandler: OnIconSelectedHandler,
        ): IconPickerViewModel = DefaultIconPickerViewModel(
            iconRepository = iconRepository,
            onIconSelectedHandler = onIconSelectedHandler
        )

        @Provides
        @IconPickerScope
        fun viewProvider(
            viewModel: IconPickerViewModel,
            imageLoader: ImageLoader
        ): ViewProvider = IconPickerViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}