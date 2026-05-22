package com.hluhovskyi.zero

import android.app.Application
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.crash.BuildConfig
import dagger.BindsInstance
import dagger.Provides
import io.sentry.android.core.SentryAndroid
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CrashScope

@CrashScope
@dagger.Component(
    modules = [CrashComponent.Module::class],
    dependencies = [CrashComponent.Dependencies::class],
)
abstract class CrashComponent {

    abstract val attachable: Attachable

    interface Dependencies {

        val application: Application
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerCrashComponent.builder()
            .dependencies(dependencies)
            .versionName("")
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CrashComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun versionName(versionName: String): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CrashScope
        internal fun attachable(
            application: Application,
            versionName: String,
        ): Attachable {
            if (BuildConfig.DEBUG || BuildConfig.SENTRY_DSN.isBlank()) {
                return Attachable.Noop
            }
            return CrashAttachable(
                application = application,
                dsn = BuildConfig.SENTRY_DSN,
                versionName = versionName,
            )
        }
    }
}

private class CrashAttachable(
    private val application: Application,
    private val dsn: String,
    private val versionName: String,
) : Attachable {

    override fun attach(): Closeable {
        SentryAndroid.init(application) { options ->
            options.dsn = dsn
            options.release = versionName
            options.environment = "production"
        }
        return Closeables.empty()
    }
}
