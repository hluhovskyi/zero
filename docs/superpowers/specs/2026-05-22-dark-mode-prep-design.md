# Dark Mode Prep — Theming Infrastructure

## Goal

Refactor color usage across the app so a future dark palette can swap in with no code changes outside `theme/`. **Scope: infrastructure only.** Stub dark values (mirror light) for now — actual dark palette polish ships separately.

## Current state (audit)

- `zero-ui/.../theme/Theme.kt` — `ZeroTheme` already wraps `MaterialTheme` with light/dark `Colors` from `androidx.compose.material` and respects `isSystemInDarkTheme()`.
- `zero-ui/.../theme/Color.kt` — 26 top-level `val Foo = Color(0xFF…)` tokens (Primary, Surface, OnSurfaceVariant, …).
- **145 direct token imports** (`import com.hluhovskyi.zero.ui.theme.Primary`) across `app/`, `zero-core/`, `zero-ui/`. These bypass `MaterialTheme.colors` and **will not swap** when dark mode is active.
- **86 hardcoded `Color(0xFF…)` / `Color.White` / `Color.Black`** usages in ViewProviders.
- Only **18** `MaterialTheme.colors.*` callsites — most of the surface area is wired wrong for theming.
- `Material 1` is in use (`androidx.compose.material:material`). Its `Colors` class has fewer slots (no `surfaceContainer*`, no `outlineVariant`, no `inverse*`) — we need a custom holder for the extra semantic tokens.

## Architecture

### `ZeroColors` data class (new)

A read-only holder for every semantic color the app uses. One source of truth.

```kotlin
@Immutable
data class ZeroColors(
    val primary: Color,
    val primaryContainer: Color,
    val primaryContainerLight: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,

    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondary: Color,
    val onSecondaryContainer: Color,

    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,

    val onSurface: Color,
    val onSurfaceVariant: Color,

    val outline: Color,
    val outlineVariant: Color,

    val error: Color,
    val errorContainer: Color,
    val onError: Color,

    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,

    val selectedPill: Color,

    // Special-purpose tokens emerged from audit (named for intent, not hex)
    val scrim: Color,                  // modal scrim / overlay
    val transactionExpense: Color,     // dedicated red used by amount text
    val transactionIncome: Color,      // dedicated green used by amount text
    val importMergeContainer: Color,   // soft blue chip for "merge" strategy
    val importNewContainer: Color,     // soft green chip for "new" strategy
    val importNewContent: Color,       // green text on importNewContainer
    val importErrorContainer: Color,   // soft red banner background
    val importErrorContent: Color,     // dark red text on importErrorContainer
    val welcomeCardLine: Color,        // translucent white shimmer lines

    val isLight: Boolean,
)
```

### `LocalZeroColors` CompositionLocal (new)

```kotlin
val LocalZeroColors = staticCompositionLocalOf<ZeroColors> {
    error("ZeroColors not provided — wrap content in ZeroTheme")
}
```

### `ZeroTheme.colors` accessor (new)

```kotlin
object ZeroTheme {
    val colors: ZeroColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZeroColors.current
}
```

Callsite ergonomics: `ZeroTheme.colors.primary` (matches `MaterialTheme.colors.primary` shape).

### `Theme.kt` updates

- Define `LightZeroColors` (real values, lifted from current `Color.kt` consts).
- Define `DarkZeroColors` (**stub — mirrors LightZeroColors**, with a `// TODO(dark-mode): tune for dark` comment on the val). `isLight = false`. This satisfies the wiring; the actual dark palette ships in a follow-up.
- `ZeroTheme` provides `LocalZeroColors` via `CompositionLocalProvider`, then forwards a derived `androidx.compose.material.Colors` to `MaterialTheme` so existing `MaterialTheme.colors.*` callsites continue to work.

### `Color.kt` retirement

Delete the 26 top-level `val` tokens. The values live inside `LightZeroColors` instances. Anything still referencing the deleted tokens fails to compile — forces the migration to be complete.

## Migration

For every callsite found in the audit:

| Pattern | Replace with |
|---|---|
| `import …theme.Primary` then `Primary` in a Composable | `ZeroTheme.colors.primary` |
| `Color(0xFFFFFFFF)` as a background of an opaque surface | `ZeroTheme.colors.surface` or `surfaceContainerLowest` (pick by context) |
| `Color(0xFF1B1B1F)` (text) | `ZeroTheme.colors.onSurface` |
| `Color(0xFF44464F)` (text/icon) | `ZeroTheme.colors.onSurfaceVariant` |
| `Color(0xFF000E2F)` | `ZeroTheme.colors.primary` |
| `Color(0xFFD9E2FF)` | `ZeroTheme.colors.selectedPill` |
| `Color(0xFFBA1A1A)` | `ZeroTheme.colors.error` |
| `Color(0xFFE53935)` (badge) | `ZeroTheme.colors.error` |
| `Color(0xFF93000A)` (import error text) | `ZeroTheme.colors.importErrorContent` |
| `Color(0xFFFFEBEE)` (import error bg) | `ZeroTheme.colors.importErrorContainer` |
| `Color(0xFFE8EEFF)` (chip / iconBg) | `ZeroTheme.colors.importMergeContainer` |
| `Color(0xFFE8F5E9)` (chip) | `ZeroTheme.colors.importNewContainer` |
| `Color(0xFF1B5E20)` (chip icon) | `ZeroTheme.colors.importNewContent` |
| `Color(0xFF006C4A)` (expense view amount, when income) | `ZeroTheme.colors.transactionIncome` |
| `Color(0xFF5DDBA8)` (budget OK tint) | `ZeroTheme.colors.transactionIncome` |
| `Color.White` as text/tint on a coloured filled surface | `ZeroTheme.colors.onPrimary` (most cases) |
| `Color.White` as standalone background | `ZeroTheme.colors.surfaceContainerLowest` |
| `Color.White.copy(alpha = X)` over a dark hero | new token `welcomeCardLine` (alpha kept) or callsite-local `copy(alpha)` of `onPrimary` |
| `Color.Black.copy(alpha = 0.32f)` (modal scrim) | `ZeroTheme.colors.scrim` |
| `Color(0x40000000)` (overlay) | `ZeroTheme.colors.scrim` |
| `Color(0xFF8E8E93)` / `Color(0xFFE5E5EA)` in `UiColorScheme.default()` | leave as-is — this is a fallback for `ColorScheme` (domain palette), not app theming |
| `lerp(Color.White, x, 0.45f)` in `CategoryViewProvider` | `lerp(ZeroTheme.colors.surfaceContainerLowest, x, 0.45f)` |

Special cases:

- `BottomBarViewProvider.kt` — replaces a slab of hardcoded hexes with `ZeroTheme.colors.surfaceContainerLowest` / `primary` / `onSurfaceVariant` / `selectedPill`.
- `TransactionExpenseView.kt` defaults — `Color(0xFF006C4A)` becomes a default of `ZeroTheme.colors.transactionIncome` at the callsite (composable default), not at the function signature (signature defaults can't read CompositionLocals).

### Files touched

Mechanical edits across:

- `zero-ui/.../theme/Color.kt` (retire)
- `zero-ui/.../theme/Theme.kt` (rewrite)
- `zero-ui/.../theme/ZeroColors.kt` (new)
- `app/.../bottombar/BottomBarViewProvider.kt`
- `app/.../activity/screens/MainActivityScreenViewProvider.kt`
- `zero-core/` — `imports/` (6 files), `transactions/` (4 files), `welcome/`, `categories/`, `budget/`, `currencies/picker/`
- `zero-ui/` — `ZeroFab`, `CategoryIconView`, `ImportErrorBanner`, `TransactionExpenseView`, `SegmentedToggle`, `AmountDisplay`, `SelectorCard`, `DatePickerCard`, `CategoryScrollRow`

## Out of scope (explicit)

- Actual dark palette colour values (`DarkZeroColors` is stubbed to mirror light).
- `values-night/themes.xml` for status bar / splash — light-only stays for now; documented in `docs/agents/color-scheme.md` as the next follow-up.
- A lint rule banning `Color(0x…)` literals outside `theme/`. Useful, but separate PR.
- `UiColorScheme.default()` — that's the domain `ColorScheme` fallback (entity colors), not theming.
- Drawable `android:fillColor="@android:color/white"` icons — they're tinted programmatically and already theme-agnostic.

## Verification

- `./gradlew testDebugUnitTest` — no logic changes; existing tests stay green.
- `./gradlew lintDebug` — no new errors.
- UI inspector spot-check via `android-ui-inspector` skill — bottom bar, transactions, imports, welcome. Visual parity with master (since dark stubs mirror light, nothing should look different).
- Manual grep: `grep -rEn "Color\(0x" app zero-core zero-ui --include='*.kt' | grep -v theme/ | grep -v UiColorScheme` returns zero results.

## Docs

Update `docs/agents/color-scheme.md` to add a "Theming" section pointing at `ZeroTheme.colors.*` as the only way to read app colors. Keep the existing `ColorScheme` (entity palette) section unchanged — they're orthogonal concerns.
