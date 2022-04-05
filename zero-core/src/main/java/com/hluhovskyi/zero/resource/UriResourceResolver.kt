package com.hluhovskyi.zero.resource

import android.content.Context
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.isAndroid
import com.hluhovskyi.zero.common.isFile
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import java.io.FileInputStream

private val ANDROID_ASSET_SCHEME = "file:///android_asset"

internal class UriResourceResolver(
    private val context: Context,
) : ResourceResolver by resourceResolverOf<UriRequest, UriResult>({ request ->
    flow<ResourceStatus<UriResult>> {
        val uri = request.uri
        when {
            uri !is Uri.NonEmpty -> {}
            request.uri.isFile -> {
                val stream = if ((request.uri as Uri.NonEmpty).value.startsWith(ANDROID_ASSET_SCHEME)) {
                    context.assets.open(uri.value.removePrefix(ANDROID_ASSET_SCHEME).removePrefix("/"))
                } else {
                    FileInputStream(uri.value)
                }
                emit(ResourceStatus.Result(UriResult(stream)))
            }
            request.uri.isAndroid -> context.contentResolver.openInputStream(android.net.Uri.parse(uri.value))
                ?.let { stream -> emit(ResourceStatus.Result(UriResult(stream))) }
        }
    }.onStart { emit(ResourceStatus.Idle()) }
})