package com.hluhovskyi.zero

import android.app.Application
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.robots.BudgetRobot
import com.hluhovskyi.zero.robots.TransactionsRobot
import com.hluhovskyi.zero.testbridge.HasTestBridgeContainer
import com.hluhovskyi.zero.testbridge.TestBridgeContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    // Launched lazily inside onTransactions/onBudget so the test body can seed the DB BEFORE
    // the app subscribes. Otherwise HomeViewModel observes an empty DB on attach, paints the
    // Welcome screen, and the test races a re-emission to flip back to Transactions — which
    // loses under host load and the transaction list's selectAfter race never resolves.
    private var scenario: ActivityScenario<MainActivity>? = null

    @get:Rule(order = 0)
    val clearDataRule: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                runBlocking {
                    container.database.clearData()
                    // Re-seed presets so every test starts in the fresh-install baseline
                    // (Food & Drink, Transport, default accounts). Production seeds these
                    // on activity attach, which our lazy-launch harness defers until onX().
                    container.database.seedPresets()
                }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    protected fun seedDefaultSetup() = runBlocking { container.database.seedDefaultSetup() }

    protected fun seedBudgetOverScenario() = runBlocking { container.database.seedBudgetOverScenario() }

    protected fun seedExpenses() = runBlocking { container.database.seedExpenses() }

    protected fun onTransactions(): TransactionsRobot {
        launchApp()
        return TransactionsRobot(composeRule)
    }

    protected fun onBudget(): BudgetRobot {
        launchApp()
        return BudgetRobot(composeRule).open()
    }

    private fun launchApp() {
        if (scenario == null) {
            scenario = ActivityScenario.launch(MainActivity::class.java)
        }
    }
}
