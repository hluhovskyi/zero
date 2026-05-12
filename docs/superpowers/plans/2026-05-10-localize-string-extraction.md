# String Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract all hardcoded UI strings from ViewProvider/Composable files into `res/values/strings.xml` in `zero-core` and `app` modules.

**Architecture:** Standard Android resources. `stringResource(R.string.xxx)` / `pluralStringResource(R.plurals.xxx, count, count)` in Composables. Four `AccountCategory` extension properties become `@Composable` functions. Two private helper functions in `TransactionEditTransferViewProvider` become `@Composable` to call `stringResource` internally.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.ui.res.stringResource`, `androidx.compose.ui.res.pluralStringResource`

---

### Task 1: Populate zero-core/strings.xml

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] Replace the entire file with the complete strings list below:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Existing filter chip plurals (from PR #99) -->
    <plurals name="filter_chip_categories">
        <item quantity="one">%d category</item>
        <item quantity="other">%d categories</item>
    </plurals>
    <plurals name="filter_chip_accounts">
        <item quantity="one">%d account</item>
        <item quantity="other">%d accounts</item>
    </plurals>

    <!-- Accounts -->
    <string name="account_total_net_worth">TOTAL NET WORTH</string>
    <string name="account_my_accounts">My Accounts</string>
    <string name="account_add">Add Account</string>
    <string name="account_header_cash">CASH</string>
    <string name="account_header_bank">BANK</string>
    <string name="account_header_credit_cards">CREDIT CARDS</string>
    <string name="account_header_digital_wallets">DIGITAL WALLETS</string>
    <string name="account_header_crypto">CRYPTO</string>
    <string name="account_header_other">OTHER</string>

    <!-- Account Detail -->
    <string name="account_detail_in_this_month">IN THIS MONTH</string>
    <string name="account_detail_out_this_month">OUT THIS MONTH</string>
    <string name="account_detail_transactions">TRANSACTIONS</string>

    <!-- Account Edit -->
    <string name="account_edit_title">New Account</string>
    <string name="account_edit_opening_balance_label">OPENING BALANCE</string>
    <string name="account_edit_name_label">Account Name</string>
    <string name="account_edit_type_label">Type</string>
    <string name="account_edit_save_description">Save account</string>
    <string name="account_edit_save">Save</string>
    <string name="account_type_cash">Cash</string>
    <string name="account_type_bank">Bank</string>
    <string name="account_type_credit_cards">Credit Cards</string>
    <string name="account_type_digital_wallets">Digital Wallets</string>
    <string name="account_type_crypto">Crypto</string>
    <string name="account_type_other">Other</string>
    <string name="account_name_placeholder_cash">e.g. Wallet</string>
    <string name="account_name_placeholder_bank">e.g. Chase Sapphire</string>
    <string name="account_name_placeholder_credit_cards">e.g. Amex Gold</string>
    <string name="account_name_placeholder_digital_wallets">e.g. PayPal</string>
    <string name="account_name_placeholder_crypto">e.g. Bitcoin</string>
    <string name="account_name_placeholder_other">e.g. Savings</string>
    <string name="account_detail_label_credit_cards">Last 4 / Nickname</string>
    <string name="account_detail_label_bank">Account Number / Type</string>
    <string name="account_detail_label_digital_wallets">Account Details</string>
    <string name="account_detail_label_crypto">Wallet Address</string>
    <string name="account_detail_label_other">Details</string>
    <string name="account_detail_placeholder_credit_cards">••• 1209</string>
    <string name="account_detail_placeholder_bank">Checking</string>
    <string name="account_detail_placeholder_digital_wallets">user@example.com</string>
    <string name="account_detail_placeholder_crypto">bc1q…</string>

    <!-- Categories -->
    <string name="category_title">Categories</string>
    <string name="category_unused_this_month">Unused this month</string>
    <string name="category_no_activity">No activity</string>
    <string name="category_percent_of_total">%1$d%% of total</string>
    <plurals name="category_transaction_count">
        <item quantity="one">%d transaction</item>
        <item quantity="other">%d transactions</item>
    </plurals>

    <!-- Category Detail -->
    <string name="category_detail_more_options_description">More options</string>
    <string name="category_detail_edit">Edit category</string>
    <string name="category_detail_add_transaction">Add transaction</string>
    <string name="category_detail_stat_transactions">TRANSACTIONS</string>
    <string name="category_detail_stat_avg_per_tx">AVG PER TX</string>
    <string name="category_detail_stat_largest">LARGEST</string>

    <!-- Category Edit -->
    <string name="category_edit_title">Add Category</string>
    <string name="category_edit_save_description">Save category</string>
    <string name="category_edit_save">Save</string>
    <string name="category_edit_name_label">CATEGORY NAME</string>
    <string name="category_edit_name_placeholder">e.g. Groceries</string>

    <!-- Pickers -->
    <string name="category_picker_search_placeholder">Search categories…</string>
    <string name="currency_picker_search_placeholder">Search currencies…</string>
    <string name="icon_picker_search_placeholder">Search icons…</string>
    <string name="icon_picker_no_results">No icons match \"%1$s\"</string>

    <!-- Settings -->
    <string name="settings_title">More</string>
    <string name="settings_section_preferences">PREFERENCES</string>
    <string name="settings_primary_currency">Primary Currency</string>
    <string name="settings_currency_loading">Loading…</string>
    <string name="settings_section_data">DATA</string>
    <string name="settings_import_data">Import Data</string>
    <string name="settings_import_data_description">Migrate history from other apps</string>
    <string name="settings_export_data">Export Data</string>
    <string name="settings_export_data_description">Save a backup of your data</string>
    <string name="settings_section_security">SECURITY</string>
    <string name="settings_biometric_lock">Biometric Lock</string>
    <string name="settings_biometric_lock_description">Face ID or Fingerprint required on open</string>
    <string name="settings_backup_saved">Backup saved</string>
    <string name="settings_export_failed">Export failed: %1$s</string>

    <!-- Import -->
    <string name="import_back_description">Back</string>
    <string name="import_nothing_to_import_title">Nothing to Import</string>
    <string name="import_up_to_date_message">Your data is already up to date.</string>
    <string name="import_all_synced_message">All categories, accounts, and transactions from this backup are already in the app.</string>
    <string name="import_done">Done</string>
    <string name="import_source_selection_title">Import Data</string>
    <string name="import_source_selection_subtitle">Choose a data source. Zero will preview what will be imported before anything is saved.</string>
    <string name="import_source_zero_backup_title">Zero Backup</string>
    <string name="import_source_zero_backup_description">Restore from a .zero backup file</string>
    <string name="import_source_zenmoney_title">ZenMoney CSV</string>
    <string name="import_source_zenmoney_description">Import transactions from a ZenMoney export</string>
    <string name="import_source_more_coming_soon">More sources coming soon</string>
    <string name="import_accounts_review_title">Review Accounts</string>
    <string name="import_accounts_review_info">%1$d ACCOUNTS · %2$d TRANSACTIONS</string>
    <string name="import_accounts_review_continue">Continue</string>
    <string name="import_accounts_review_tx_count">%d tx</string>
    <string name="import_categories_review_title">Review Categories</string>
    <string name="import_categories_review_info">%1$d CATEGORIES</string>
    <string name="import_categories_review_continue">Continue</string>
    <plurals name="import_categories_review_tx_count">
        <item quantity="one">%d transaction</item>
        <item quantity="other">%d transactions</item>
    </plurals>
    <string name="import_transactions_preview_title">Review Transactions</string>
    <string name="import_transactions_preview_info">%1$d TRANSACTIONS</string>
    <string name="import_transactions_preview_import">Import %1$d Transactions</string>

    <!-- Transaction List -->
    <string name="transaction_empty_state">No transactions found</string>
    <string name="transaction_delete">Delete</string>
    <string name="transaction_filter_icon_description">Filter</string>
    <string name="transaction_clear_all">Clear all</string>
    <string name="transaction_remove_filter_description">Remove filter</string>

    <!-- Transaction Filter Sheet -->
    <string name="filter_title">Filter</string>
    <string name="filter_reset">Reset</string>
    <string name="filter_section_period">Period</string>
    <string name="filter_section_type">Type</string>
    <string name="filter_section_categories">Categories</string>
    <string name="filter_all_categories">All categories</string>
    <string name="filter_section_accounts">Accounts</string>
    <string name="filter_all_accounts">All accounts</string>
    <string name="filter_apply">Apply filters</string>

    <!-- Transaction Edit -->
    <string name="transaction_edit_title">Edit Transaction</string>
    <string name="transaction_new_title">New Transaction</string>
    <string name="transaction_edit_more_options_description">More options</string>
    <string name="transaction_edit_delete">Delete</string>
    <string name="transaction_type_expense">Expense</string>
    <string name="transaction_type_income">Income</string>
    <string name="transaction_type_transfer">Transfer</string>
    <string name="transaction_edit_save">Save Transaction</string>
    <string name="transaction_edit_date_label">Date</string>
    <string name="transaction_edit_account_label">Account</string>
    <string name="transaction_edit_amount_label">Amount</string>
    <string name="transaction_edit_category_label">Category</string>
    <string name="transaction_edit_edit_categories_description">Edit categories</string>
    <string name="transaction_edit_currency_label">Currency</string>
    <string name="transaction_edit_rate_label">Rate</string>
    <string name="transaction_edit_show_all_categories_description">Show all categories</string>
    <string name="transaction_edit_all_categories">All</string>

    <!-- Transaction Edit Transfer -->
    <string name="transfer_edit_rate_label">RATE</string>
    <string name="transfer_edit_change">CHANGE</string>
    <string name="transfer_edit_enter_rate">Enter rate</string>
    <string name="transfer_edit_destination_amount">DESTINATION AMOUNT</string>
    <string name="transfer_edit_from_label">From</string>
    <string name="transfer_edit_to_label">To</string>
    <string name="transfer_edit_swap_description">Swap accounts</string>
    <string name="transfer_receives_amount">Receives %1$s</string>
    <string name="transfer_receives_with_symbol">Receives %1$s%2$s</string>
    <string name="transfer_receives_with_rate">Receives %1$s%2$s · 1 %3$s = %4$s %5$s</string>
</resources>
```

