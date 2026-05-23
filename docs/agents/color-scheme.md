# Color & Theming

## App theming — `ZeroTheme.colors`

- `ZeroTheme.colors.<token>` is the **only** way to read app colors from a `@Composable`. Reading `MaterialTheme.colors.*` works for Material widgets but does not expose the extra tokens (`surfaceContainerHigh`, `selectedPill`, `importNewContainer`, …).
- Top-level color consts in `zero-ui/.../theme/Color.kt` are deleted on purpose. If you need a new semantic token, add a field to `ZeroColors` and set values in both `LightZeroColors` and `DarkZeroColors`.
- `DarkZeroColors` is a stub mirroring light. The real dark palette ships as a follow-up — do not assume dark-mode visuals are tuned.
- Function-signature defaults can read `ZeroTheme.colors.*` only inside `@Composable` functions. For non-composable defaults (e.g. a `DrawScope` lambda), hoist the read to the call site and pass the resolved `Color` in.

## Entity `ColorScheme` (domain palette)

- `ColorScheme(swatch: Color, primary: Color, background: Color)` — three domain `Color` values, each with `id` and `value: ColorValue`:
  - `swatch` — brighter display color shown in pickers and swatches
  - `primary` — darker tone used for themed text and icons
  - `background` — lighter tone used for themed container backgrounds
- `ColorScheme.Grey` — default fallback scheme; use when no color is selected
- `ColorValue.isUnspecified()` — true for `ColorValue(0x00000000UL)`
- `ColorValue.hex.toInt()` gives ARGB int; safe to pass to `ComposeColor(argb: Int)`
- `colorRepository.schemeFor(id: Id.Known): ColorScheme` — synchronous lookup
- **Always tint an icon with the color scheme of the entity it represents** — never a related entity's (e.g. account icon uses `account.colorScheme`; category icon uses `category.colorScheme`). Using a sibling entity's color leaks visual identity across domain boundaries.
- Entity `ColorScheme` is orthogonal to app theming. `UiColorScheme.default()` is a fallback for entity colors only; it must NOT route through `ZeroTheme.colors`.
