package com.hluhovskyi.zero.testbridge

interface DatabaseTestBridge {
    suspend fun clearData()
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
}
