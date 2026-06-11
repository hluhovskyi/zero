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
    fun importedHistoricalTransactionAppearsLive() {
        seedExpenses()
        onTransactions().assertHasExpense(amount = "42")
        // A historical-dated insert (like an import) while the list is already attached.
        seedHistoricalExpense()
        onTransactions().assertHasExpense(amount = "137")
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
    fun filterSummaryCardShowsCorrectAggregate() {
        // Two USD expenses (42 + 99). Filtering to Expenses surfaces the summary card:
        // no-income branch → Spent 141, Avg 70.5, Largest 99 over the complete filtered set.
        seedExpenses()
        onTransactions()
            .openFilter()
            .selectType("Expenses")
            .apply()
            .assertFilterSummaryCount("2 transactions")
            .assertFilterSummaryStat("SPENT", "141")
            .assertFilterSummaryStat("AVG", "70.5")
            .assertFilterSummaryStat("LARGEST", "99")
            .assertShowBreakdownVisible()
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
    fun foreignCurrencyExpenseShowsConversionCard() {
        seedDefaultSetup()
        onTransactions()
            .tapAddTransaction()
            .openCurrencyPicker(currentSymbol = "$")
            .pickCurrencyByName("Euro")
            .assertConversionVisible(convertsToCurrency = "US Dollar")
    }

    @Test
    fun crossCurrencyTransferShowsFromToAmountsAndRate() {
        seedFxAccounts()
        onTransactions()
            .tapAddTransaction()
            .switchToTransfer()
            .selectToAccount("Revolut")
            .assertTransferConversionVisible()
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

    // TODO(#322): re-enable once the M3 over-budget tab dot renders again — the dot regressed in
    //  the Material 3 migration (unrelated to this feature). https://github.com/hluhovskyi/zero/issues/322
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

    @Test
    fun analyticsTabShowsCategoryBreakdownAndBacksFromCategories() {
        // Two Food expenses (42 + 99) → the Analytics hub breaks them down by category,
        // "See all categories" opens the existing Categories screen, and its back button returns.
        seedExpenses()
        onTransactions()
            .assertHasExpense(amount = "42")
            .openAnalytics()
            .assertVisible()
            .assertCategoryVisible("Food")
            .openSeeAllCategories()
            .assertVisible()
            .goBack()
            .assertVisible()
    }
}
