package com.hluhovskyi.zero.integrity

internal interface IntegrityTokenProvider {

    suspend fun getToken(nonce: String): String?
}