- [ ] Commit:

```bash
git add zero-core/src/main/res/values/strings.xml
git commit -m "feat: add all localization string resources to zero-core"
```

---

### Task 2: TransactionViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource` (already has `pluralStringResource`). Make these replacements:

| Old | New |
|-----|-----|
| `"No transactions found"` | `stringResource(R.string.transaction_empty_state)` |
| `"Delete"` | `stringResource(R.string.transaction_delete)` |
| `contentDescription = "Filter"` | `contentDescription = stringResource(R.string.transaction_filter_icon_description)` |
| `"Clear all"` | `stringResource(R.string.transaction_clear_all)` |
| `contentDescription = "Remove filter"` | `contentDescription = stringResource(R.string.transaction_remove_filter_description)` |

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "feat: extract hardcoded strings from TransactionViewProvider"
```

---

### Task 3: TransactionFilterSheet.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilterSheet.kt`

- [ ] Add import `androidx.compose.ui.res.stringResource`. Make these replacements:

| Old | New |
|-----|-----|
| `title = "Filter"` | `title = stringResource(R.string.filter_title)` |
| `text = "Reset"` | `text = stringResource(R.string.filter_reset)` |
| `FilterSection(label = "Period")` | `FilterSection(label = stringResource(R.string.filter_section_period))` |
| `FilterSection(label = "Type")` | `FilterSection(label = stringResource(R.string.filter_section_type))` |
| `FilterSection(label = "Categories")` | `FilterSection(label = stringResource(R.string.filter_section_categories))` |
| `allLabel = "All categories"` (in the categories FilterSection block) | `allLabel = stringResource(R.string.filter_all_categories)` |
| `FilterSection(label = "Accounts")` | `FilterSection(label = stringResource(R.string.filter_section_accounts))` |
| `allLabel = "All accounts"` (in the accounts FilterSection block) | `allLabel = stringResource(R.string.filter_all_accounts)` |
| `text = "Apply filters"` | `text = stringResource(R.string.filter_apply)` |

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilterSheet.kt
git commit -m "feat: extract hardcoded strings from TransactionFilterSheet"
```

---

### Task 4: Transaction edit common components (6 files)

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditAccountSelect.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditAmountTextField.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCurrencySelect.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditRateTextField.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryScrollRow.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt`

