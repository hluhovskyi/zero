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

**Verify layout with a UI dump + screenshot, not by reasoning about dp values** — visual gaps, clipping, and alignment errors are invisible in code. After any layout change, run `./scripts/dump-ui.sh` and check element bounds. For alignment fixes specifically, check *both* the x-position *and* the vertical gap between related elements; passing one check while ignoring the other is a common source of "still broken" follow-ups.

**Do not assume a component's layout size from its parameter name** — read the implementation. Example: `CategoryIconView(size = 40.dp)` actually occupies `size + 8.dp = 48.dp` in the layout because it reserves space for the selection border ring. When computing sibling offsets, run the dump first and measure from actual bounds.

## Compose Event Traps

**Do not use global touch interceptors for focus management.** `LazyColumn` and scrollable areas consume touch events, breaking root-level `clickable` modifiers. Instead of raw `pointerInput` hacks, apply `clearFocus()` explicitly on the interactive elements (buttons, selectors) or use a custom `Modifier` specifically on the items that should trigger dismissal.

**Never hardcode layout coordinates.** If you find yourself writing logic like `if (y > 350)` or manually calculating `Rect` bounds to manage interactions, stop immediately. You are fighting the framework.
