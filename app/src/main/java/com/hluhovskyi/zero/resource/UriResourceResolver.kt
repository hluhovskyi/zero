package com.hluhovskyi.zero.resource

import android.content.Context
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.isAndroid
import com.hluhovskyi.zero.common.isFile
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import java.io.FileInputStream

internal class UriResourceResolver(
    private val context: Context,
) : ResourceResolver by resourceResolverOf<UriRequest, UriResult>({ request ->
    flow<ResourceStatus<UriResult>> {
        val uri = request.uri
        when {
            uri !is Uri.NonEmpty -> {}
            request.uri.isFile -> emit(ResourceStatus.Result(UriResult(FileInputStream(uri.value))))
            request.uri.isAndroid -> emit(ResourceStatus.Result(UriResult(
                context.contentResolver.openInputStream(android.net.Uri.parse(uri.value))!!)
            ))
        }
    }
        .onStart { emit(ResourceStatus.Idle()) }
})