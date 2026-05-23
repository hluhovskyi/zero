package com.hluhovskyi.zero.auth

import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GoogleOAuthTokenProviderTest {

    private val refreshKey = "drive.refresh_token"
    private val labelKey = "drive.account_label"

    private fun provider(
        store: FakeSecureKeyValueStore,
        http: FakeHttpExecutor,
    ) = GoogleOAuthTokenProvider(
        secureKeyValueStore = store,
        httpExecutor = http,
        clientId = "client-123",
        scopes = listOf("https://www.googleapis.com/auth/drive.appdata"),
        currentActivity = { null },
    )

    @Test
    fun `getAccessToken returns cached token without a second network call`() = runTest {
        val http = FakeHttpExecutor().apply {
            enqueue(200, """{"access_token":"at1","expires_in":3600}""")
        }
        val provider = provider(FakeSecureKeyValueStore(mapOf(refreshKey to "rt")), http)

        assertEquals("at1", provider.getAccessToken())
        assertEquals("at1", provider.getAccessToken())
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `getAccessToken refreshes via token endpoint when no cached token`() = runTest {
        val http = FakeHttpExecutor().apply {
            enqueue(200, """{"access_token":"at1","expires_in":3600}""")
        }
        val provider = provider(FakeSecureKeyValueStore(mapOf(refreshKey to "rt")), http)

        assertEquals("at1", provider.getAccessToken())
        val request = http.requests.single()
        assertEquals(HttpRequest.Method.POST, request.method)
        assertEquals("https://oauth2.googleapis.com/token", request.url)
        val form = request.body as HttpRequest.Body.Form
        assertEquals("refresh_token", form.fields["grant_type"])
        assertEquals("rt", form.fields["refresh_token"])
        assertEquals("client-123", form.fields["client_id"])
    }

    @Test
    fun `getAccessToken returns null when no refresh token present`() = runTest {
        val http = FakeHttpExecutor()
        val provider = provider(FakeSecureKeyValueStore(), http)

        assertNull(provider.getAccessToken())
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `getAccessToken returns null when refresh endpoint reports invalid_grant`() = runTest {
        val http = FakeHttpExecutor().apply {
            enqueue(400, """{"error":"invalid_grant"}""")
        }
        val provider = provider(FakeSecureKeyValueStore(mapOf(refreshKey to "rt")), http)

        assertNull(provider.getAccessToken())
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `revoke calls revoke endpoint, clears keys, and flips isSignedIn false`() = runTest {
        val store = FakeSecureKeyValueStore(mapOf(refreshKey to "rt", labelKey to "me@x.com"))
        val http = FakeHttpExecutor().apply { enqueue(200) }
        val provider = provider(store, http)

        provider.revoke()

        val request = http.requests.single()
        assertEquals(HttpRequest.Method.POST, request.method)
        assertTrue(request.url.startsWith("https://oauth2.googleapis.com/revoke"))
        assertTrue(request.url.contains("token=rt"))
        assertNull(store.get(refreshKey))
        assertNull(store.get(labelKey))
        assertFalse(provider.isSignedIn.first())
    }

    @Test
    fun `revoke clears local state even when revoke call fails`() = runTest {
        val store = FakeSecureKeyValueStore(mapOf(refreshKey to "rt", labelKey to "me@x.com"))
        val http = FakeHttpExecutor().apply { enqueueFailure(IOException("offline")) }
        val provider = provider(store, http)

        provider.revoke()

        assertNull(store.get(refreshKey))
        assertNull(store.get(labelKey))
        assertFalse(provider.isSignedIn.first())
    }

    @Test
    fun `isSignedIn initial value derives from stored refresh token`() = runTest {
        val signedIn = provider(FakeSecureKeyValueStore(mapOf(refreshKey to "rt")), FakeHttpExecutor())
        val signedOut = provider(FakeSecureKeyValueStore(), FakeHttpExecutor())

        assertTrue(signedIn.isSignedIn.first())
        assertFalse(signedOut.isSignedIn.first())
    }
}
