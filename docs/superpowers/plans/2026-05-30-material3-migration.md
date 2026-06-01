# Material 3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the whole app from Compose Material 2 (`androidx.compose.material`) to Material 3 (`androidx.compose.material3`) in one PR, with no intended visual regression.

**Architecture:** Keep the existing `ZeroColors`/`LocalZeroColors` token layer (already modeled on M3 roles) as the source of truth. `Theme.kt` builds an M3 `ColorScheme` from those tokens. Components migrate by package swap (`material.` → `material3.`) plus per-component API deltas from the Cookbook below. Both libraries coexist on the classpath during the migration; removing the M2 dependency at the end is the completeness check.

**Tech Stack:** Kotlin 2.3.20, Compose 1.10.5, `androidx.compose.material3`, Dagger, custom Component/ViewModel/ViewProvider architecture.

Spec: `docs/superpowers/specs/2026-05-30-material3-migration-design.md`

---

## Migration Cookbook (shared reference for all module tasks)

Apply these transformations. The dominant move is **mechanical**: change the import package `androidx.compose.material.X` → `androidx.compose.material3.X`, then fix the specific API deltas below. **`androidx.compose.material.icons.*` imports are NOT touched** (icons are theme-agnostic and shared).

**Compiler-driven workflow per module:** swap imports → `./gradlew :<module>:compileDebugKotlin` → fix each reported error using the deltas below → repeat until green.

### Color reads
- `MaterialTheme.colors.primary` → `ZeroTheme.colors.primary`
- `MaterialTheme.colors.background` → `ZeroTheme.colors.surface`
- `MaterialTheme.colors.surface` → `ZeroTheme.colors.surface`
- Remove the now-unused `import androidx.compose.material.MaterialTheme`; add `import com.hluhovskyi.zero.ui.theme.ZeroTheme` if absent.

### Component deltas
- **`Divider`** → `HorizontalDivider` (import `androidx.compose.material3.HorizontalDivider`). Same params (`color`, `thickness`, `modifier`).
- **`DropdownMenuItem`** — M3 requires a `text` slot. M2 `DropdownMenuItem(onClick = …) { Text("x") }` → `DropdownMenuItem(text = { Text("x") }, onClick = …)`. Leading/trailing content moves to `leadingIcon`/`trailingIcon` params.
- **`Surface`** — `elevation = N.dp` splits into `tonalElevation` and `shadowElevation`. Map the existing value to **`shadowElevation`** and leave **`tonalElevation = 0.dp`** so M3's surface tint does not shift the brand surface color.
- **`Button`** — `ButtonDefaults.buttonColors(backgroundColor = …, contentColor = …)` → `ButtonDefaults.buttonColors(containerColor = …, contentColor = …)`.
- **`FloatingActionButton`** — `backgroundColor = …` → `containerColor = …`. `FloatingActionButtonDefaults.elevation(...)` keeps `defaultElevation`/`pressedElevation` param names.
- **`OutlinedTextField`** — `TextFieldDefaults.outlinedTextFieldColors(...)` → `OutlinedTextFieldDefaults.colors(...)`. Param renames: `focusedBorderColor`/`unfocusedBorderColor`/`cursorColor` keep names; `backgroundColor` → `focusedContainerColor` + `unfocusedContainerColor`.
- **`Switch`** — `SwitchDefaults.colors(checkedThumbColor=, checkedTrackColor=, uncheckedThumbColor=, uncheckedTrackColor=, ...)` param names are stable in M3; `uncheckedTrackColor` now pairs with `uncheckedBorderColor` (add if the unchecked track needs no visible border: `uncheckedBorderColor = Color.Transparent`).
- **`Scaffold`** — `backgroundColor = …` → `containerColor = …`. M3 `Scaffold` consumes `WindowInsets` by default; if a screen previously handled its own insets and now double-pads, pass `contentWindowInsets = WindowInsets(0)`. Verify on device.
- **`CircularProgressIndicator`** — same core params; `color` stays.
- **`SnackbarHost` / `SnackbarHostState`** — move to `androidx.compose.material3.*`; API identical.
- **`IconButton` / `Icon` / `Text` / `DropdownMenu`** — pure package swap, no param change.

