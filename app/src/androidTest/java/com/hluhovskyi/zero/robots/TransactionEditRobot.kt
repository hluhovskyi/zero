package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class TransactionEditRobot(private val composeRule: ComposeTestRule) {

    fun fillExpense(amount: String, category: String, account: String): TransactionEditRobot {
        composeRule.apply {
            // Tap the amount to open the inline keypad, then tap each character.
            // The amount label is itself clickable and can share text with a key once digits
            // are typed (e.g. amount "4" vs the "4" key); the keypad renders below the label,
            // so onLast() reliably picks the key.
            onNodeWithTag("TransactionEdit.amountField").performClick()
            amount.forEach { ch ->
                onAllNodesWithText(ch.toString()).filter(hasClickAction()).onLast().performClick()
            }
            onNodeWithText(category).performClick()
            onNodeWithText("ACCOUNT").performClick()
            onAllNodesWithText(account).onLast().performClick()
        }
        return this
    }

    fun assertCurrencySymbol(symbol: String): TransactionEditRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(symbol).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText(symbol).assertCountEquals(1)
        }
        return this
    }

    fun openCurrencyPicker(currentSymbol: String): TransactionEditRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(currentSymbol).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(currentSymbol).performClick()
        }
        return this
    }

    fun pickCurrencyByName(name: String): TransactionEditRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Search currencies…").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(name).performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Search currencies…").fetchSemanticsNodes().isEmpty()
            }
        }
        return this
    }

    fun save(): TransactionsRobot {
        composeRule.apply {
            onNodeWithContentDescription("Save Transaction").performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithContentDescription("Save Transaction")
                    .fetchSemanticsNodes().isEmpty()
            }
        }
        return TransactionsRobot(composeRule)
    }
}
