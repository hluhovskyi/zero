package com.hluhovskyi.zero.testbridge

interface DatabaseTestBridge {
    suspend fun clearData()

    /**
     * Re-seeds the default preset categories and accounts (Food & Drink, Transport, ...).
     * Production seeds these on activity attach; tests that bypass the activity for setup
     * must call this after [clearData] to restore the fresh-install baseline.
     */
    suspend fun seedPresets()

    suspend fun seedDefaultSetup()

    /**
     * Sets up an over-budget scenario for the *current* month:
     * - Wallet account
     * - Food & Drink budget = $50, Transport budget = $200 (both from default presets)
     * - One $100 expense on Food & Drink in the current month
     *
     * Used by Phase 5 (over-budget Reallocate / Increase) e2e tests.
     */
    suspend fun seedBudgetOverScenario()

    /**
     * Seeds a Wallet account, a "Food" expense category, and two expenses
     * ($42 and $99) so the transaction list has selectable rows without going
     * through the (separately-tested) UI creation flow. Used by the
     * batch-select removal e2e test.
     */
    suspend fun seedExpenses()
}