- [ ] In each file, add `import androidx.compose.ui.res.stringResource` and make the following replacements:

**TransactionEditAccountSelect.kt** — replace `"Account"` (the `text` parameter) with `stringResource(R.string.transaction_edit_account_label)`

**TransactionEditAmountTextField.kt** — replace `"Amount"` with `stringResource(R.string.transaction_edit_amount_label)`

**TransactionEditCurrencySelect.kt** — replace `"Currency"` with `stringResource(R.string.transaction_edit_currency_label)`

**TransactionEditRateTextField.kt** — replace `"Rate"` with `stringResource(R.string.transaction_edit_rate_label)`

**TransactionEditCategorySelect.kt:**
- Replace `"Category"` (label) with `stringResource(R.string.transaction_edit_category_label)`
- Replace `contentDescription = "Edit categories"` with `contentDescription = stringResource(R.string.transaction_edit_edit_categories_description)`

**CategoryScrollRow.kt:**
- Replace `contentDescription = "Show all categories"` with `contentDescription = stringResource(R.string.transaction_edit_show_all_categories_description)`
- Replace `text = "All"` with `text = stringResource(R.string.transaction_edit_all_categories)`

**TransactionEditExpenseIncomeViewProvider.kt:**
- Replace `label = "Account"` (the DatePickerCard / account row label) with `label = stringResource(R.string.transaction_edit_account_label)`

