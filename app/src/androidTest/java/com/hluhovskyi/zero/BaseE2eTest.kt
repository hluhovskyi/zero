package com.hluhovskyi.zero

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.robots.BudgetRobot
import com.hluhovskyi.zero.robots.TransactionsRobot
import com.hluhovskyi.zero.testbridge.HasTestBridgeContainer
import com.hluhovskyi.zero.testbridge.TestBridgeContainer
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

abstract class BaseE2eTest {

    private val container: TestBridgeContainer by lazy {
        val app = ApplicationProvider.getApplicationContext<Application>()
        check(app is HasTestBridgeContainer) {
            "$app is expected to implement HasTestBridgeContainer"
        }
        app.testBridgeContainer
    }

    @get:Rule(order = 0)
    val clearDataRule: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                runBlocking { container.database.clearData() }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected fun seedDefaultSetup() = runBlocking { container.database.seedDefaultSetup() }

    protected fun seedBudgetOverScenario() = runBlocking { container.database.seedBudgetOverScenario() }

    protected fun seedExpenses() = runBlocking { container.database.seedExpenses() }

    protected fun onTransactions() = TransactionsRobot(composeRule)

    protected fun onBudget() = BudgetRobot(composeRule).open()
}
