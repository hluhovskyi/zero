# Kotlin / Compose Gotchas

## Interface Default Methods + `@Composable`

Interface default methods with `@Composable` and default parameters: Kotlin's `DefaultImpls` may execute the interface body instead of dispatching to the class override. Fix: make the method abstract (no body); add explicit overrides to all implementations.

## ComposeColor Constructor

`ComposeColor(packedLong)` encodes colorspace index in lower 6 bits — invalid for arbitrary hex values.

To convert a `ColorValue` to Compose `Color`: `ComposeColor(colorValue.hex.toInt())`. This passes the ARGB int, not the raw ULong.

## BasicTextField Auto-Focus on Screen Entry

**`FocusManager.clearFocus()` does not stop `BasicTextField` from auto-focusing** — `BasicTextField` focuses a native `EditText` independently of Compose's focus system. `clearFocus()` (including `force = true`, in `SideEffect`, in `LaunchedEffect`) only reaches the Compose layer and leaves the native `EditText` focused.

To prevent auto-focus on screen entry, place `Modifier.focusTarget()` on the parent container *before* the text field so Android's initial focus traversal lands on the non-text node instead:

```kotlin
Column(modifier = Modifier.fillMaxSize().focusTarget()) {
    SearchBar(...)  // no longer receives initial focus
}
```

Verify with the view dump, not a screenshot: `grep 'focused="true"' /tmp/ui.xml`.

## Layout Verification

**Verify layout with a UI dump + screenshot after every layout change** — visual gaps, clipping, and alignment errors are invisible in code. Run `./scripts/ui/dump-ui.sh` and check element bounds. For alignment fixes, verify both x-position *and* vertical gap between related elements — passing one check while ignoring the other is the most common source of "still broken" follow-ups.

## `stringResource` Outside Composable Context

**`stringResource` cannot be called in coroutines or plain lambdas** — it reads `LocalContext.current`, a composition local only accessible during composition. Three escape patterns:

- **Coroutine scope** (`LaunchedEffect`, `launch {}`): capture as `val`s before the coroutine block; use `String.format(template, arg)` for parameterised strings inside it.
- **Non-`@Composable` lambda** (`nameMapping: (T) -> String`): precompute a map in composable scope — `val labels = MyEnum.entries.associateWith { it.label() }` — and close over it.
- **Private helper**: annotate `@Composable` if the function is only called from composable context.

## Compose Event Traps

**Do not use global touch interceptors for focus management.** `LazyColumn` and scrollable areas consume touch events, breaking root-level `clickable` modifiers. Instead of raw `pointerInput` hacks, apply `clearFocus()` explicitly on the interactive elements (buttons, selectors) or use a custom `Modifier` specifically on the items that should trigger dismissal.

**Never hardcode layout coordinates.** If you find yourself writing logic like `if (y > 350)` or manually calculating `Rect` bounds to manage interactions, stop immediately. You are fighting the framework.
