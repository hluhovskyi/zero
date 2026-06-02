package com.hluhovskyi.zero.http

interface HttpExecutor {
    suspend fun execute(request: HttpRequest): HttpResponse

    data class HttpRequest(
        val method: Method,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: Body? = null,
    ) {
        enum class Method { GET, POST, DELETE }

        sealed interface Body {
            data class Json(val payload: String) : Body
            data class Form(val fields: Map<String, String>) : Body
            data class Multipart(
                val metadataJson: String,
                val contentType: String,
                val content: ByteArray,
            ) : Body
        }
    }

    data class HttpResponse(
        val status: Int,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        fun bodyAsString(): String = body.decodeToString()
    }
}
