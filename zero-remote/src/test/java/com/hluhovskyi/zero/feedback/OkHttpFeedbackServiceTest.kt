package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.integrity.FakeIntegrityTokenProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpFeedbackServiceTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val report = FeedbackReport(title = "t", body = "b", type = FeedbackType.Bug, isDebug = true)

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
    fun `happy path returns Success with parsed issueUrl`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"issueUrl":"https://example.test/issues/1"}"""),
        )
        val service = service(endpoint = server.url("/").toString(), token = "tok")

        val result = service.submit(report)

        assertEquals(FeedbackSubmitResult.Success("https://example.test/issues/1"), result)
        assertEquals(1, server.requestCount)
        val recorded = server.takeRequest()
        assertEquals("tok", recorded.getHeader("X-Integrity-Token"))
    }

    @Test
    fun `blank endpoint returns Failure without making any request`() = runTest {
        val service = service(endpoint = "", token = "tok")

        val result = service.submit(report)

        assertEquals(FeedbackSubmitResult.Failure, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `null integrity token returns Failure without making any request`() = runTest {
        val service = service(endpoint = server.url("/").toString(), token = null)

        val result = service.submit(report)

        assertEquals(FeedbackSubmitResult.Failure, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `401 returns Failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(""))
        val service = service(endpoint = server.url("/").toString(), token = "tok")

        val result = service.submit(report)

        assertEquals(FeedbackSubmitResult.Failure, result)
    }

    @Test
    fun `500 returns Failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody(""))
        val service = service(endpoint = server.url("/").toString(), token = "tok")

        val result = service.submit(report)

        assertEquals(FeedbackSubmitResult.Failure, result)
    }

    @Test
    fun `socket reset returns Failure`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val service = service(endpoint = server.url("/").toString(), token = "tok")

        val result = service.submit(report)

        assertTrue(result is FeedbackSubmitResult.Failure)
    }

    private fun service(endpoint: String, token: String?): OkHttpFeedbackService = OkHttpFeedbackService(
        endpoint = endpoint,
        client = client,
        tokenProvider = FakeIntegrityTokenProvider { token },
        json = json,
    )
}
