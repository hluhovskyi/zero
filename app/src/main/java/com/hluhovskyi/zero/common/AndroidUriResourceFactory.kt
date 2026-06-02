package com.hluhovskyi.zero.common

import android.content.Context

interface AndroidUriResourceFactory {

    fun asset(path: String): Uri

    fun drawable(name: String): Uri

    fun raw(name: String): Uri
}

internal class DefaultAndroidUriResourceFactory(
    private val context: Context,
) : AndroidUriResourceFactory {

    override fun asset(path: String): Uri = Uri("file:///android_asset/$path")

    override fun drawable(name: String): Uri = resourceUri("drawable", name)

    override fun raw(name: String): Uri = resourceUri("raw", name)

    // Resolve the resource id up front so the URI is the canonical numeric
    // android.resource://<pkg>/<resId> form.
    private fun resourceUri(type: String, name: String): Uri {
        val packageName = context.packageName
        val resId = context.resources.getIdentifier(name, type, packageName)
        return Uri("android.resource://$packageName/$resId")
    }
}
