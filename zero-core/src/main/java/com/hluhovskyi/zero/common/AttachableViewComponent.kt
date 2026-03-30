package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

interface AttachableViewComponent : Attachable, Tagged {

    val viewProvider: ViewProvider
}

fun Buildable<out AttachableViewComponent>.logging(logger: Logger): Buildable<out AttachableViewComponent> =
    LoggingAttachableViewComponent(delegate = this, logger = logger)

@Composable
fun <T : AttachableViewComponent> T.AttachWithView(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onAttach: (T) -> Unit = {},
    onDispose: (T) -> Unit = {},
) {
    DisposableEffect(lifecycleOwner) {
        val closeable = attach()
        onAttach(this@AttachWithView)
        onDispose {
            onDispose(this@AttachWithView)
            closeable.close()
        }
    }
    viewProvider()
}

@Composable
fun <Component : AttachableViewComponent> Buildable<out Component>.AttachWithView() {
    val component = remember { build() }
    component.AttachAndRetainWithView()
}

@Composable
private fun <Component : AttachableViewComponent> Component.AttachAndRetainWithView(
    holder: ComponentHolderViewModel<Component> = viewModel(
        key = this.tag,
        factory = ComponentHolderViewModel.Factory(this)
    ),
) {
    holder.component.viewProvider()
}

private class ComponentHolderViewModel<T : AttachableViewComponent>(
    private val componentInput: T
) : ViewModel() {

    private val closeable = AtomicReference<Closeable>(Closeables.empty())

    val component: T = componentInput.also {
        closeable.set(it.attach())
    }

    override fun onCleared() {
        closeable.get().close()
    }

    class Factory<Component : AttachableViewComponent>(
        val component: Component
    ) : ViewModelProvider.Factory {

        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ComponentHolderViewModel<Component>(component) as T
    }
}

private class LoggingAttachableViewComponent(
    private val delegate: Buildable<out AttachableViewComponent>,
    logger: Logger,
) : Buildable<AttachableViewComponent> {

    private val logger = logger.withTag("LoggingAttachableViewComponent")

    override fun build(): AttachableViewComponent {
        val component = delegate.build()
        logger.d("[${component.tag}] build")
        return LoggingComponent(
            delegate = component,
            logger = logger,
        )
    }

    private class LoggingComponent(
        private val delegate: AttachableViewComponent,
        private val logger: Logger,
    ) : AttachableViewComponent {

        override val tag: String = delegate.tag

        override val viewProvider: ViewProvider = delegate.viewProvider
            .also { logger.d("[$tag] viewProvider") }

        override fun attach(): Closeable {
            logger.d("[$tag] attach")
            val closeable = delegate.attach()
            return Closeables.from {
                logger.d("[$tag] close")
                closeable.close()
            }
        }
    }
}