package com.hluhovskyi.zero

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hluhovskyi.zero.common.Uri
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.ui.graphics.Color as ComposeColor

internal class CoilImageLoader(
    private val context: Context,
    private val imageLoader: coil.ImageLoader,
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
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(uri.value)
                        .build(),
                    imageLoader = imageLoader,
                )
                ComposeImage(
                    painter = painter,
                    contentDescription = contentDescription,
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
