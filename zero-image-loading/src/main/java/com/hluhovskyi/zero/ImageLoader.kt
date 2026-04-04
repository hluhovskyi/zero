package com.hluhovskyi.zero

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Uri

interface ImageLoader {

    @Composable
    fun View(
        uri: Uri,
        contentDescription: String?,
        modifier: Modifier,
        scale: Scale,
        tint: Color? = null,
    )

    enum class Scale {
        Fit,
        Crop
    }

    interface Factory {

        fun create(): ImageLoader
    }

    companion object {

        fun empty(): ImageLoader = EmptyImageLoader

        fun factory(
            context: Context
        ): Factory = CoilImageLoaderFactory(context)
    }
}

@Composable
fun ImageLoader.View(
    uri: Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scale: ImageLoader.Scale = ImageLoader.Scale.Fit,
    tint: Color? = null,
) {
    View(
        uri = uri,
        contentDescription = contentDescription,
        modifier = modifier,
        scale = scale,
        tint = tint,
    )
}

@Composable
fun ImageLoader.View(
    image: Image,
    modifier: Modifier = Modifier,
    scale: ImageLoader.Scale = ImageLoader.Scale.Fit,
    tint: Color? = null,
) {
    View(
        uri = image.uri,
        contentDescription = image.description,
        modifier = modifier,
        scale = scale,
        tint = tint,
    )
}
