# ColorScheme

- `ColorScheme(swatch: Color, primary: Color, background: Color)` — three domain `Color` values, each with `id` and `value: ColorValue`:
  - `swatch` — brighter display color shown in pickers and swatches
  - `primary` — darker tone used for themed text and icons
  - `background` — lighter tone used for themed container backgrounds
- `ColorScheme.Grey` — default fallback scheme; use when no color is selected
- `ColorValue.isUnspecified()` — true for `ColorValue(0x00000000UL)`
- `ColorValue.hex.toInt()` gives ARGB int; safe to pass to `ComposeColor(argb: Int)`
- `colorRepository.schemeFor(id: Id.Known): ColorScheme` — synchronous lookup
- `colorRepository.query(AllSchemes())` returns `Flow<List<ColorScheme>>`; use `scheme.swatch` directly — no `Map<ColorScheme, Color>` needed
