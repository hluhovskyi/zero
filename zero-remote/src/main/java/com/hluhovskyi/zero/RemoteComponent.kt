package com.hluhovskyi.zero

import android.content.Context
import com.hluhovskyi.zero.feedback.FeedbackService
import com.hluhovskyi.zero.feedback.OkHttpFeedbackService
import com.hluhovskyi.zero.integrity.IntegrityTokenProvider
import com.hluhovskyi.zero.integrity.PlayIntegrityTokenProvider
import dagger.BindsInstance
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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

@RemoteScope
@dagger.Component(
    modules = [RemoteComponent.Module::class],
    dependencies = [RemoteComponent.Dependencies::class],
)
interface RemoteComponent {

    val feedbackService: FeedbackService

    interface Dependencies {

        val context: Context
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerRemoteComponent.builder()
            .dependencies(dependencies)
            .feedbackEndpoint("")
            .integrityCloudProject(0L)
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun feedbackEndpoint(@FeedbackEndpoint endpoint: String): Builder

        @BindsInstance
        fun integrityCloudProject(@IntegrityCloudProject cloudProjectNumber: Long): Builder

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
    }
}
