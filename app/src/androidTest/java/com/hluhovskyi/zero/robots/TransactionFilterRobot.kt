package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class TransactionFilterRobot(private val composeRule: ComposeTestRule) {

    fun selectType(label: String): TransactionFilterRobot {
        composeRule.onNodeWithText(label).performClick()
        return this
    }

    fun apply(): TransactionsRobot {
        composeRule.apply {
            onNodeWithText("Apply filters").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Filter").fetchSemanticsNodes().isNotEmpty() &&
                    onAllNodesWithText("Apply filters").fetchSemanticsNodes().isEmpty()
            }
        }
        return TransactionsRobot(composeRule)
    }
}
