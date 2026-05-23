package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class BudgetRobot(private val composeRule: ComposeTestRule) {

    fun open(): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Budget").fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText("Budget").filter(hasClickAction()).onFirst().performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("Food & Drink").fetchSemanticsNodes().isNotEmpty()
            }
        }
        return this
    }

    fun assertEmptyCalloutVisible(): BudgetRobot {
        composeRule.onNodeWithText("No budget for", substring = true).assertIsDisplayed()
        return this
    }

    fun assertEmptyCalloutHidden(): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("No budget for", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            }
        }
        return this
    }

    fun tapCategory(name: String): BudgetInlineNumpadRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText(name).onFirst().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Delete")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        return BudgetInlineNumpadRobot(composeRule)
    }
}
