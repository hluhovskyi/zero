package com.hluhovskyi.zero.resource

import com.hluhovskyi.zero.common.Uri
import java.io.InputStream

data class UriRequest(
    val uri: Uri
) : ResourceRequest<UriResult>

data class UriResult(
    val inputStream: InputStream
)