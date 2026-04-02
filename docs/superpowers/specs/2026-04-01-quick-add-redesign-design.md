# Quick Add Transaction Screen Redesign

## Overview

Redesign the transaction add screen (expense/income types) to match the Stitch design "Updated Quick Add - Neutral Icons". The transfer type is deferred to a future phase.

Reference designs:
- **Expense/Income**: Stitch project `9891740845259743556`, screen `1a787a28fe9c4d3fa0a9526df8fb1e8d` ("Updated Quick Add - Neutral Icons")
- **Transfer** (phase 2): Stitch screen `63b0c4b0c4cd4359b4a05211b6f5ea89` ("Manual Transfer - Harmonized Style")

## Scope

**In scope:**
- Header with discard (X) button
- Segmented toggle replacing type dropdown
- Large centered amount display with currency symbol
- Horizontally scrollable category row (expense/income only)
- Side-by-side currency | account card selectors
- Conditional rate field (when currencies differ)
- ExtendedFAB save button (floating, full-width)
- Theme color palette update to match Stitch design system

**Out of scope (deferred):**
- Transfer type redesign (stays as-is)
- Tags, notes, recurring, receipt attachment
- Date picker
- "Add More Details" expandable section

## Layout (top to bottom)

```
Header:        [X discard]     New Transaction
Toggle:        [Expense]  [Income]  [Transfer]
Amount:                  AMOUNT
                       $ 0.00
Categories:    (horizontal scroll) Food | Transport | Shopping | ...
Selectors:     [ Currency  ▾ ] [ Account  ▾ ]
Rate:          [ Rate field ] (conditional)
               (bottom padding for FAB)
FAB:           ★ Save Transaction (floating)
```

When Transfer is selected, the current transfer form renders unchanged.

## Styling

Color palette sourced from the Stitch design system:

| Token | Hex | Usage |
|-------|-----|-------|
| primary | `#000e2f` | Primary text |
| primary-container | `#0a2351` | FAB bg, header icon color, deep accents |
| on-primary | `#ffffff` | Text on primary surfaces |
| secondary | `#006c4a` | Positive/success accents |
| secondary-container | `#82f5c1` | Success badges |
| surface | `#faf8fd` | Screen background |
| surface-container-low | `#f5f3f7` | Toggle container, card selectors |
| surface-container-lowest | `#ffffff` | Selected toggle tab, category item bg |
| on-surface | `#1b1b1f` | Body text |
| on-surface-variant | `#44464f` | Secondary text, labels |
| outline-variant | `#c5c6d0` | Subtle borders |

Component-specific styling:

- **Segmented toggle**: `surface-container-low` bg container with 12dp rounded. Selected tab: white bg + subtle shadow. Unselected: `on-surface-variant` text. All tabs: 14sp semibold.
- **Amount input**: "AMOUNT" label in 10sp bold uppercase tracking-widest `on-surface-variant`. Currency symbol: 32sp bold `on-surface-variant`. Amount value: 56sp extrabold `primary`. Center-aligned. The amount is a `BasicTextField` (no border, no background) styled to look like a large display. `KeyboardType.Number`.
- **Category items**: White rounded-full 48dp icon container on each item. Icon rendered via `ImageLoader` with `primary` tint. Category name below in 12sp semibold. Horizontal scroll with 12dp gaps. Selected category: `primary-container` icon container bg with white icon tint, bold `primary` name text. Unselected: `slate-100` icon container bg with `primary` icon tint.
- **Card selectors** (currency/account): `surface-container-low` bg, 16dp rounded corners, 10sp uppercase bold tracking-wider label, 14sp bold value, `unfold_more` trailing icon.
- **Save FAB**: `ExtendedFloatingActionButton` with `primary-container` bg, white text, 16dp rounded, shadow. Full-width style, fixed at screen bottom.
- **Header**: Surface bg, 64dp height. Close (X) icon button on left. "New Transaction" title centered in 18sp bold `primary-container`. No right-side action (save is the FAB).

## Architecture

### Approach
Restyle in-place. Rewrite composables in existing view provider files. No new modules, no ViewModel/UseCase/Component changes.

### Files modified

1. **`zero-core/.../transactions/edit/TransactionEditViewProvider.kt`**
   - Constructor: add `onDiscardHandler` parameter
   - `TransactionEditView` composable: new layout with header, segmented toggle, and `Box` with FAB
   - `TransactionTypeSelect` replaced by `TransactionTypeToggle` (private composable)
   - Save button moved from inline `Button` to `ExtendedFloatingActionButton` in a `Scaffold` or `Box` overlay

2. **`zero-core/.../transactions/edit/expense/TransactionEditExpenseViewProvider.kt`**
   - `TransactionEditExpenseView`: horizontal scrollable `LazyRow` for categories, 2-column `Row` for currency|account, conditional rate field
   - New private composables: `CategoryScrollRow`, `SelectorCard`

3. **`zero-core/.../transactions/edit/income/TransactionEditIncomeViewProvider.kt`**
   - Same structural changes as expense

4. **`app/.../ui/theme/Color.kt`**
   - Replace purple palette with Stitch design system colors

5. **`app/.../ui/theme/Theme.kt`**
   - Update `LightColorPalette` and `DarkColorPalette` to use new colors

### Files added

6. **`zero-core/.../transactions/edit/OnDiscardHandler.kt`**
   - Functional interface, same pattern as `OnTransactionSavedHandler`

### Files modified (wiring)

7. **`zero-core/.../transactions/edit/TransactionEditComponent.kt`**
   - Add `OnDiscardHandler` to `Builder` via `@BindsInstance`
   - Pass it through to `ViewProvider` in the `@Provides` method

8. **`app` module** — wherever `TransactionEditComponent.builder()` is called
   - Provide `OnDiscardHandler` lambda (navigate back)

### Not modified
- All ViewModels, UseCases, domain models
- `TransactionEditTransferViewProvider.kt`
- `TextFieldDropdownMenu.kt`, `CategoryIconView.kt` (still used elsewhere)
- Dagger modules (except the one `@Provides` for `ViewProvider`)
