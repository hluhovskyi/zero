package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner

interface AttachableViewComponent : Attachable {
    
    val viewProvider: ViewProvider
}

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
fun <T : AttachableViewComponent> Buildable<out T>.AttachWithView(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onAttach: (T) -> Unit = {},
    onDispose: (T) -> Unit = {},
) {
    val component by remember(lifecycleOwner) { mutableStateOf(build()) }
    component.AttachWithView(
        onAttach = onAttach,
        onDispose = onDispose,
    )
}