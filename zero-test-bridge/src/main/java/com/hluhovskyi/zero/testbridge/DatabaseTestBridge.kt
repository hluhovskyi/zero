package com.hluhovskyi.zero.testbridge

interface DatabaseTestBridge {
    /**
     * Resets to the fresh-install baseline: clears all tables and re-seeds the default
     * preset categories and accounts (Food & Drink, Transport, ...). Production seeds those
     * on activity attach; this bridge restores them itself so test bodies that bypass the
     * activity for setup still start from the same baseline.
     */
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

    /**
     * Seeds a Wallet account, a "Food" expense category, and two expenses
     * ($42 and $99) so the transaction list has selectable rows without going
     * through the (separately-tested) UI creation flow. Used by the
     * batch-select removal e2e test.
     */
    suspend fun seedExpenses()

    /**
     * Seeds two accounts in different currencies — "Wallet" (USD) and "Revolut" (EUR) — plus a
     * bootstrap expense so the app lands on Transactions, not Welcome. Used by the FX e2e tests to
     * reach the foreign-currency conversion UI (expense currency ≠ account, and cross-currency
     * transfer) without hand-driving account creation.
     */
    suspend fun seedFxAccounts()
}
