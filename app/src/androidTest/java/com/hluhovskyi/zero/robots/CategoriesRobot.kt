package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class CategoriesRobot(private val composeRule: ComposeTestRule) {

    fun assertVisible(): CategoriesRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Categories").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Categories").assertIsDisplayed()
        }
        return this
    }

    fun goBack(): AnalyticsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Back").performClick()
        }
        return AnalyticsRobot(composeRule)
    }
}
