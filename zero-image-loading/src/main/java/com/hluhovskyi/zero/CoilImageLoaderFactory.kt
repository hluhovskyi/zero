package com.hluhovskyi.zero

import android.content.Context
import coil.ImageLoader as CoilImageLoaderLib

internal class CoilImageLoaderFactory(
    private val context: Context,
) : ImageLoader.Factory {

    override fun create(): ImageLoader = CoilImageLoader(
        context = context,
        imageLoader = CoilImageLoaderLib.Builder(context)
            .build(),
    )
}
