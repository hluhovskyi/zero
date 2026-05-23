# `ZeroThemeBypass` Lint Rule — Design

## Goal

Mechanically prevent the dark-mode-prep regression: any app color that doesn't route through `ZeroTheme.colors.*` is a build error.

## Rule

Flag — outside the allowlist:

1. Any call to `androidx.compose.ui.graphics.Color(...)` (all overloads: `Int`, `Long`, RGB, RGBA).
2. Any qualified reference to the `Color` companion-object color constants: `Color.White`, `Color.Black`, `Color.Red`, `Color.Green`, `Color.Blue`, `Color.Gray`, `Color.Yellow`, `Color.Magenta`, `Color.Cyan`.

**Allowed:**
- Files in the `com.hluhovskyi.zero.ui.theme` package (the canonical home for `ZeroColors` and friends).
- The `UiColorScheme.kt` file (entity-palette fallback, orthogonal to app theming — already documented as out of scope in `docs/agents/color-scheme.md`).
- `Color.Unspecified` and `Color.Transparent` — sentinel values, not palette choices.

Suppress per-site with `@Suppress("ZeroThemeBypass")` for the rare bespoke palette (e.g. the budget summary widget's dark island).

## Architecture

Single `Detector` in `lint-rules/.../ZeroThemeBypassDetector.kt`, modelled on `HardcodedComposableStringDetector`:

- `UastScanner` over `UCallExpression` (constructor calls) and `USimpleNameReferenceExpression` (the `Color.White`-style qualified refs).
- For call expressions: resolve and check that the resolved method's containing FQN is `androidx.compose.ui.graphics.ColorKt.Color` (the Kotlin top-level builder; reports as a constructor-style call in UAST).
- For simple name references: walk the qualifier; if it resolves to `androidx.compose.ui.graphics.Color`'s companion and the property name is one of the banned set, flag.
- Allowlist check: inspect `context.uastFile?.packageName` for the theme package, and `context.file.name` for `UiColorScheme.kt`. Return early.

Register in `ZeroIssueRegistry`.

## Test fixtures (`ZeroThemeBypassDetectorTest`)

Each test follows the existing `LintDetectorTest` pattern with a minimal `Color` stub. Cases:

| # | Scenario | Expectation |
|---|---|---|
| 1 | `Color(0xFFFFFFFF)` in a feature file | flagged |
| 2 | `Color(0xFFAA0000L)` (Long overload) | flagged |
| 3 | `Color(0.5f, 0.5f, 0.5f)` (RGB overload) | flagged |
| 4 | `Color(0.5f, 0.5f, 0.5f, 0.5f)` (RGBA overload) | flagged |
| 5 | `Color.White` in a feature file | flagged |
| 6 | `Color.Black.copy(alpha = 0.32f)` | flagged (the `Color.Black` ref, not the `.copy`) |
| 7 | `Color.Unspecified` | clean (sentinel) |
| 8 | `Color.Transparent` | clean (sentinel) |
| 9 | `Color(0xFF000000)` inside `com.hluhovskyi.zero.ui.theme.ZeroColors.kt` | clean (allowlist) |
| 10 | `Color(0xFF8E8E93)` inside `UiColorScheme.kt` | clean (allowlist) |
| 11 | `Color(0xFFFFFFFF)` with `@Suppress("ZeroThemeBypass")` on the val | clean |

Stub `Color` builder + companion mirroring just enough of `androidx.compose.ui.graphics.Color` to resolve.

## Pre-existing violations (12)

After landing, suppress the bespoke budget palette per-val with `@Suppress("ZeroThemeBypass")`:

- `zero-core/.../budget/SummaryBar.kt` — 9 file-private vals (`SummaryBg`, `SummaryTextStrong`, `SummaryTextDim`, `SummaryTrack`, `GreenAccent`, `OrangeAccent`, `RedAccent`, `OverPillBg`, `OnTrackPillBg`).
- `zero-core/.../budget/BudgetCard.kt` — 3 file-private vals (`OverBg`, `OrangeWarn`, `YellowWarn`).

These are intentional sub-palette colors for a single widget; suppression is the correct call rather than promotion to `ZeroColors`.

## Out of scope

- Allowing `Color(red, green, blue, alpha, colorSpace)` (5-arg overload) — same path, covered by the FQN check.
- `Color.hsl()` / `Color.hsv()` factory functions — not currently used; revisit if they appear.
- A pre-commit grep gate — superseded by this lint rule.
