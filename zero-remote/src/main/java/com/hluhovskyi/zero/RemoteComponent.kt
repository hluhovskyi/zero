package com.hluhovskyi.zero

import android.content.Context
import com.hluhovskyi.zero.currencies.ChainedExchangeRateService
import com.hluhovskyi.zero.currencies.CurrencyApiExchangeRateService
import com.hluhovskyi.zero.currencies.CurrencyApiRemoteService
import com.hluhovskyi.zero.currencies.ExchangeRateService
import com.hluhovskyi.zero.currencies.FrankfurterExchangeRateService
import com.hluhovskyi.zero.currencies.FrankfurterRemoteService
import com.hluhovskyi.zero.feedback.FeedbackService
import com.hluhovskyi.zero.feedback.OkHttpFeedbackService
import com.hluhovskyi.zero.http.HttpExecutor
import com.hluhovskyi.zero.http.OkHttpHttpExecutor
import com.hluhovskyi.zero.integrity.IntegrityTokenProvider
import com.hluhovskyi.zero.integrity.PlayIntegrityTokenProvider
import dagger.BindsInstance
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class RemoteScope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class FeedbackEndpoint

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class IntegrityCloudProject

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class ExchangeRateEndpoint

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class CurrencyApiEndpoint

@RemoteScope
@dagger.Component(
    modules = [RemoteComponent.Module::class],
    dependencies = [RemoteComponent.Dependencies::class],
)
interface RemoteComponent {

    val feedbackService: FeedbackService

    val httpExecutor: HttpExecutor

    val exchangeRateService: ExchangeRateService

    interface Dependencies {

        val context: Context
    }

    companion object {

        private const val FRANKFURTER_ENDPOINT = "https://api.frankfurter.dev/"
        private const val CURRENCY_API_ENDPOINT = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/"

        fun builder(dependencies: Dependencies): Builder = DaggerRemoteComponent.builder()
            .dependencies(dependencies)
            .feedbackEndpoint("")
            .integrityCloudProject(0L)
            .exchangeRateEndpoint(FRANKFURTER_ENDPOINT)
            .currencyApiEndpoint(CURRENCY_API_ENDPOINT)
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun feedbackEndpoint(@FeedbackEndpoint endpoint: String): Builder

        @BindsInstance
        fun integrityCloudProject(@IntegrityCloudProject cloudProjectNumber: Long): Builder

        @BindsInstance
        fun exchangeRateEndpoint(@ExchangeRateEndpoint endpoint: String): Builder

        @BindsInstance
        fun currencyApiEndpoint(@CurrencyApiEndpoint endpoint: String): Builder

        fun build(): RemoteComponent
    }

    @dagger.Module
    object Module {

        @Provides
        @RemoteScope
        internal fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @Provides
        @RemoteScope
        internal fun json(): Json = Json { ignoreUnknownKeys = true }

        @Provides
        @RemoteScope
        internal fun httpExecutor(client: OkHttpClient): HttpExecutor = OkHttpHttpExecutor(client)

        @Provides
        @RemoteScope
        internal fun integrityTokenProvider(
            context: Context,
            @IntegrityCloudProject cloudProjectNumber: Long,
        ): IntegrityTokenProvider = PlayIntegrityTokenProvider(
            context = context,
            cloudProjectNumber = cloudProjectNumber,
        )

        @Provides
        @RemoteScope
        internal fun feedbackService(
            @FeedbackEndpoint endpoint: String,
            client: OkHttpClient,
            tokenProvider: IntegrityTokenProvider,
            json: Json,
        ): FeedbackService = OkHttpFeedbackService(
            endpoint = endpoint,
            client = client,
            tokenProvider = tokenProvider,
            json = json,
        )

        @Provides
        @RemoteScope
        internal fun frankfurterRemoteService(
            @ExchangeRateEndpoint endpoint: String,
            client: OkHttpClient,
            json: Json,
        ): FrankfurterRemoteService = retrofit(endpoint, client, json).create(FrankfurterRemoteService::class.java)

        @Provides
        @RemoteScope
        internal fun currencyApiRemoteService(
            @CurrencyApiEndpoint endpoint: String,
            client: OkHttpClient,
            json: Json,
        ): CurrencyApiRemoteService = retrofit(endpoint, client, json).create(CurrencyApiRemoteService::class.java)

        // Tiered: broad currency-api fills coverage, ECB-authoritative Frankfurter overrides its
        // fiat; both unreachable falls through to the caller's bundled rates.
        @Provides
        @RemoteScope
        internal fun exchangeRateService(
            frankfurter: FrankfurterRemoteService,
            currencyApi: CurrencyApiRemoteService,
        ): ExchangeRateService = ChainedExchangeRateService(
            listOf(
                CurrencyApiExchangeRateService(currencyApi),
                FrankfurterExchangeRateService(frankfurter),
            ),
        )
    }
}

private fun retrofit(endpoint: String, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(endpoint)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()
