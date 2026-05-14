package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class TransactionsRobot(private val composeRule: ComposeTestRule) {

    fun assertWelcomeScreenVisible(): TransactionsRobot {
        composeRule.onNodeWithText("Your finances,", substring = true).assertIsDisplayed()
        return this
    }

    fun tapAddTransaction(): TransactionEditRobot {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Add transaction")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Add transaction").performClick()
        return TransactionEditRobot(composeRule)
    }

    fun assertHasExpense(amount: String): TransactionsRobot {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(amount, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(amount, substring = true)[0].assertIsDisplayed()
        return this
    }
}