### Theme-agnostic — DO NOT migrate
- `androidx.compose.material.icons.*` (Icons, Icons.Default.*, etc.) — keep as-is.
- `androidx.compose.foundation.*`, `androidx.compose.ui.*`, `androidx.compose.runtime.*` — unaffected.

---

## Task 1: Add material3 dependency + rebuild the theme

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `zero-ui/build.gradle.kts` (and `zero-core`, `app` build files — wherever `androidx-compose-material` is declared)
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Theme.kt`
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Type.kt`
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Shape.kt`

- [ ] **Step 1: Add the material3 catalog entry.** In `gradle/libs.versions.toml` add a `material3` version and library. Resolve the version empirically — run `./gradlew :zero-ui:dependencies --configuration debugRuntimeClasspath | grep material3` after a first guess, or check the latest `androidx.compose.material3:material3` that resolves against Compose 1.10.5. Add:
  ```toml
  material3 = "1.4.0"   # adjust to whatever resolves; bump until compileDebugKotlin finds the symbols
  androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
  ```
  Keep `androidx-compose-material` and `androidx-compose-materialNavigation` for now. Keep `androidx-compose-materialIconsExtended`.

- [ ] **Step 2: Add `material3` to each module that uses Material.** Add `implementation(libs.androidx.compose.material3)` next to the existing `androidx.compose.material` line in `zero-ui`, `zero-core`, and `app` build files. Do not remove the M2 line yet.

- [ ] **Step 3: Rewrite `Theme.kt` to build an M3 `ColorScheme` from `ZeroColors`.** Replace the M2 `darkColors`/`lightColors` + M2 `MaterialTheme` with:
  ```kotlin
  package com.hluhovskyi.zero.ui.theme

  import androidx.compose.foundation.isSystemInDarkTheme
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.darkColorScheme
  import androidx.compose.material3.lightColorScheme
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.CompositionLocalProvider

  @Composable
  fun ZeroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
      val zeroColors = if (darkTheme) DarkZeroColors else LightZeroColors
      val scheme = if (darkTheme) darkColorScheme(
          primary = zeroColors.primary,
          onPrimary = zeroColors.onPrimary,
          primaryContainer = zeroColors.primaryContainer,
          onPrimaryContainer = zeroColors.onPrimaryContainer,
          inversePrimary = zeroColors.inversePrimary,
          secondary = zeroColors.secondary,
          onSecondary = zeroColors.onSecondary,
          secondaryContainer = zeroColors.secondaryContainer,
          onSecondaryContainer = zeroColors.onSecondaryContainer,
          background = zeroColors.surface,
          onBackground = zeroColors.onSurface,
          surface = zeroColors.surface,
          onSurface = zeroColors.onSurface,
          surfaceVariant = zeroColors.surfaceContainer,
          onSurfaceVariant = zeroColors.onSurfaceVariant,
          surfaceContainerLowest = zeroColors.surfaceContainerLowest,
          surfaceContainerLow = zeroColors.surfaceContainerLow,
          surfaceContainer = zeroColors.surfaceContainer,
          surfaceContainerHigh = zeroColors.surfaceContainerHigh,
          inverseSurface = zeroColors.inverseSurface,
          inverseOnSurface = zeroColors.inverseOnSurface,
          outline = zeroColors.outline,
          outlineVariant = zeroColors.outlineVariant,
          error = zeroColors.error,
          onError = zeroColors.onError,
          errorContainer = zeroColors.errorContainer,
          scrim = zeroColors.scrim,
      ) else lightColorScheme(
          // same arguments, same zeroColors.* fields (LightZeroColors selected above)
          primary = zeroColors.primary,
          onPrimary = zeroColors.onPrimary,
          primaryContainer = zeroColors.primaryContainer,
          onPrimaryContainer = zeroColors.onPrimaryContainer,
          inversePrimary = zeroColors.inversePrimary,
          secondary = zeroColors.secondary,
          onSecondary = zeroColors.onSecondary,
          secondaryContainer = zeroColors.secondaryContainer,
          onSecondaryContainer = zeroColors.onSecondaryContainer,
          background = zeroColors.surface,
          onBackground = zeroColors.onSurface,
          surface = zeroColors.surface,
          onSurface = zeroColors.onSurface,
          surfaceVariant = zeroColors.surfaceContainer,
          onSurfaceVariant = zeroColors.onSurfaceVariant,
          surfaceContainerLowest = zeroColors.surfaceContainerLowest,
          surfaceContainerLow = zeroColors.surfaceContainerLow,
          surfaceContainer = zeroColors.surfaceContainer,
          surfaceContainerHigh = zeroColors.surfaceContainerHigh,
          inverseSurface = zeroColors.inverseSurface,
          inverseOnSurface = zeroColors.inverseOnSurface,
          outline = zeroColors.outline,
          outlineVariant = zeroColors.outlineVariant,
          error = zeroColors.error,
          onError = zeroColors.onError,
          errorContainer = zeroColors.errorContainer,
          scrim = zeroColors.scrim,
      )
      CompositionLocalProvider(LocalZeroColors provides zeroColors) {
          MaterialTheme(colorScheme = scheme, typography = Typography, shapes = Shapes, content = content)
      }
  }
  ```
  (Note: `primaryContainerLight`, `selectedPill`, `transaction*`, `import*`, `welcomeCardLine` have no M3 role — they remain accessed only via `ZeroTheme.colors`, which is unchanged.)

- [ ] **Step 4: Rewrite `Type.kt` for M3 `Typography`.**
  ```kotlin
  import androidx.compose.material3.Typography
  // ...
  val Typography = Typography(
      bodyLarge = TextStyle(
          fontFamily = FontFamily.Default,
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
      ),
  )
  ```

- [ ] **Step 5: Rewrite `Shape.kt` for M3 `Shapes`.** Preserve current radii (small/medium = 4dp, large = 0dp); fill the extra M3 slots from those.
  ```kotlin
  import androidx.compose.material3.Shapes
  // ...
  val Shapes = Shapes(
      extraSmall = RoundedCornerShape(4.dp),
      small = RoundedCornerShape(4.dp),
      medium = RoundedCornerShape(4.dp),
      large = RoundedCornerShape(0.dp),
      extraLarge = RoundedCornerShape(0.dp),
  )
  ```

- [ ] **Step 6: Compile.** Run: `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -25`. Expected: the theme files compile. Other `zero-ui` files still using M2 components also compile (M2 dep still present). Fix any error in the three theme files only.

- [ ] **Step 7: Commit.**
  ```bash
  git add gradle/libs.versions.toml zero-ui/build.gradle.kts zero-core/build.gradle.kts app/build.gradle.kts zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/
  git commit -m "m3: add material3 dependency and rebuild ZeroTheme from tokens"
  ```

---

## Task 2: Migrate `zero-ui` components

**Files:** all 18 non-theme `zero-ui` files importing `androidx.compose.material.*` (see `git grep -l "import androidx.compose.material\." zero-ui/src` minus the icons-only and theme files). Includes the 6 `MaterialTheme.colors` reads in `SegmentedToggle`, `AmountDisplay`, `SelectorCard`, `DatePickerCard`, and the `Divider` usage.

- [ ] **Step 1: Swap imports + apply Cookbook deltas across the module.** For each file, change `androidx.compose.material.X` → `androidx.compose.material3.X` (NOT `material.icons.*`), redirect `MaterialTheme.colors.*` per the Cookbook, and apply component deltas (`Divider` → `HorizontalDivider`, `DropdownMenuItem` text slot, `Surface` elevation split, `OutlinedTextField`/`Button`/`Switch`/`FAB` color params).

- [ ] **Step 2: Compile and iterate.** Run: `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -30`. Fix each reported error using the Cookbook. Repeat until green.

- [ ] **Step 3: Commit.**
  ```bash
  git add zero-ui/src
  git commit -m "m3: migrate zero-ui components to material3"
  ```

---

## Task 3: Migrate `zero-core` components

**Files:** all 35 `zero-core` files importing `androidx.compose.material.*`. Includes the 11 `MaterialTheme.colors` reads (`CurrencyPickerViewProvider`, `TransactionEditViewProvider`, `TransactionEditTransferViewProvider`, `CategoryScrollRow`), the `ExposedDropdownMenuBox` usage (`TransactionEditAccountSelect`/`CurrencySelect`), and the `rememberModalBottomSheetState` sheet (`TransactionFilterSheet`).

- [ ] **Step 1: Swap imports + apply Cookbook deltas** across all 35 files (mechanical package swap + color redirects + `DropdownMenuItem` text slot — this module has the bulk of the 70 dropdown usages).

- [ ] **Step 2: `ExposedDropdownMenuBox` delta.** In M3 the editable text field inside the box needs `.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)` on its `Modifier` (import `androidx.compose.material3.MenuAnchorType`). `ExposedDropdownMenuDefaults` API is otherwise stable.

- [ ] **Step 3: `TransactionFilterSheet` — M2 `ModalBottomSheetState` → M3 `ModalBottomSheet`.** Replace M2 `rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)` + `ModalBottomSheetLayout` wrapper with the M3 overlay form:
  ```kotlin
  import androidx.compose.material3.ModalBottomSheet
  import androidx.compose.material3.rememberModalBottomSheetState
  import androidx.compose.material3.BottomSheetDefaults
  // visible: Boolean drives presence
  if (visible) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
      ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          dragHandle = { BottomSheetDefaults.DragHandle() },
          containerColor = ZeroTheme.colors.surface,
      ) { /* existing sheet content */ }
  }
  ```
  Preserve the half-expanded + visible drag-handle UX (`skipPartiallyExpanded = false` + `BottomSheetDefaults.DragHandle()`). Keep any existing `BackHandler(enabled = visible)`.

- [ ] **Step 4: Compile and iterate.** Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -30`. Fix each error via the Cookbook. Repeat until green.

