package com.hluhovskyi.zero.common

interface AndroidUriResourceFactory {

    fun asset(path: String): Uri

    fun drawable(name: String): Uri

    fun raw(name: String): Uri
}

internal class DefaultAndroidUriResourceFactory(
    private val packageName: String,
) : AndroidUriResourceFactory {

    override fun asset(path: String): Uri = Uri("file:///android_asset/$path")

    override fun drawable(name: String): Uri = Uri("android.resource://$packageName/drawable/$name")

    override fun raw(name: String): Uri = Uri("android.resource://$packageName/raw/$name")
}
