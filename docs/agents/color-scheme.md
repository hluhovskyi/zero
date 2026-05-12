# ColorScheme

- `ColorScheme(swatch: Color, primary: Color, background: Color)` — three domain `Color` values, each with `id` and `value: ColorValue`:
  - `swatch` — brighter display color shown in pickers and swatches
  - `primary` — darker tone used for themed text and icons
  - `background` — lighter tone used for themed container backgrounds
- `ColorScheme.Grey` — default fallback scheme; use when no color is selected
- `ColorValue.isUnspecified()` — true for `ColorValue(0x00000000UL)`
- `ColorValue.hex.toInt()` gives ARGB int; safe to pass to `ComposeColor(argb: Int)`
- `colorRepository.schemeFor(id: Id.Known): ColorScheme` — synchronous lookup
- **Always tint an icon with the color scheme of the entity it represents** — never a related entity's (e.g. account icon uses `account.colorScheme`; category icon uses `category.colorScheme`). Using a sibling entity's color leaks visual identity across domain boundaries.
