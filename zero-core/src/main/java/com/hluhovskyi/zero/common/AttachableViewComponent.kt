package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    AttachAndRetainWithView()
}

@Composable
private fun <Component : AttachableViewComponent> Buildable<out Component>.AttachAndRetainWithView(
    holder: ComponentHolderViewModel<Component> = viewModel(
        key = hashCode().toString(),
        factory = ComponentHolderViewModel.Factory(this)
    ),
) {
    holder.component.viewProvider()
}

private class ComponentHolderViewModel<T : AttachableViewComponent>(
    val builder: Buildable<out T>
) : ViewModel() {

    private val closeable = AtomicReference<Closeable>(Closeables.empty())
    private val lazyComponent: Lazy<T> = lazy {
        builder.build()
    }

    val component: T = lazyComponent.value.also {
        closeable.set(it.attach())
    }

    override fun onCleared() {
        closeable.get().close()
    }

    class Factory<Component : AttachableViewComponent>(
        val component: Buildable<out Component>
    ) : ViewModelProvider.Factory {

        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
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
        logger.d("build [${component.tag}]")
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
            .also { logger.d("viewProvider [$tag]") }

        override fun attach(): Closeable {
            logger.d("attach [$tag]")
            val closeable = delegate.attach()
            return Closeables.from {
                logger.d("close [$tag]")
                closeable.close()
            }
        }
    }
}