- [ ] Commit:

```bash
git add \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditAccountSelect.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditAmountTextField.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCurrencySelect.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditRateTextField.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryScrollRow.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt
git commit -m "feat: extract hardcoded strings from transaction edit common components"
```

---

### Task 5: TransactionEditViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
// Old
title = if (state.isEditMode) "Edit Transaction" else "New Transaction",

// New
title = if (state.isEditMode) stringResource(R.string.transaction_edit_title) else stringResource(R.string.transaction_new_title),
```

```kotlin
// Old
contentDescription = "More options",

// New
contentDescription = stringResource(R.string.transaction_edit_more_options_description),
```

```kotlin
// Old
text = "Delete",

// New
text = stringResource(R.string.transaction_edit_delete),
```

```kotlin
// Old (labelMapping lambda)
TransactionEditType.EXPENSE -> "Expense"
TransactionEditType.INCOME -> "Income"
TransactionEditType.TRANSFER -> "Transfer"

// New
TransactionEditType.EXPENSE -> stringResource(R.string.transaction_type_expense)
TransactionEditType.INCOME -> stringResource(R.string.transaction_type_income)
TransactionEditType.TRANSFER -> stringResource(R.string.transaction_type_transfer)
```

```kotlin
// Old
Text(text = "Save Transaction")

// New
Text(text = stringResource(R.string.transaction_edit_save))
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt
git commit -m "feat: extract hardcoded strings from TransactionEditViewProvider"
```

---

### Task 6: TransactionEditTransferViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewProvider.kt`

The two private helper functions `formatDefaultPillText` and `computeTargetFromRate` produce localized strings but are not `@Composable`. Convert them to `@Composable` functions so they can call `stringResource`.

- [ ] Add imports:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
```

- [ ] Replace `DatePickerCard(label = "Date", ...)` with `DatePickerCard(label = stringResource(R.string.transaction_edit_date_label), ...)`

- [ ] Replace the hardcoded `"RATE"`, `"CHANGE"`, `"Enter rate"`, `"DESTINATION AMOUNT"`, `"CHANGE"` labels in `RateModePill`:

```kotlin
// Old
text = "RATE"
// New
text = stringResource(R.string.transfer_edit_rate_label)

// Old
text = "CHANGE"   // appears twice (CustomRate and CustomAmount branches)
// New
text = stringResource(R.string.transfer_edit_change)

// Old
text = "Enter rate"
// New
text = stringResource(R.string.transfer_edit_enter_rate)