- [ ] **Step 5: Commit.**
  ```bash
  git add zero-core/src
  git commit -m "m3: migrate zero-core components to material3"
  ```

---

## Task 4: Migrate `app` — bottom bar + nav bottom-sheet

**Files:** `app/src/main/java/com/hluhovskyi/zero/activity/screens/bottombar/BottomBarViewProvider.kt`, `app/src/main/java/com/hluhovskyi/zero/activity/MainActivityViewProvider.kt`, `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt`, `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`.

- [ ] **Step 1: `BottomBarViewProvider` — `BottomNavigation` → `NavigationBar`.** `BottomNavigation { }` → `NavigationBar { }`; `BottomNavigationItem(selectedContentColor = …, unselectedContentColor = …, …)` → `NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = …, unselectedIconColor = …, selectedTextColor = …, unselectedTextColor = …, indicatorColor = …), …)`. Use `ZeroTheme.colors` for the color values (e.g. `indicatorColor = ZeroTheme.colors.selectedPill`).

- [ ] **Step 2: Color reads.** `MainActivityViewProvider` `MaterialTheme.colors.background` → `ZeroTheme.colors.surface`. `MainActivityScreenViewProvider` `sheetBackgroundColor = MaterialTheme.colors.background` is part of the nav-sheet rewrite below.

