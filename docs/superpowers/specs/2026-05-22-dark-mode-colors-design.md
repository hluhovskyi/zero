# Dark Mode Colors — Palette Implementation

## Goal

Fill in the dark palette values in `DarkZeroColors` so the app renders correctly when the device is in dark mode. The prep refactor (#241) already routed every callsite through `ZeroTheme.colors.*` and left `DarkZeroColors = LightZeroColors.copy(isLight = false)` as a stub. This PR replaces that stub with the real dark palette from the design system, plus the status bar wiring (`values-night/themes.xml`) that the prep spec called out as the next follow-up.

**Source of truth:** `Components.jsx` `DARK_PALETTE` in the design bundle at `wYsBVLj1TYUptjS1il8cvQ` — see `ui_kits/zero/Components.jsx` lines 16–25. The CSS file `colors_and_type.css` only defines light tokens.

## Mapping — design `DARK_PALETTE` → `DarkZeroColors`

| `ZeroColors` field | Light | Dark (design) | Source |
|---|---|---|---|
| `primary` | `#000E2F` | `#B1C6FD` | `DARK_PALETTE.primary` |
| `primaryContainer` | `#0A2351` | `#2D4B7E` | `DARK_PALETTE.primaryContainer` |
| `onPrimary` | `#FFFFFF` | `#00132C` | derived — readable dark navy on light blue primary |
| `onPrimaryContainer` | `#778BBF` | `#D9E2FF` | `DARK_PALETTE.onPrimaryContainer` |
| `secondary` | `#006C4A` | `#65D9A6` | `DARK_PALETTE.secondary` |
| `secondaryContainer` | `#82F5C1` | `#005237` | `DARK_PALETTE.secondaryContainer` |
| `onSecondary` | `#FFFFFF` | `#003824` | `DARK_PALETTE.onSecondary` |
| `onSecondaryContainer` | `#00714E` | `#82F5C1` | derived — readable bright green on dark green container |
| `surface` | `#FAF8FD` | `#111318` | `DARK_PALETTE.surface` |
| `surfaceContainerLowest` | `#FFFFFF` | `#1B1D24` | `DARK_PALETTE.surfaceLowest` |
| `surfaceContainerLow` | `#F5F3F7` | `#181A20` | `DARK_PALETTE.surfaceLow` |
| `surfaceContainer` | `#EFEDF2` | `#22252D` | `DARK_PALETTE.surfaceContainer` |
| `surfaceContainerHigh` | `#E9E7EC` | `#2A2D35` | `DARK_PALETTE.surfaceHigh` |
| `onSurface` | `#1B1B1F` | `#E3E2E9` | `DARK_PALETTE.onSurface` |
| `onSurfaceVariant` | `#44464F` | `#C5C6D0` | `DARK_PALETTE.onSurfaceVariant` |
| `outline` | `#757780` | `#8F909A` | `DARK_PALETTE.outline` |
| `outlineVariant` | `#C5C6D0` | `#44464F` | `DARK_PALETTE.outlineVariant` |
| `error` | `#BA1A1A` | `#E5564C` | `DARK_PALETTE.error` |
| `selectedPill` | `#D9E2FF` | `#2D4B7E` | `DARK_PALETTE.navPill` |
| `inversePrimary` | `#B1C6FD` | `#000E2F` | `DARK_PALETTE.inversePrimary` |

### Fields not in design palette — derived

The design's `DARK_PALETTE` only defines the 20 fields above. The remaining `ZeroColors` fields are derived using Material 3 dark-mode conventions and the design's `LIGHT_TO_DARK` mapping for tinted surfaces:

| `ZeroColors` field | Light | Dark | Reasoning |
|---|---|---|---|
| `primaryContainerLight` | `#C8D8FE` | `#4F6FA8` | Welcome screen mid-card backdrop — needs to read as a lighter blue than `primaryContainer` (`#2D4B7E`) but darker than `onPrimaryContainer` (`#D9E2FF`) so the layered welcome cards stay distinguishable. |
| `errorContainer` | `#FFDAD6` | `#5A1F1B` | M3 dark `errorContainer` ≈ deep red. |
| `onError` | `#FFFFFF` | `#680003` | M3 dark `onError` — text on the brighter dark `error` red. |
| `inverseSurface` | `#303034` | `#E3E2E9` | M3 inversion: dark mode's inverse is a light surface (matches `DARK_PALETTE.onSurface`). |
| `inverseOnSurface` | `#F2F0F5` | `#303034` | M3 inversion — dark text on the now-light inverse surface. |
| `transactionExpense` | `#BA1A1A` | `#E5564C` | Tracks `error`. |
| `transactionIncome` | `#006C4A` | `#65D9A6` | Tracks `secondary`. |
| `importMergeContainer` | `#E8EEFF` | `#15193A` | Per `LIGHT_TO_DARK['#E8EAF6']` pattern in design. |
| `importNewContainer` | `#E8F5E9` | `#0E2A12` | `LIGHT_TO_DARK['#E8F5E9']`. |
| `importNewContent` | `#1B5E20` | `#7FD18C` | `LIGHT_TO_DARK['#1B5E20']`. |
| `importErrorContainer` | `#FFEBEE` | `#3A0F12` | `LIGHT_TO_DARK['#FFEBEE']`. |
| `importErrorContent` | `#93000A` | `#FFB4AB` | M3 `onErrorContainer` dark — brightened red text on the dark-red container. |
| `welcomeCardLine` | `#FFFFFF` | `#FFFFFF` | Translucent shimmer on the dark welcome card — `primaryContainer` in dark is still dark blue (`#2D4B7E`), so white-with-alpha lines still read correctly. |
| `scrim` | `0x52000000` | `0x52000000` | Modal backdrop — kept identical; 32% black scrim works on both surfaces. |

## Status bar (`values-night/themes.xml`)

Light theme uses `android:statusBarColor=#E3E2E6` with `windowLightStatusBar=true` (`app/src/main/res/values/themes.xml`). Dark theme needs the mirrored config:

```xml
<!-- app/src/main/res/values-night/themes.xml -->
<resources>
    <style name="Theme.Zero" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#111318</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

The dark status bar matches `DARK_PALETTE.surface` (`#111318`), so the system bar blends with the app's top edge instead of producing a visible seam. `windowLightStatusBar=false` flips the icon foreground to light so the time/battery remain legible against the dark bar.

## Out of scope (explicit)

- **`SummaryBar.kt` and `BudgetCard.kt` hardcoded colors.** Both files were added in #234 (Budget Phase 4) after the prep migration and contain ~12 `Color(0x…)` literals (e.g. `SummaryBg = #1A2E52`, `OverBg = #FFF8F6`). They're intentional always-dark/always-light hero cards in light mode and will look slightly off in dark mode but won't break — migrating them to semantic tokens (or new tokens like `budgetSummaryBg`) is a follow-up PR. Out of scope here to keep the palette PR focused.
- **Lint rule banning `Color(0x…)` literals outside `theme/`.** Same follow-up as called out in the prep spec.
- **Drawable `@android:color/white` icons.** They're tinted programmatically and already theme-agnostic.
- **Splash screen.** Light-only splash continues; not part of the dark palette work.

## Verification

- `./gradlew testDebugUnitTest` — no logic changes, existing tests stay green.
- `./gradlew lintDebug` — no new errors.
- UI inspector spot-check via `android-ui-inspector`:
  - Toggle device dark mode: `./scripts/ui/adb.sh shell "cmd uimode night yes"` / `night no`.
  - Inspect Transactions, Accounts, Categories, Budget, Welcome, and one bottom sheet (e.g. Add Transaction) in both modes.
  - Verify status bar contrast in dark mode (light icons on `#111318`).

## Docs

Update `docs/agents/color-scheme.md` — drop the "stub mirroring light" warning on `DarkZeroColors` (the stub is replaced), keep the rest of the guidance.
