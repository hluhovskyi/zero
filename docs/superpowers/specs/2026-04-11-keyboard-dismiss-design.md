# Keyboard Dismiss Behaviour — Design Spec

**Date:** 2026-04-11

## Problem

On both the Account Edit and Transaction Edit screens the soft keyboard:
- Does not dismiss when the user taps outside the amount text field
- Does not dismiss when the user opens any picker (SelectorCard, DatePickerCard, currency, etc.)
- Can re-appear automatically after the user has already dismissed it (transaction screen only, caused by type-switching Expense↔Transfer which removes/re-adds the composable and re-triggers `LaunchedEffect(Unit)`)

## Goals

1. Tapping outside the amount field (on empty space) dismisses the keyboard.
2. Tapping any picker/selector dismisses the keyboard before that picker opens.
3. The initial auto-focus on screen open fires exactly once; after the user dismisses the keyboard it never auto-focuses again.
4. User tapping the amount field directly still shows the keyboard (normal text field behaviour).
5. Zero per-usage-site boilerplate — no `clearFocus()` scattered across every click handler.

## Solution

### 1 — Background tap handler (per screen, once)

Each edit screen's outer `Box` gets a `pointerInput` modifier that calls `focusManager.clearFocus()` on any tap that was **not** consumed by a child. Because `detectTapGestures` uses `PointerEventPass.Main`, it only fires when children don't consume the event — so interactive elements (buttons, selectors) are unaffected by this handler alone.

```kotlin
val focusManager = LocalFocusManager.current
Box(
    modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
) { ... }
```

Screens affected: `AccountEditViewProvider`, `TransactionEditViewProvider`.

### 2 — Shared component keyboard clearing (once per component)

`SelectorCard` and `DatePickerCard` use `LocalFocusManager.current` internally and call `clearFocus()` before their own action. This makes every current and future usage in the app automatically dismiss the keyboard — no call-site changes needed.

**SelectorCard** — in the `clickable` lambda, before `onClick()` or `expanded = true`:
```kotlin
val focusManager = LocalFocusManager.current
.clickable {
    focusManager.clearFocus()
    if (onClick != null) onClick() else expanded = true
}
```

**DatePickerCard** — same pattern before `DatePickerDialog.show()`.

### 3 — One-time auto-focus guard (pure view layer)

#### AccountEditViewProvider
The composable is stable (never removed/re-added). A plain `remember` flag is sufficient:
```kotlin
var hasAutoFocused by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    if (!hasAutoFocused) {
        focusRequester.requestFocus()
        hasAutoFocused = true
    }
}
```

#### TransactionEditViewProvider + TransactionEditExpenseIncomeViewProvider
Switching Expense↔Transfer removes and re-adds the expense/income composable, which resets `remember` state and re-fires `LaunchedEffect(Unit)`. Fix: wrap the slot in `SaveableStateProvider` so `rememberSaveable` inside the child persists across removal/re-addition.

In `TransactionEditViewProvider`:
```kotlin
val stateHolder = rememberSaveableStateHolder()
// ...
stateHolder.SaveableStateProvider("expense_income") {
    expenseIncomeComponent.AttachWithView()
}
```

In `TransactionEditExpenseIncomeViewProvider`:
```kotlin
var hasAutoFocused by rememberSaveable { mutableStateOf(false) }
LaunchedEffect(Unit) {
    if (!hasAutoFocused) {
        focusRequester.requestFocus()
        hasAutoFocused = true
    }
}
```

## Files to change

| File | Change |
|------|--------|
| `zero-ui/.../SelectorCard.kt` | Add `LocalFocusManager.current.clearFocus()` before click action |
| `zero-ui/.../DatePickerCard.kt` | Add `LocalFocusManager.current.clearFocus()` before dialog show |
| `zero-core/.../AccountEditViewProvider.kt` | Background tap handler + `remember` auto-focus guard |
| `zero-core/.../TransactionEditViewProvider.kt` | `rememberSaveableStateHolder` + `SaveableStateProvider` wrapper |
| `zero-core/.../TransactionEditExpenseIncomeViewProvider.kt` | `rememberSaveable` auto-focus guard |

## What is explicitly NOT done

- No ViewModel changes — this is entirely view-layer.
- No `PointerEventPass.Initial` interceptors — would break scroll/click interactions.
- No per-call-site `clearFocus()` additions — handled once in shared components.
- No hardcoded bounds or coordinates.
