package com.hluhovskyi.zero

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.testbridge.TestBridgeContainer
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule

abstract class BaseE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container: TestBridgeContainer by lazy {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        TestBridgeContainerFactory.create((app as HasApplicationComponent).applicationComponent)
    }

    @Before
    fun setUp() = runBlocking {
        container.database.clearData()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    protected fun seedDefaultSetup() = runBlocking { container.database.seedDefaultSetup() }
}
