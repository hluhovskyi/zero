package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RetrofitExchangeRateServiceTest {

    private lateinit var server: MockWebServer
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
    fun `happy path maps snapshot and queries v1 latest with EUR base`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"base":"EUR","date":"2026-05-26","rates":{"USD":1.16,"GBP":0.86}}""",
            ),
        )

        val snapshot = service().latest()!!

        assertEquals(Id("EUR"), snapshot.base)
        assertEquals(Rate(1.16).value, snapshot.rates.getValue(Id("USD")).value)
        assertEquals(Rate(0.86).value, snapshot.rates.getValue(Id("GBP")).value)
        assertEquals("/v1/latest?base=EUR", server.takeRequest().path)
    }

    @Test
    fun `500 returns null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        assertNull(service().latest())
    }

    @Test
    fun `socket reset returns null`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertNull(service().latest())
    }

    private fun service(): RetrofitExchangeRateService {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FrankfurterRemoteService::class.java)
        return RetrofitExchangeRateService(api)
    }
}