- [ ] **Step 3: Resolve R1 — nav bottom-sheet (`androidx.compose.material.navigation`).** Investigate, in this order, and apply the first that works:
  1. Check for an M3 nav bottom-sheet artifact compatible with Compose 1.10.5 (`androidx.compose.material3:material3` does not include it; look for an `androidx.compose.material3` navigation-material equivalent or `androidx.navigation:navigation-compose` bottom-sheet support). If one exists, swap `ModalBottomSheetLayout`/`bottomSheet {}`/`BottomSheetNavigator` to it.
  2. If none exists: convert the `bottomSheet {}` destinations to render via M3 `ModalBottomSheet` keyed off the current nav back-stack entry (manual wiring, contained to these 3 files). Dismiss → `navController.popBackStack()`.
  3. Last resort: keep `androidx-compose-materialNavigation` (M2) as an isolated island **only** for the nav-sheet; wrap just that subtree so the M2 `ModalBottomSheetLayout` still has what it needs. Note this as a documented follow-up in the PR body.

- [ ] **Step 4: Compile and iterate.** Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`. Fix each error via the Cookbook. Repeat until green.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src
  git commit -m "m3: migrate app bottom bar and nav bottom-sheet to material3"
  ```

---

## Task 5: Remove the Material 2 dependency (completeness check)

