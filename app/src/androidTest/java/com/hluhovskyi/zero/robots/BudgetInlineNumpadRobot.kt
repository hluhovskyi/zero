package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

class BudgetInlineNumpadRobot(private val composeRule: ComposeTestRule) {

    fun typeDigits(digits: String): BudgetInlineNumpadRobot {
        digits.forEach { c -> tapKey(c.toString()) }
        return this
    }

    fun tapKey(key: String): BudgetInlineNumpadRobot {
        composeRule.onAllNodesWithText(key).filter(hasClickAction()).onFirst().performClick()
        return this
    }

    fun assertAmountShown(amount: String): BudgetInlineNumpadRobot {
        // The displayed amount-text node sits inside the numpad header, identical text appears
        // only there once digits have been entered (numpad keys are separate single-digit nodes).
        composeRule.onAllNodesWithText(amount)
            .filter(hasClickAction().not())
            .onFirst()
            .assertIsDisplayed()
        return this
    }

    fun tapCommit(): BudgetInlineNumpadRobot {
        composeRule.onNodeWithTag("Budget.inlineNumpad.commit").performClick()
        composeRule.waitForIdle()
        return this
    }

    fun dismiss(): BudgetRobot {
        composeRule.apply {
            onNodeWithTag("Budget.inlineNumpad.scrim").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Delete").fetchSemanticsNodes().isEmpty()
            }
        }
        return BudgetRobot(composeRule)
    }
}

// Local extension since SemanticsMatcher.not() is not in the public test API on older versions.
private operator fun androidx.compose.ui.test.SemanticsMatcher.not(): androidx.compose.ui.test.SemanticsMatcher =
    androidx.compose.ui.test.SemanticsMatcher("not(${this.description})") { node ->
        !matches(node)
    }
