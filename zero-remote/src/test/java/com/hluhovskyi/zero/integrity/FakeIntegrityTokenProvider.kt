package com.hluhovskyi.zero.integrity

internal class FakeIntegrityTokenProvider(
    private val tokenFor: (nonce: String) -> String?,
) : IntegrityTokenProvider {

    override suspend fun getToken(nonce: String): String? = tokenFor(nonce)
}
