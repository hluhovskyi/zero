package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement

class TransactionEditRobot(private val composeRule: ComposeTestRule) {

    fun fillExpense(amount: String, category: String, account: String): TransactionEditRobot {
        composeRule.onNodeWithTag("TransactionEdit.amountField")
            .performClick()
            .performTextReplacement(amount)
        composeRule.onNodeWithText(category).performClick()
        composeRule.onNodeWithText("ACCOUNT").performClick()
        composeRule.onAllNodesWithText(account).onLast().performClick()
        return this
    }

    fun save(): TransactionsRobot {
        composeRule.onNodeWithContentDescription("Save Transaction").performClick()
        // Wait for the edit screen to be dismissed (save is async on IO; navigation
        // on Main fires only after the DB write completes).
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("Save Transaction")
                .fetchSemanticsNodes().isEmpty()
        }
        return TransactionsRobot(composeRule)
    }
}