**Files:** `gradle/libs.versions.toml`, `zero-ui/build.gradle.kts`, `zero-core/build.gradle.kts`, `app/build.gradle.kts`.

- [ ] **Step 1: Confirm no remaining M2 component imports.** Run: `git grep -n "import androidx.compose.material\.\([A-Z]\|Scaffold\|Surface\|Button\)" -- '*.kt' | grep -v "material.icons" | grep -v "material3"`. Expected: only `material.navigation.*` if R1 step 3 (island) was taken; otherwise empty.

- [ ] **Step 2: Remove `implementation(libs.androidx.compose.material)`** from all three module build files (keep `material3`, keep `materialIconsExtended`; keep `materialNavigation` only if R1-island was taken). Remove the now-unused catalog entry if fully gone.

- [ ] **Step 3: Full compile.** Run: `./gradlew :app:compileDebugKotlin :zero-core:compileDebugKotlin :zero-ui:compileDebugKotlin 2>&1 | tail -25`. Expected: BUILD SUCCESSFUL. Any failure here means a stray M2 reference — fix via the Cookbook.

- [ ] **Step 4: Commit.**
  ```bash
  git add gradle/libs.versions.toml zero-ui/build.gradle.kts zero-core/build.gradle.kts app/build.gradle.kts
  git commit -m "m3: drop Material 2 dependency"
  ```

---

## Task 6: Verification

- [ ] **Step 1: Unit tests + lint.** Run: `./gradlew testDebugUnitTest lintDebug 2>&1 | tail -25`. Expected: BUILD SUCCESSFUL. Fix any failure (lint may flag deprecated M3 overloads — address them).

- [ ] **Step 2: Install + on-device screenshots.** Build/install the debug app and capture AFTER screenshots (light + dark) of every major screen via `android-ui-inspector`: transactions list, transaction edit (expense/income/transfer), budget/numpad, categories, currency picker, settings, import flow, welcome, feedback. Compare against the M2 baseline captured before execution. Confirm: no missing text/icons, no color regressions, drag handle present on filter sheet, bottom bar renders, nav sheet opens/dismisses.

- [ ] **Step 3: Fix any visual regression** found in Step 2 by adjusting the relevant color role mapping in `Theme.kt` or the specific component's params. Re-screenshot.

---

## Self-Review notes
- **Spec coverage:** dependency (T1), theme/type/shape (T1), 18 color reads (T2/T3/T4), mechanical swap (T2/T3/T4), Divider/DropdownMenuItem/Surface/Button/OTF/Switch/FAB (Cookbook), ExposedDropdownMenuBox (T3), ModalBottomSheet sheet (T3), BottomNavigation (T4), nav bottom-sheet R1 (T4), M2 removal (T5), tests+lint+screenshots (T6). All spec items mapped.
- **Icons untouched** — stated in Cookbook and reinforced in every swap step.
- **Type consistency:** `ZeroTheme.colors`, `ColorScheme`, `Typography`, `Shapes`, `ModalBottomSheet`, `NavigationBar` used consistently across tasks.
