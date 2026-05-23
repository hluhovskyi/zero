package com.hluhovskyi.zero.auth

import android.app.Activity
import com.hluhovskyi.zero.http.HttpExecutor
import com.hluhovskyi.zero.security.SecureKeyValueStore
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AuthScope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class GoogleOAuthClientId

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class GoogleOAuthScopes

@AuthScope
@dagger.Component(
    modules = [AuthComponent.Module::class],
    dependencies = [AuthComponent.Dependencies::class],
)
interface AuthComponent {

    val googleOAuthTokenProvider: OAuthTokenProvider

    interface Dependencies {
        val httpExecutor: HttpExecutor
        val secureKeyValueStore: SecureKeyValueStore
        val currentActivityProvider: @JvmSuppressWildcards () -> Activity?
    }

    companion object {

        fun builder(): Builder = DaggerAuthComponent.builder()
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        fun build(): AuthComponent
    }

    @dagger.Module
    object Module {

        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

        @Provides
        @AuthScope
        @GoogleOAuthClientId
        internal fun clientId(): String = BuildConfig.DRIVE_OAUTH_CLIENT_ID

        @Provides
        @AuthScope
        @GoogleOAuthScopes
        internal fun scopes(): List<String> = listOf(DRIVE_APPDATA_SCOPE)

        @Provides
        @AuthScope
        internal fun googleOAuthTokenProvider(
            secureKeyValueStore: SecureKeyValueStore,
            httpExecutor: HttpExecutor,
            @GoogleOAuthClientId clientId: String,
            @GoogleOAuthScopes scopes: List<String>,
            currentActivityProvider: @JvmSuppressWildcards () -> Activity?,
        ): OAuthTokenProvider = GoogleOAuthTokenProvider(
            secureKeyValueStore = secureKeyValueStore,
            httpExecutor = httpExecutor,
            clientId = clientId,
            scopes = scopes,
            currentActivity = currentActivityProvider,
        )
    }
}
