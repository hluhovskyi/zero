package com.hluhovskyi.zero

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import com.hluhovskyi.zero.common.Uri

internal class CoilImageLoader(
    private val context: Context,
    private val imageLoader: coil.ImageLoader,
) : ImageLoader {

    @Composable
    override fun View(
        uri: Uri,
        contentDescription: String?,
        modifier: Modifier,
        scale: ImageLoader.Scale
    ) {
        val contentScale = when (scale) {
            ImageLoader.Scale.Fit -> ContentScale.Fit
            ImageLoader.Scale.Crop -> ContentScale.Crop
        }

        when (uri) {
            is Uri.NonEmpty -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri.value)
                        .build(),
                    contentDescription = contentDescription,
                    imageLoader = imageLoader,
                    modifier = modifier,
                    contentScale = contentScale
                )
            }
            else -> {
                Spacer(
                    modifier = modifier
                )
            }
        }
    }
}