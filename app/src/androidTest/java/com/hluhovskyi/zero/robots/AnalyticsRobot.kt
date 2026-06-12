package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class AnalyticsRobot(private val composeRule: ComposeTestRule) {

    fun assertVisible(): AnalyticsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Net cash flow").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Net cash flow").assertIsDisplayed()
        }
        return this
    }

    fun assertCategoryVisible(name: String): AnalyticsRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText(name)[0].assertIsDisplayed()
        }
        return this
    }

    fun openSeeAllCategories(): CategoriesRobot {
        composeRule.apply {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("See all", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText("See all", substring = true)[0].performClick()
        }
        return CategoriesRobot(composeRule)
    }
}
