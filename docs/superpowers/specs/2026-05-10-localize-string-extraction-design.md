# Localization: String Extraction Design

**Date:** 2026-05-10
**Issue:** #103

## Goal

Extract all hardcoded UI strings from ViewProvider/Composable files to Android string resources,
making the app ready for localization.

## Scope

### In scope

- All string literals in `@Composable` functions across `zero-core` and `app` modules
- Includes: labels, titles, button text, section headers, placeholder text, empty-state messages,
  snackbar messages, content descriptions

### Out of scope (tracked separately)

- ViewModels that embed strings in State objects — tracked in issue #131
  (`DefaultTransactionsPreviewViewModel` fallbacks, `DefaultBottomBarViewModel` nav labels)
- Lint rule to enforce no future regressions — tracked in issue #132
- Log messages, class names, route strings, numeric placeholders, data values (currency codes)
- KMP migration: when pursued, `R.string.xxx` → `Res.string.xxx` is a mechanical find-and-replace

## Approach

**Standard Android string resources** (`res/values/strings.xml`) following the pattern established
in PR #99. No new dependencies or infrastructure changes.

- In Composables: `stringResource(R.string.xxx)`
- For count-based strings: `pluralStringResource(R.plurals.xxx, count, count)`
- For strings with interpolated values: `stringResource(R.string.xxx, arg1, arg2, ...)`

## String placement

Strings live in the module where their ViewProvider/Composable lives:

| Module | File |
|--------|------|
| `zero-core` | `zero-core/src/main/res/values/strings.xml` (~110 new entries) |
| `app` | `app/src/main/res/values/strings.xml` (~10 new entries) |

## Naming convention

`<screen>_<element>` in snake_case. Examples:

| Screen | String | Key |
|--------|--------|-----|
| Account edit | "New Account" | `account_edit_title` |
| Transaction filter | "Reset" | `transaction_filter_reset` |
| Settings | "PREFERENCES" (section header) | `settings_section_preferences` |
| Import | "Import Data" | `import_source_selection_title` |

Prefix rules:
- Screen-scoped strings: `<screen>_<element>` (e.g. `account_edit_save`)
- Shared within a feature: `<feature>_<element>` (e.g. `transaction_filter_period`)
- Bottom bar (app module): `bottom_bar_<tab>` (e.g. `bottom_bar_home`)

## Files to update

### `app` module

- `activity/screens/bottombar/DefaultBottomBarViewModel.kt` — bottom bar strings deferred to #131;
  no ViewProvider strings found in `app`

### `zero-core` module

- `accounts/AccountViewProvider.kt`
- `accounts/detail/AccountDetailViewProvider.kt`
- `accounts/edit/AccountEditViewProvider.kt`
- `categories/CategoryViewProvider.kt`
- `categories/detail/CategoryDetailViewProvider.kt`
- `categories/edit/CategoriesEditViewProvider.kt`
- `categories/picker/CategoryPickerViewProvider.kt`
- `currencies/picker/CurrencyPickerViewProvider.kt`
- `icons/IconAndColorPicker.kt`
- `imports/ImportViewProvider.kt`
- `imports/sourceselection/SourceSelectionViewProvider.kt`
- `imports/accountsreview/AccountsReviewViewProvider.kt`
- `imports/categoriesreview/CategoriesReviewViewProvider.kt`
- `imports/transactionspreview/TransactionsPreviewViewProvider.kt`
- `settings/SettingsViewProvider.kt`
- `transactions/TransactionViewProvider.kt`
- `transactions/TransactionFilterSheet.kt`
- `transactions/edit/TransactionEditViewProvider.kt`
- `transactions/edit/transfer/TransactionEditTransferViewProvider.kt`
- `transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt`
- `transactions/edit/common/TransactionEditAccountSelect.kt`
- `transactions/edit/common/TransactionEditAmountTextField.kt`
- `transactions/edit/common/TransactionEditCategorySelect.kt`
- `transactions/edit/common/TransactionEditCurrencySelect.kt`
- `transactions/edit/common/TransactionEditRateTextField.kt`
- `transactions/edit/common/CategoryScrollRow.kt`

## Deliverables

1. `zero-core/src/main/res/values/strings.xml` — ~110 new string/plural entries
2. `app/src/main/res/values/strings.xml` — unchanged (bottom bar strings deferred)
3. All ViewProvider Kotlin files above updated to use `stringResource()`
4. GitHub issue #131 — ViewModel strings deferred work
5. GitHub issue #132 — lint rule for future enforcement

## Verification

- `./gradlew lintDebug` passes (no regressions)
- `./gradlew testDebugUnitTest` passes
- UI inspection via `android-ui-inspector` confirms all visible strings render correctly
