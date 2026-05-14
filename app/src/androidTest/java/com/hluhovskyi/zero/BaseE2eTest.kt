package com.hluhovskyi.zero

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.testbridge.DatabaseTestBridge
import com.hluhovskyi.zero.testbridge.TestBridge
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule

abstract class BaseE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val bridge: TestBridge by lazy {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dbComponent = (app as HasApplicationComponent).applicationComponent.databaseComponent
        DatabaseTestBridge(
            cleanupJob = dbComponent.cleanupJob,
            currentUserRepository = dbComponent.currentUserRepository,
            accountRepository = dbComponent.accountRepository,
            categoryRepository = dbComponent.categoryRepository,
            transactionRepository = dbComponent.transactionRepository,
        )
    }

    @Before
    fun setUp() = runBlocking { bridge.clearData() }

    protected fun seedDefaultSetup() = runBlocking { bridge.seedDefaultSetup() }
}
