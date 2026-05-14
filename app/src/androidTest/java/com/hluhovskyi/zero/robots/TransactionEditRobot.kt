package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
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
        // After the dropdown opens, both the SelectorCard's merged text and the dropdown
        // item contain the account name — pick [1] (the dropdown item, SelectorCard is [0]).
        composeRule.onAllNodesWithText(account)[1].performClick()
        return this
    }

    fun save(): TransactionsRobot {
        composeRule.onNodeWithContentDescription("Save Transaction").performClick()
        return TransactionsRobot(composeRule)
    }
}