// Old
text = "DESTINATION AMOUNT"
// New
text = stringResource(R.string.transfer_edit_destination_amount)
```

- [ ] Replace `"From"`, `"To"`, `"Swap accounts"` in `AccountSelectorsWithSwap`:

```kotlin
label = stringResource(R.string.transfer_edit_from_label)
label = stringResource(R.string.transfer_edit_to_label)
contentDescription = stringResource(R.string.transfer_edit_swap_description)
```

- [ ] Convert `formatDefaultPillText` to a `@Composable` function and update its string building:

```kotlin
@Composable
private fun formatDefaultPillText(
    amount: String,
    rate: Rate,
    sourceCurrencySymbol: String,
    targetCurrencySymbol: String,
): String {
    val sourceAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val targetAmount = sourceAmount.multiply(rate.value)
    val formattedTarget = amountFormat.format(targetAmount)

    return if (sourceCurrencySymbol == targetCurrencySymbol) {
        stringResource(R.string.transfer_receives_amount, formattedTarget)
    } else {
        val formattedRate = rate.value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        stringResource(
            R.string.transfer_receives_with_rate,
            targetCurrencySymbol,
            formattedTarget,
            sourceCurrencySymbol,
            formattedRate,
            targetCurrencySymbol,
        )
    }
}
```

- [ ] Convert `computeTargetFromRate` to a `@Composable` function:

```kotlin
@Composable
private fun computeTargetFromRate(
    sourceAmount: String,
    rateStr: String,
    targetCurrencySymbol: String,
): String {
    val amount = sourceAmount.toBigDecimalOrNull() ?: return ""
    val rate = rateStr.toBigDecimalOrNull() ?: return ""
    if (rate.compareTo(BigDecimal.ZERO) == 0) return ""
    val target = amount.multiply(rate)
    val formatted = amountFormat.format(target)
    return if (targetCurrencySymbol.isNotEmpty()) {
        stringResource(R.string.transfer_receives_with_symbol, targetCurrencySymbol, formatted)
    } else {
        stringResource(R.string.transfer_receives_amount, formatted)
    }
}
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewProvider.kt
git commit -m "feat: extract hardcoded strings from TransactionEditTransferViewProvider"
```

---

### Task 7: AccountViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`

The `displayName` extension property on `AccountCategory` calls happen inside `@Composable` functions, so convert it to a `@Composable` function.

- [ ] Add imports:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
```

- [ ] Replace these literals in the Composable functions directly:

```kotlin
// Old
text = "TOTAL NET WORTH"
// New
text = stringResource(R.string.account_total_net_worth)

// Old
text = "My Accounts"
// New
text = stringResource(R.string.account_my_accounts)

// Old
text = "Add Account"
// New
text = stringResource(R.string.account_add)
```

- [ ] Convert `displayName` extension property to `@Composable` function and update its call site:

```kotlin
// Old (property)
private val AccountCategory.displayName: String
    get() = when (this) {
        AccountCategory.CASH -> "CASH"
        AccountCategory.BANK -> "BANK"
        AccountCategory.CREDIT_CARDS -> "CREDIT CARDS"
        AccountCategory.DIGITAL_WALLETS -> "DIGITAL WALLETS"
        AccountCategory.CRYPTO -> "CRYPTO"
        AccountCategory.OTHER -> "OTHER"
    }

// New (@Composable function)
@Composable
private fun AccountCategory.displayName(): String = when (this) {
    AccountCategory.CASH -> stringResource(R.string.account_header_cash)
    AccountCategory.BANK -> stringResource(R.string.account_header_bank)
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_header_credit_cards)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_header_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_header_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_header_other)
}
```

- [ ] Update the call site in `CategoryHeader` from `category.displayName` to `category.displayName()`.

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt
git commit -m "feat: extract hardcoded strings from AccountViewProvider"
```

---

### Task 8: AccountDetailViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Replace:

```kotlin
label = "IN THIS MONTH"   →   label = stringResource(R.string.account_detail_in_this_month)
label = "OUT THIS MONTH"  →   label = stringResource(R.string.account_detail_out_this_month)
label = "TRANSACTIONS"    →   label = stringResource(R.string.account_detail_transactions)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt
git commit -m "feat: extract hardcoded strings from AccountDetailViewProvider"
```

