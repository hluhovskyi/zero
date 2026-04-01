# ColorScheme

- `ColorScheme(primary: Color, background: Color)` — both are domain `Color` with `id` and `value: ColorValue`
- `ColorScheme.Grey` — default fallback scheme (dark grey / light grey); use when no color is selected
- `ColorValue.isUnspecified()` — true for `ColorValue(0x00000000UL)`
- `ColorValue.hex.toInt()` gives ARGB int; safe to pass to `ComposeColor(argb: Int)`
- `colorRepository.schemeFor(id: Id.Known): ColorScheme` — synchronous lookup
