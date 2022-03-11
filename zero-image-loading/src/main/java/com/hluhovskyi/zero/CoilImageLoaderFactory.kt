package com.hluhovskyi.zero

import android.content.Context

internal class CoilImageLoaderFactory(
    private val context: Context
) : ImageLoader.Factory {

    override fun create(): ImageLoader = CoilImageLoader(
        context = context,
        imageLoader = coil.ImageLoader.Builder(context)
            .build()
    )
}