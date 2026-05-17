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
    fun setBudgetForCategoryPersistsAndHidesEmptyCallout() {
        seedDefaultSetup()
        onBudget()
            .assertEmptyCalloutVisible()
            .tapCategory("Food")
            .typeDigits("100")
            .assertAmountShown("100")
            .tapCommit()
            .dismiss()
            .assertEmptyCalloutHidden()
            .tapCategory("Food")
            .assertAmountShown("100")
    }
}
