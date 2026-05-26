package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
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

class OkHttpFrankfurterExchangeRateServiceTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

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
    fun `happy path parses rates and queries base`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"base":"USD","date":"2026-05-26","rates":{"EUR":0.86,"GBP":0.74}}""",
            ),
        )
        val service = service(endpoint = server.url("/v1").toString())

        val rates = service.ratesFor(Id("USD"))

        assertEquals(Rate(0.86).value, rates.getValue(Id("EUR")).value)
        assertEquals(Rate(0.74).value, rates.getValue(Id("GBP")).value)
        val recorded = server.takeRequest()
        assertEquals("/v1/latest?base=USD", recorded.path)
    }

    @Test
    fun `blank endpoint returns empty without request`() = runTest {
        val rates = service(endpoint = "").ratesFor(Id("USD"))

        assertTrue(rates.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `500 returns empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody(""))

        val rates = service(endpoint = server.url("/v1").toString()).ratesFor(Id("USD"))

        assertTrue(rates.isEmpty())
    }

    @Test
    fun `socket reset returns empty`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val rates = service(endpoint = server.url("/v1").toString()).ratesFor(Id("USD"))

        assertTrue(rates.isEmpty())
    }

    private fun service(endpoint: String): OkHttpFrankfurterExchangeRateService =
        OkHttpFrankfurterExchangeRateService(endpoint = endpoint, client = client, json = json)
}