---

### Task 9: AccountEditViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`

Four extension properties (`displayName`, `namePlaceholder`, `detailLabel`, `detailPlaceholder`) are used inside Composables. Convert all four to `@Composable` functions.

- [ ] Add imports:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
```

- [ ] Replace string literals in the main Composable body:

```kotlin
title = "New Account"              →  title = stringResource(R.string.account_edit_title)
label = "OPENING BALANCE"          →  label = stringResource(R.string.account_edit_opening_balance_label)
label = "Account Name"             →  label = stringResource(R.string.account_edit_name_label)
label = "Type"                     →  label = stringResource(R.string.account_edit_type_label)
contentDescription = "Save account" → contentDescription = stringResource(R.string.account_edit_save_description)
text = "Save"                      →  text = stringResource(R.string.account_edit_save)
```

- [ ] Convert all four extension properties to `@Composable` functions:

```kotlin
@Composable
private fun AccountCategory.displayName(): String = when (this) {
    AccountCategory.CASH -> stringResource(R.string.account_type_cash)
    AccountCategory.BANK -> stringResource(R.string.account_type_bank)
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_type_credit_cards)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_type_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_type_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_type_other)
}

@Composable
private fun AccountCategory.namePlaceholder(): String = when (this) {
    AccountCategory.CASH -> stringResource(R.string.account_name_placeholder_cash)
    AccountCategory.BANK -> stringResource(R.string.account_name_placeholder_bank)
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_name_placeholder_credit_cards)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_name_placeholder_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_name_placeholder_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_name_placeholder_other)
}

@Composable
private fun AccountCategory.detailLabel(): String = when (this) {
    AccountCategory.CASH -> ""
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_detail_label_credit_cards)
    AccountCategory.BANK -> stringResource(R.string.account_detail_label_bank)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_detail_label_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_detail_label_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_detail_label_other)
}

@Composable
private fun AccountCategory.detailPlaceholder(): String = when (this) {
    AccountCategory.CASH -> ""
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_detail_placeholder_credit_cards)
    AccountCategory.BANK -> stringResource(R.string.account_detail_placeholder_bank)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_detail_placeholder_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_detail_placeholder_crypto)
    AccountCategory.OTHER -> ""
}
```

- [ ] Update all call sites: `category.displayName` → `category.displayName()`, `category.namePlaceholder` → `category.namePlaceholder()`, `category.detailLabel` → `category.detailLabel()`, `category.detailPlaceholder` → `category.detailPlaceholder()`.

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt
git commit -m "feat: extract hardcoded strings from AccountEditViewProvider"
```

---

### Task 10: CategoryViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`

- [ ] Add imports:

```kotlin
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
```

- [ ] Make these replacements:

```kotlin
// Old
text = "Categories"
// New
text = stringResource(R.string.category_title)

// Old
text = "Unused this month"
// New
text = stringResource(R.string.category_unused_this_month)

// Old
text = "${spending.transactionCount} transaction${if (spending.transactionCount != 1) "s" else ""}"
// New
text = pluralStringResource(R.plurals.category_transaction_count, spending.transactionCount, spending.transactionCount)

// Old
text = "$percentOfTotal% of total"
// New
text = stringResource(R.string.category_percent_of_total, percentOfTotal)

// Old
text = "No activity"
// New
text = stringResource(R.string.category_no_activity)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt
git commit -m "feat: extract hardcoded strings from CategoryViewProvider"
```

---

### Task 11: CategoryDetailViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
contentDescription = "More options"  →  contentDescription = stringResource(R.string.category_detail_more_options_description)
text = "Edit category"               →  text = stringResource(R.string.category_detail_edit)
text = "Add transaction"             →  text = stringResource(R.string.category_detail_add_transaction)
label = "TRANSACTIONS"               →  label = stringResource(R.string.category_detail_stat_transactions)
label = "AVG PER TX"                 →  label = stringResource(R.string.category_detail_stat_avg_per_tx)
label = "LARGEST"                    →  label = stringResource(R.string.category_detail_stat_largest)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt
git commit -m "feat: extract hardcoded strings from CategoryDetailViewProvider"
```

---

### Task 12: CategoriesEditViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
title = "Add Category"               →  title = stringResource(R.string.category_edit_title)
contentDescription = "Save category" →  contentDescription = stringResource(R.string.category_edit_save_description)
text = "Save"                        →  text = stringResource(R.string.category_edit_save)
text = "CATEGORY NAME"               →  text = stringResource(R.string.category_edit_name_label)
text = "e.g. Groceries"              →  text = stringResource(R.string.category_edit_name_placeholder)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt
git commit -m "feat: extract hardcoded strings from CategoriesEditViewProvider"
```

