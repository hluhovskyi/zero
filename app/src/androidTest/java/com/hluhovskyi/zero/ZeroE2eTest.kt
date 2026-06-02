package com.hluhovskyi.zero

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZeroE2eTest : BaseE2eTest() {

    @Test
    fun freshInstallShowsWelcomeScreen() {
        onTransactions().assertWelcomeScreenVisible()
    }

    @Test
    fun addExpenseAppearsInTransactionList() {
        seedDefaultSetup()
        onTransactions()
            .tapAddTransaction()
            .fillExpense(amount = "42", category = "Food", account = "Wallet")
            .save()
            .assertHasExpense(amount = "42")
    }

    @Test
    fun batchSelectRemovesSelectedTransactions() {
        seedExpenses()
        onTransactions()
            .assertHasExpense(amount = "42")
            .assertHasExpense(amount = "99")
            .longPressTransaction(amount = "42")
            .assertSelectionCount(1)
            .assertDuplicateVisible()
            .tapTransaction(amount = "99")
            .assertSelectionCount(2)
            .assertDuplicateNotVisible()
            .deleteSelected()
            .assertAmountNotVisible("42")
            .assertAmountNotVisible("99")
    }

    @Test
    fun applyTypeFilterUpdatesListAcrossNavigation() {
        seedDefaultSetup()
        onTransactions()
            .openFilter()
            .selectType("Income")
            .apply()
            .assertFilterChipVisible("Income")
            .assertCategoryNotVisible("Food")
    }

    @Test
    fun pickingCurrencyInPickerUpdatesTransactionEditChip() {
        seedDefaultSetup()
        onTransactions()
            .tapAddTransaction()
            .assertCurrencySymbol("$")
            .openCurrencyPicker(currentSymbol = "$")
            .pickCurrencyByName("Australian Dollar")
            .assertCurrencySymbol("A$")
    }

    @Test
    fun setBudgetForCategoryPersistsAndHidesEmptyCallout() {
        seedDefaultSetup()
        onBudget()
            .assertEmptyCalloutVisible()
            .tapCategory("Food & Drink")
            .typeDigits("100")
            .assertAmountShown("100")
            .tapCommit()
            .dismiss()
            .assertEmptyCalloutHidden()
            .tapCategory("Food & Drink")
            .assertAmountShown("100")
    }

    @Test
    fun reallocateMovesAmountFromSourceToTargetAndClearsOverBudget() {
        seedBudgetOverScenario()
        onBudget()
            .assertCategoryOver("50.00")
            .assertOverBudgetActionsVisible()
            .tapReallocate()
            .assertSourceCovers("Transport")
            .selectSource("Transport")
            .confirmMove()
            .assertCategoryLeft("0.00")
            .assertCategoryLeft("150.00")
    }

    @Test
    fun overBudgetShowsDotOnBudgetTabAndClearsWhenRaised() {
        seedBudgetOverScenario()
        onBudget()
            .assertBudgetTabAlertVisible()
            .tapIncrease()
            .pickSuggestion("+50.00")
            .confirm()
            .assertBudgetTabAlertHidden()
    }

    @Test
    fun increaseGrowsTargetBudgetOnlyAndClearsOverBudget() {
        seedBudgetOverScenario()
        onBudget()
            .assertCategoryOver("50.00")
            .tapIncrease()
            .assertSuggestionVisible("+50.00")
            .pickSuggestion("+50.00")
            .confirm()
            .assertCategoryLeft("0.00")
            .assertCategoryLeft("200.00")
    }
}
