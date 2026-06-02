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

    fun assertBudgetTabAlertVisible(): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Over budget").fetchSemanticsNodes().isNotEmpty()
            }
        }
        return this
    }

    fun assertBudgetTabAlertHidden(): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Over budget").fetchSemanticsNodes().isEmpty()
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

    fun assertOverBudgetActionsVisible(): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Reallocate").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Reallocate").assertIsDisplayed()
            onNodeWithText("Increase").assertIsDisplayed()
        }
        return this
    }

    fun assertCategoryLeft(amount: String): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("$amount left", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("$amount left").assertIsDisplayed()
        }
        return this
    }

    fun assertCategoryOver(amount: String): BudgetRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("$amount over", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("$amount over").assertIsDisplayed()
        }
        return this
    }

    fun tapReallocate(): BudgetOverReallocateRobot {
        composeRule.apply {
            onAllNodesWithText("Reallocate").filter(hasClickAction()).onFirst().performClick()
            waitForIdle()
        }
        return BudgetOverReallocateRobot(composeRule)
    }

    fun tapIncrease(): BudgetOverIncreaseRobot {
        composeRule.apply {
            onAllNodesWithText("Increase").filter(hasClickAction()).onFirst().performClick()
            waitForIdle()
        }
        return BudgetOverIncreaseRobot(composeRule)
    }

    fun goToHome(): TransactionsRobot {
        composeRule.apply {
            onAllNodesWithText("Home").filter(hasClickAction()).onFirst().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Add transaction").fetchSemanticsNodes().isNotEmpty()
            }
        }
        return TransactionsRobot(composeRule)
    }
}
