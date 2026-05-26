package com.hluhovskyi.zero

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.hluhovskyi.zero.common.Uri
import android.net.Uri as AndroidUri
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.ui.graphics.Color as ComposeColor

internal class CoilImageLoader(
    private val context: Context,
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
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(coilModel(uri.value))
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

    /**
     * Coil 3's [coil3.fetch.ResourceUriFetcher] only accepts numeric resource URIs
     * (`android.resource://<pkg>/<resId>`), unlike Coil 2 which also resolved the
     * `<pkg>/<type>/<name>` form the app produces via `AndroidUriResourceFactory`.
     * Resolve that form to a resource id here and hand Coil the `@DrawableRes Int`, which it
     * maps back to a numeric resource URI. Other schemes (assets, files) pass through unchanged.
     */
    private fun coilModel(value: String): Any {
        val parsed = AndroidUri.parse(value)
        if (parsed.scheme != "android.resource") return value
        val pkg = parsed.authority ?: return value
        val type = parsed.pathSegments.getOrNull(0) ?: return value
        val name = parsed.pathSegments.getOrNull(1) ?: return value
        val resId = context.resources.getIdentifier(name, type, pkg)
        return if (resId != 0) resId else value
    }
}
