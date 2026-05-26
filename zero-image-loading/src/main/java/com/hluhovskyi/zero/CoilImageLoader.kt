package com.hluhovskyi.zero

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.hluhovskyi.zero.common.Uri
import androidx.compose.ui.graphics.Color as ComposeColor

internal class CoilImageLoader(
    private val imageLoader: coil3.ImageLoader,
) : ImageLoader {

    @Composable
    override fun View(
        uri: Uri,
        contentDescription: String?,
        modifier: Modifier,
        scale: ImageLoader.Scale,
        tint: ComposeColor?,
    ) {
        val contentScale = when (scale) {
            ImageLoader.Scale.Fit -> ContentScale.Fit
            ImageLoader.Scale.Crop -> ContentScale.Crop
        }

        when (uri) {
            is Uri.NonEmpty -> {
                AsyncImage(
                    model = uri.value,
                    contentDescription = contentDescription,
                    imageLoader = imageLoader,
                    modifier = modifier,
                    contentScale = contentScale,
                    colorFilter = tint
                        ?.takeIf { it != ComposeColor.Unspecified }
                        ?.let { ColorFilter.tint(it) },
                )
            }
            else -> {
                Spacer(modifier = modifier)
            }
        }
    }
}
