package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class BudgetOverReallocateRobot(private val composeRule: ComposeTestRule) {

    init {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Reallocate From").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun assertSourceCovers(name: String): BudgetOverReallocateRobot {
        composeRule.onAllNodesWithText(name).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("✓ Covers it").assertIsDisplayed()
        return this
    }

    fun assertSourcePartial(name: String): BudgetOverReallocateRobot {
        composeRule.onAllNodesWithText(name).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Partial").assertIsDisplayed()
        return this
    }

    fun selectSource(name: String): BudgetOverReallocateRobot {
        composeRule.onNodeWithTag("Budget.over.source.$name").performClick()
        composeRule.waitForIdle()
        return this
    }

    fun confirmMove(): BudgetRobot {
        composeRule.apply {
            onNodeWithTag("Budget.over.reallocate.confirm").performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("Reallocate From").fetchSemanticsNodes().isEmpty()
            }
        }
        return BudgetRobot(composeRule)
    }
}
