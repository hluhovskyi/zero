# Kotlin / Compose Gotchas

## Interface Default Methods + `@Composable`

Interface default methods with `@Composable` and default parameters: Kotlin's `DefaultImpls` may execute the interface body instead of dispatching to the class override. Fix: make the method abstract (no body); add explicit overrides to all implementations.

## ComposeColor Constructor

`ComposeColor(packedLong)` encodes colorspace index in lower 6 bits — invalid for arbitrary hex values.

To convert a `ColorValue` to Compose `Color`: `ComposeColor(colorValue.hex.toInt())`. This passes the ARGB int, not the raw ULong.
