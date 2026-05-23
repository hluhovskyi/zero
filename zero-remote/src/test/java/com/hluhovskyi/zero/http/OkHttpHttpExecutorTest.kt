package com.hluhovskyi.zero.http

import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpHttpExecutorTest {

    private lateinit var server: MockWebServer
    private val executor = OkHttpHttpExecutor(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET returns status and body and forwards headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))

        val response = executor.execute(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = server.url("/files").toString(),
                headers = mapOf("Authorization" to "Bearer tok"),
            ),
        )

        assertEquals(200, response.status)
        assertEquals("hello", response.bodyAsString())
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
    }

    @Test
    fun `POST JSON carries application-json content type and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = executor.execute(
            HttpRequest(
                method = HttpRequest.Method.POST,
                url = server.url("/files").toString(),
                body = HttpRequest.Body.Json("""{"name":"zero-backup.json"}"""),
            ),
        )

        assertEquals(200, response.status)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        assertEquals("""{"name":"zero-backup.json"}""", recorded.body.readUtf8())
    }

    @Test
    fun `POST multipart is multipart-related with two typed parts`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        executor.execute(
            HttpRequest(
                method = HttpRequest.Method.POST,
                url = server.url("/upload").toString(),
                body = HttpRequest.Body.Multipart(
                    metadataJson = """{"name":"zero-backup.json","parents":["appDataFolder"]}""",
                    contentType = "application/json",
                    content = """{"format":1}""".toByteArray(),
                ),
            ),
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/related"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.contains(""""format":1"""))
        // Each of the two parts declares its own Content-Type.
        assertEquals(2, Regex("Content-Type: application/json").findAll(body).count())
    }

    @Test
    fun `401 is returned as-is, not swallowed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))

        val response = executor.execute(
            HttpRequest(method = HttpRequest.Method.GET, url = server.url("/x").toString()),
        )

        assertEquals(401, response.status)
        assertEquals("nope", response.bodyAsString())
    }

    @Test
    fun `network failure bubbles out as IOException`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        var thrown: Throwable? = null
        try {
            executor.execute(
                HttpRequest(method = HttpRequest.Method.GET, url = server.url("/x").toString()),
            )
        } catch (e: IOException) {
            thrown = e
        }

        assertTrue(thrown is IOException)
    }
}
