# Domain Types — Gotchas and Non-obvious Behavior

All types live in `zero-api`. Read the source for full API — this doc covers only what isn't obvious from the code.

## Id

- `Id.Unknown` is the null-equivalent. Never use `null` for missing IDs.
- `Id("string")` always returns `Id.Known`, never `Unknown`. To get `Unknown`, use the sealed type directly.
- Navigation arguments use `idOptionalValueOf()` which falls back to `Id.Unknown`, not `null`.

## Amount

- `Amount(null)` returns `Amount.zero()`, not a crash. Safe for `toBigDecimalOrNull()` results.
- `amount.withRate(rate)` multiplies — used for currency conversion display.

## Rate

- `Rate.Same` is `BigDecimal(1)` — means "no conversion needed" (same currency).
- `Rate(null)` returns `Rate.Same`.

## Clock

- Always inject `Clock` and call `clock.localDateTime()`. Never `LocalDateTime.now()`.
- Tests can provide a fixed clock for deterministic results.

## ColorValue

- `ColorValue.hex` is `ULong`. To convert to Compose: `ComposeColor(colorValue.hex.toInt())`.
- `ComposeColor(ULong)` is a **different constructor** that encodes colorspace in lower bits — will produce wrong colors.

## Flow Extensions

- `onStartWithEmptyList()` is critical in `combine()` — without it, `combine` blocks until ALL source flows emit, causing blank screens.
