package com.hluhovskyi.zero.common

interface AndroidUriResourceFactory {

    fun drawable(name: String): Uri

    fun raw(name: String): Uri
}

internal class DefaultAndroidUriResourceFactory(
    private val packageName: String
) : AndroidUriResourceFactory {

    override fun drawable(name: String): Uri =
        Uri("android.resource://$packageName/drawable/$name")

    override fun raw(name: String): Uri =
        Uri("android.resource://$packageName/raw/$name")
}