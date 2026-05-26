package com.hluhovskyi.zero

import android.content.Context
import coil3.ImageLoader as CoilImageLoaderLib

internal class CoilImageLoaderFactory(
    private val context: Context,
) : ImageLoader.Factory {

    override fun create(): ImageLoader = CoilImageLoader(
        imageLoader = CoilImageLoaderLib.Builder(context)
            .components { add(ResourceNameUriMapper()) }
            .build(),
    )
}
