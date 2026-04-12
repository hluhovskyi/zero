# Kotlin / Compose Gotchas

## Interface Default Methods + `@Composable`

Interface default methods with `@Composable` and default parameters: Kotlin's `DefaultImpls` may execute the interface body instead of dispatching to the class override. Fix: make the method abstract (no body); add explicit overrides to all implementations.

## ComposeColor Constructor

`ComposeColor(packedLong)` encodes colorspace index in lower 6 bits — invalid for arbitrary hex values.

To convert a `ColorValue` to Compose `Color`: `ComposeColor(colorValue.hex.toInt())`. This passes the ARGB int, not the raw ULong.

## Compose Event Traps

**Do not use global touch interceptors for focus management.** `LazyColumn` and scrollable areas consume touch events, breaking root-level `clickable` modifiers. Instead of raw `pointerInput` hacks, apply `clearFocus()` explicitly on the interactive elements (buttons, selectors) or use a custom `Modifier` specifically on the items that should trigger dismissal.

**Never hardcode layout coordinates.** If you find yourself writing logic like `if (y > 350)` or manually calculating `Rect` bounds to manage interactions, stop immediately. You are fighting the framework.