---

### Task 13: Pickers — CategoryPickerViewProvider, CurrencyPickerViewProvider, IconAndColorPicker

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/picker/CategoryPickerViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/currencies/picker/CurrencyPickerViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconAndColorPicker.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource` to each file. Make these replacements:

**CategoryPickerViewProvider.kt:**
```kotlin
placeholder = "Search categories…"  →  placeholder = stringResource(R.string.category_picker_search_placeholder)
```

**CurrencyPickerViewProvider.kt:**
```kotlin
placeholder = "Search currencies…"  →  placeholder = stringResource(R.string.currency_picker_search_placeholder)
```

**IconAndColorPicker.kt:**
```kotlin
placeholder = "Search icons…"  →  placeholder = stringResource(R.string.icon_picker_search_placeholder)

// Old
text = "No icons match \"$query\""
// New
text = stringResource(R.string.icon_picker_no_results, query)
```

- [ ] Commit:

```bash
git add \
  zero-core/src/main/java/com/hluhovskyi/zero/categories/picker/CategoryPickerViewProvider.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/currencies/picker/CurrencyPickerViewProvider.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/icons/IconAndColorPicker.kt
git commit -m "feat: extract hardcoded strings from picker components"
```

---

### Task 14: SettingsViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
// Old
snackbarHostState.showSnackbar("Backup saved")
// New
snackbarHostState.showSnackbar(context.getString(R.string.settings_backup_saved))
```

Note: `showSnackbar` is called from a `LaunchedEffect`, not a `@Composable` slot, so it cannot use `stringResource()`. Capture the strings before the `LaunchedEffect` and reference them inside it:

```kotlin
// Add before LaunchedEffect
val backupSaved = stringResource(R.string.settings_backup_saved)
val exportFailedPrefix = stringResource(R.string.settings_export_failed)

// Inside LaunchedEffect
SettingsViewModel.ExportFeedback.Success ->
    snackbarHostState.showSnackbar(backupSaved)
is SettingsViewModel.ExportFeedback.Error ->
    snackbarHostState.showSnackbar(exportFailedPrefix.format(feedback.message))
```

Wait — `stringResource(R.string.settings_export_failed)` returns `"Export failed: %1$s"` which is a Java format string. Use `String.format`:
```kotlin
snackbarHostState.showSnackbar(String.format(exportFailedPrefix, feedback.message))
```

- [ ] Replace remaining literals:

```kotlin
MoreSection(title = "PREFERENCES")   →  MoreSection(title = stringResource(R.string.settings_section_preferences))
primaryText = "Primary Currency"     →  primaryText = stringResource(R.string.settings_primary_currency)
state.selectedCurrencyName.ifEmpty { "Loading…" }  →  state.selectedCurrencyName.ifEmpty { stringResource(R.string.settings_currency_loading) }
MoreSection(title = "DATA")          →  MoreSection(title = stringResource(R.string.settings_section_data))
primaryText = "Import Data"          →  primaryText = stringResource(R.string.settings_import_data)
secondaryText = "Migrate history from other apps"  →  secondaryText = stringResource(R.string.settings_import_data_description)
primaryText = "Export Data"          →  primaryText = stringResource(R.string.settings_export_data)
secondaryText = "Save a backup of your data"       →  secondaryText = stringResource(R.string.settings_export_data_description)
MoreSection(title = "SECURITY")      →  MoreSection(title = stringResource(R.string.settings_section_security))
primaryText = "Biometric Lock"       →  primaryText = stringResource(R.string.settings_biometric_lock)
secondaryText = "Face ID or Fingerprint required on open"  →  secondaryText = stringResource(R.string.settings_biometric_lock_description)
text = "More"                        →  text = stringResource(R.string.settings_title)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt
git commit -m "feat: extract hardcoded strings from SettingsViewProvider"
```

