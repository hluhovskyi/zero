# Material 3 Migration — Design

**Date:** 2026-05-30
**Goal:** Migrate the whole app from Compose Material 2 (`androidx.compose.material`) to Material 3 (`androidx.compose.material3`) in a single PR, with no intended visual regression.

## Why this is far smaller than it looks

The scary surface (57 files importing `androidx.compose.material.*`, ~553 with worktree copies) collapses once you look at *what* they import:

- **`material-icons-extended` is theme-agnostic.** The ~1077 `material.icons.*` imports are shared by M2 and M3 and **do not migrate**. They stay exactly as-is.
- **Color reads already go through a custom token layer.** Only **18** call sites read M2's `MaterialTheme.colors`; **445** read the app's own `ZeroTheme.colors`. The token object `ZeroColors` is **already modeled on M3 color roles** (`primaryContainer`, `onPrimaryContainer`, `surfaceContainerLowest/Low/High`, `onSurfaceVariant`, `outline`, `outlineVariant`, `errorContainer`, `inverseSurface`, `inversePrimary`, `scrim`). `zero-ui/AGENTS.md` even already labels the theme "Material 3 theme setup." We are realizing documented intent.
- **Typography and Shapes have zero call-site reads.** `MaterialTheme.typography` / `MaterialTheme.shapes` are read **0** times. `Type.kt` and `Shape.kt` are configured only inside `Theme.kt`, so rewriting them is fully isolated.
- **Every genuinely-different component is localized to a single file** (Divider, BottomNavigation, ExposedDropdownMenuBox, the `ModalBottomSheetState` sheet, and the nav bottom-sheet).

So the migration is: swap the dependency, rebuild the theme from the same tokens, fix 18 color reads, do a mechanical package swap for theme-agnostic components, and hand-rewrite ~5 component call sites.

## Scope

In scope: `zero-ui`, `zero-core`, `app` — all `androidx.compose.material.*` (non-icons) usage.
Out of scope: `material-icons-extended`, any visual redesign, dynamic color / Material You.

## Key decisions

1. **Keep `ZeroColors` + `LocalZeroColors` as the source of truth.** Do **not** adopt dynamic color. The app has a deliberate brand palette; Material You theming is explicitly not wanted (YAGNI). `Theme.kt` builds an M3 `ColorScheme` (`lightColorScheme`/`darkColorScheme`) from the existing tokens — *expanding* today's squashed 12-slot `lightColors`/`darkColors` mapping out to the full set of M3 roles the tokens already carry. The 445 `ZeroTheme.colors.X` reads are untouched.

2. **Redirect the 18 `MaterialTheme.colors.X` reads to `ZeroTheme.colors.X`**, matching the dominant convention rather than introducing `MaterialTheme.colorScheme` reads. Mapping: `.primary` → `ZeroTheme.colors.primary`; `.background` and `.surface` → `ZeroTheme.colors.surface` (today's `Theme.kt` already maps `background = surface`).

3. **Typography:** M2 `Typography(body1 = …)` → M3 `Typography(bodyLarge = …)`. Only `body1` is customized today; the rest are defaults. Safe (0 reads).

4. **Shapes:** M2 `Shapes(small/medium/large)` → M3 `Shapes(extraSmall/small/medium/large/extraLarge)`, preserving current radii (small/medium = 4dp, large = 0dp; fill the new slots conservatively from those). Safe (0 reads).

5. **Mechanical package swap** `androidx.compose.material.X` → `androidx.compose.material3.X` for theme-agnostic components: `Text`, `Icon`, `IconButton`, `Surface`, `Button`, `Scaffold`, `OutlinedTextField`, `DropdownMenu`, `DropdownMenuItem`, `Switch`/`SwitchDefaults`, `CircularProgressIndicator`, `FloatingActionButton`/`FloatingActionButtonDefaults`, `SnackbarHost`/`SnackbarHostState`, `MaterialTheme`. Each carries small API deltas handled per call site (e.g. `Surface` elevation → `tonalElevation`/`shadowElevation`; `Scaffold` consumes `WindowInsets` by default — verify padding on device; `OutlinedTextField` color params renamed via `OutlinedTextFieldDefaults`).

6. **Hand-rewrite the localized components:**
   - `Divider` → `HorizontalDivider` (1 file).
   - `BottomNavigation`/`BottomNavigationItem` → `NavigationBar`/`NavigationBarItem` (1 file, `BottomBarViewProvider`). New `NavigationBarItemDefaults` color API.
   - `ExposedDropdownMenuBox` (1 file) — M3 signature changes (`menuAnchor()` modifier now required).
   - The `ModalBottomSheetState`/`rememberModalBottomSheetState` sheet (1 file) → M3 `ModalBottomSheet` + `SheetState`, preserving the deliberate **half-expanded + visible DragHandle** UX (`skipPartiallyExpanded = false` + `dragHandle = { BottomSheetDefaults.DragHandle() }`), per prior product decision on issue #213.

7. **Nav bottom-sheet (`androidx.compose.material.navigation`, `ModalBottomSheetLayout` + `bottomSheet {}`):** localized to `MainActivityScreenViewProvider` / `MainActivityScreenComponent` / `MainActivityViewProvider` in `app/`. This is the one **open technical risk (R1)** — see below.

8. **Dependency:** add `androidx-compose-material3` to the version catalog at the version that pairs with Compose `1.10.5` (resolved empirically during execution). Remove `androidx-compose-material` once nothing references it. Keep `material-icons-extended`.

## Open technical risk

**R1 — nav bottom-sheet has no guaranteed drop-in M3 equivalent.** `androidx.compose.material.navigation` (the M2 `ModalBottomSheetLayout` + `bottomSheet {}` destination integration) may not have a stable M3 counterpart artifact. Execution resolves this empirically, in priority order:
   1. If an M3 nav bottom-sheet artifact exists at a compatible version, use it (drop-in).
   2. Otherwise, convert the bottom-sheet *destinations* to M3 `ModalBottomSheet` driven by the existing navigation state (manual wiring, contained to the 3 `app/` files).
   3. Last resort: keep `material-navigation` (M2) as a single isolated island for nav sheets only, while everything else is M3. Documented as a known follow-up if taken.

This risk is contained to 3 files and does not block the other ~54.

## Verification

- `./gradlew testDebugUnitTest lintDebug` — green.
- On-device **before/after screenshots** of every major screen in **light and dark** via `android-ui-inspector`: transactions list, transaction edit (expense/income/transfer), budget/numpad, categories, currency picker, settings, import flow, welcome, feedback. Visual diff confirms no regression; before set captured from M2 `master` baseline first.

## Non-goals

- No visual redesign, no new M3 components adopted for their own sake.
- No dynamic color / Material You.
- No change to `material-icons-extended`.
