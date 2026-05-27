package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput

class TransactionsRobot(private val composeRule: ComposeTestRule) {

    fun assertWelcomeScreenVisible(): TransactionsRobot {
        composeRule.onNodeWithText("Your finances,", substring = true).assertIsDisplayed()
        return this
    }

    fun tapAddTransaction(): TransactionEditRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Add transaction")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Add transaction").performClick()
        }
        return TransactionEditRobot(composeRule)
    }

    fun assertHasExpense(amount: String): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(amount, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText(amount, substring = true)[0].assertIsDisplayed()
        }
        return this
    }

    fun openFilter(): TransactionFilterRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Filter").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Filter").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Apply filters").fetchSemanticsNodes().isNotEmpty()
            }
        }
        return TransactionFilterRobot(composeRule)
    }

    fun assertFilterChipVisible(label: String): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(label).assertIsDisplayed()
        }
        return this
    }

    fun assertCategoryNotVisible(name: String): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(name).fetchSemanticsNodes().isEmpty()
            }
            onAllNodesWithText(name).assertCountEquals(0)
        }
        return this
    }

    fun longPressTransaction(amount: String): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(amount, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText(amount, substring = true)[0].performTouchInput { longClick() }
        }
        return this
    }

    fun tapTransaction(amount: String): TransactionsRobot {
        composeRule.onAllNodesWithText(amount, substring = true)[0].performClick()
        return this
    }

    fun assertSelectionCount(count: Int): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("$count selected").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("$count selected").assertIsDisplayed()
        }
        return this
    }

    fun assertDuplicateVisible(): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Duplicate").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Duplicate").assertIsDisplayed()
        }
        return this
    }

    fun assertDuplicateNotVisible(): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Duplicate").fetchSemanticsNodes().isEmpty()
            }
            onAllNodesWithContentDescription("Duplicate").assertCountEquals(0)
        }
        return this
    }

    fun deleteSelected(): TransactionsRobot {
        composeRule.apply {
            onNodeWithContentDescription("More options").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Delete").performClick()
        }
        return this
    }

    fun assertAmountNotVisible(amount: String): TransactionsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(amount, substring = true).fetchSemanticsNodes().isEmpty()
            }
            onAllNodesWithText(amount, substring = true).assertCountEquals(0)
        }
        return this
    }
}