---

### Task 15: ImportViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
contentDescription = "Back"          →  contentDescription = stringResource(R.string.import_back_description)
text = "Nothing to Import"           →  text = stringResource(R.string.import_nothing_to_import_title)
text = "Your data is already up to date."  →  text = stringResource(R.string.import_up_to_date_message)
text = "All categories, accounts, and transactions from this backup are already in the app."  →  text = stringResource(R.string.import_all_synced_message)
text = "Done"                        →  text = stringResource(R.string.import_done)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt
git commit -m "feat: extract hardcoded strings from ImportViewProvider"
```

---

### Task 16: SourceSelectionViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
title = "Import Data"  →  title = stringResource(R.string.import_source_selection_title)
text = "Choose a data source. Zero will preview what will be imported before anything is saved."  →  text = stringResource(R.string.import_source_selection_subtitle)
"Zero Backup"          →  stringResource(R.string.import_source_zero_backup_title)
"Restore from a .zero backup file"  →  stringResource(R.string.import_source_zero_backup_description)
"ZenMoney CSV"         →  stringResource(R.string.import_source_zenmoney_title)
"Import transactions from a ZenMoney export"  →  stringResource(R.string.import_source_zenmoney_description)
text = "More sources coming soon"  →  text = stringResource(R.string.import_source_more_coming_soon)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt
git commit -m "feat: extract hardcoded strings from SourceSelectionViewProvider"
```

---

### Task 17: AccountsReviewViewProvider.kt + CategoriesReviewViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt`

- [ ] Add imports (`stringResource`, `pluralStringResource`) to each. Make these replacements:

**AccountsReviewViewProvider.kt:**
```kotlin
title = "Review Accounts"  →  title = stringResource(R.string.import_accounts_review_title)

// Old
text = "${state.accounts.size} ACCOUNTS · $totalTransactions TRANSACTIONS"
// New
text = stringResource(R.string.import_accounts_review_info, state.accounts.size, totalTransactions)

text = "Continue"  →  text = stringResource(R.string.import_accounts_review_continue)

// Old
text = "${account.transactionCount} tx"
// New
text = stringResource(R.string.import_accounts_review_tx_count, account.transactionCount)
```

**CategoriesReviewViewProvider.kt:**
```kotlin
title = "Review Categories"  →  title = stringResource(R.string.import_categories_review_title)

// Old
text = "$selectedCount CATEGORIES"
// New
text = stringResource(R.string.import_categories_review_info, selectedCount)

text = "Continue"  →  text = stringResource(R.string.import_categories_review_continue)

// Old
text = "${category.transactionCount} transactions"
// New
text = pluralStringResource(R.plurals.import_categories_review_tx_count, category.transactionCount, category.transactionCount)
```

- [ ] Commit:

```bash
git add \
  zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt \
  zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt
git commit -m "feat: extract hardcoded strings from import review screens"
```

---

### Task 18: TransactionsPreviewViewProvider.kt

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewProvider.kt`

- [ ] Add `import androidx.compose.ui.res.stringResource`. Make these replacements:

```kotlin
title = "Review Transactions"  →  title = stringResource(R.string.import_transactions_preview_title)

// Old
text = "${state.totalCount} TRANSACTIONS"
// New
text = stringResource(R.string.import_transactions_preview_info, state.totalCount)

// Old
text = "Import ${state.totalCount} Transactions"
// New
text = stringResource(R.string.import_transactions_preview_import, state.totalCount)
```

- [ ] Commit:

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewProvider.kt
git commit -m "feat: extract hardcoded strings from TransactionsPreviewViewProvider"
```

---

### Task 19: Verify — lint + tests

- [ ] Run lint:

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Expected: no errors. Fix any R.string reference errors (misspelled keys, missing imports).

- [ ] Run unit tests:

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] Push:

```bash
git push origin worktree-feature+localize-string-extraction
```
