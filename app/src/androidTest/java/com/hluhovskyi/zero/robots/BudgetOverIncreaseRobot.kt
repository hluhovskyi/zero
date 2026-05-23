package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class BudgetOverIncreaseRobot(private val composeRule: ComposeTestRule) {

    init {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Increase Budget").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun assertSuggestionVisible(label: String): BudgetOverIncreaseRobot {
        composeRule.onNodeWithText(label).assertIsDisplayed()
        return this
    }

    fun pickSuggestion(label: String): BudgetOverIncreaseRobot {
        composeRule.onAllNodesWithText(label).onFirst().performClick()
        composeRule.waitForIdle()
        return this
    }

    fun confirm(): BudgetRobot {
        composeRule.apply {
            onNodeWithTag("Budget.over.increase.confirm").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Increase Budget").fetchSemanticsNodes().isEmpty()
            }
        }
        return BudgetRobot(composeRule)
    }
}
