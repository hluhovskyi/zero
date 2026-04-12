package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class ColorId

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class SelectedIconId

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class IconPickerScope

private const val TAG = "IconPickerComponent"

@IconPickerScope
@dagger.Component(
    dependencies = [IconPickerComponent.Dependencies::class],
    modules = [IconPickerComponent.Module::class],
)
abstract class IconPickerComponent : AttachableViewComponent {

    internal abstract val viewModel: IconPickerViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerIconPickerComponent.builder()
            .dependencies(dependencies)
            .onIconSelectedHandler(OnIconSelectedHandler.Noop)
            .colorId(Id.Unknown)
            .selectedIconId(Id.Unknown)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<IconPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onIconSelectedHandler(handler: OnIconSelectedHandler): Builder

        @BindsInstance
        fun colorId(@ColorId colorId: Id): Builder

        @BindsInstance
        fun selectedIconId(@SelectedIconId selectedIconId: Id): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @IconPickerScope
        fun viewModel(
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            onIconSelectedHandler: OnIconSelectedHandler,
            @ColorId colorId: Id,
            @SelectedIconId selectedIconId: Id,
        ): IconPickerViewModel = DefaultIconPickerViewModel(
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            onIconSelectedHandler = onIconSelectedHandler,
            colorId = colorId,
            selectedIconId = selectedIconId,
        )

        @Provides
        @IconPickerScope
        fun viewProvider(
            viewModel: IconPickerViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = IconPickerViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
