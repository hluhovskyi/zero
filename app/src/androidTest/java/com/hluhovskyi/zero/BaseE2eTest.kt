package com.hluhovskyi.zero

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.testbridge.HasTestBridgeContainer
import com.hluhovskyi.zero.testbridge.TestBridgeContainer
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule

abstract class BaseE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container: TestBridgeContainer by lazy {
        val app = ApplicationProvider.getApplicationContext<Application>()
        check(app is HasTestBridgeContainer) {
            "$app is expected to implement HasTestBridgeContainer"
        }
        app.testBridgeContainer
    }

    @Before
    fun setUp() = runBlocking {
        container.database.clearData()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    protected fun seedDefaultSetup() = runBlocking { container.database.seedDefaultSetup() }
}
