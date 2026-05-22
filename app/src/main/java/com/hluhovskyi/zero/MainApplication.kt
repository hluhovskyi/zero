package com.hluhovskyi.zero

import android.app.Application
import android.content.Context
import com.hluhovskyi.zero.testbridge.DefaultDatabaseTestBridge
import com.hluhovskyi.zero.testbridge.HasTestBridgeContainer
import com.hluhovskyi.zero.testbridge.TestBridgeContainer
import timber.log.Timber
import java.io.Closeable

internal class MainApplication :
    Application(),
    HasApplicationComponent,
    HasTestBridgeContainer {

    override val applicationComponent: ApplicationComponent by lazy {
        val dependencies = object : ApplicationComponent.Dependencies {
            override val context: Context = this@MainApplication
            override val application: Application = this@MainApplication
        }

        ApplicationComponent.builder(dependencies)
            .build()
    }

    override val testBridgeContainer: TestBridgeContainer by lazy {
        val db = applicationComponent.databaseComponent
        TestBridgeContainer(
            database = DefaultDatabaseTestBridge(
                cleanupJob = db.cleanupJob,
                currentUserRepository = db.currentUserRepository,
                accountRepository = db.accountRepository,
                categoryRepository = db.categoryRepository,
                transactionRepository = db.transactionRepository,
            ),
        )
    }

    private lateinit var attachCloseable: Closeable

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        attachCloseable = applicationComponent.attachable.attach()
    }

    override fun onTerminate() {
        attachCloseable.close()
        super.onTerminate()
    }
}
