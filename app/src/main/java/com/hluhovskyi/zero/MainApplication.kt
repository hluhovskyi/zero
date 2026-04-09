package com.hluhovskyi.zero

import android.app.Application
import android.content.Context
import timber.log.Timber

internal class MainApplication :
    Application(),
    HasApplicationComponent {

    override val applicationComponent: ApplicationComponent by lazy {
        val dependencies = object : ApplicationComponent.Dependencies {
            override val context: Context = this@MainApplication
        }

        ApplicationComponent.builder(dependencies)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
