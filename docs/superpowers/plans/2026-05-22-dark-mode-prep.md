# Dark Mode Prep — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every app color through `ZeroTheme.colors.*` so a future dark palette swaps in without touching callers.

**Architecture:** New `ZeroColors` data class + `LocalZeroColors` CompositionLocal + `ZeroTheme.colors` accessor in `zero-ui/.../theme/`. `Theme.kt` provides it. All hardcoded `Color(0x…)` / `Color.White` / `Color.Black` callsites migrate to semantic tokens. Top-level `val` tokens in `Color.kt` are deleted — the compiler enforces full migration. **Dark palette is a stub that mirrors light** (the actual dark values ship later).

**Tech Stack:** Compose `androidx.compose.material:material` (Material 1), Kotlin.

**Spec:** [docs/superpowers/specs/2026-05-22-dark-mode-prep-design.md](../specs/2026-05-22-dark-mode-prep-design.md)

**Project conventions:** Follow [docs/agents/superpowers-workflow.md](../../agents/superpowers-workflow.md) — no boilerplate, doc-references where possible, every new file names an analog.

---

## Task 1: Create `ZeroColors`, `LocalZeroColors`, and `ZeroTheme.colors` accessor

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/ZeroColors.kt`

**Analog:** structural pattern from `androidx.compose.material.Colors` (immutable data class) + `MaterialTheme` companion accessor.

- [ ] **Step 1: Create the file**

```kotlin
package com.hluhovskyi.zero.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    val scrim: Color,
    val transactionExpense: Color,
    val transactionIncome: Color,
    val importMergeContainer: Color,
    val importNewContainer: Color,
    val importNewContent: Color,
    val importErrorContainer: Color,
    val importErrorContent: Color,
    val welcomeCardLine: Color,
    val isLight: Boolean,
)

val LightZeroColors = ZeroColors(
    primary = Color(0xFF000E2F),
    primaryContainer = Color(0xFF0A2351),
    primaryContainerLight = Color(0xFFC8D8FE),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF778BBF),
    secondary = Color(0xFF006C4A),
    secondaryContainer = Color(0xFF82F5C1),
    onSecondary = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF00714E),
    surface = Color(0xFFFAF8FD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F7),
    surfaceContainer = Color(0xFFEFEDF2),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F5),
    inversePrimary = Color(0xFFB1C6FD),
    selectedPill = Color(0xFFD9E2FF),
    scrim = Color(0x52000000),
    transactionExpense = Color(0xFFBA1A1A),
    transactionIncome = Color(0xFF006C4A),
    importMergeContainer = Color(0xFFE8EEFF),
    importNewContainer = Color(0xFFE8F5E9),
    importNewContent = Color(0xFF1B5E20),
    importErrorContainer = Color(0xFFFFEBEE),
    importErrorContent = Color(0xFF93000A),
    welcomeCardLine = Color(0xFFFFFFFF),
    isLight = true,
)

// TODO(dark-mode): tune these to real dark values. Mirroring light for now keeps
// the wiring honest without committing to a palette before design lands.
val DarkZeroColors = LightZeroColors.copy(isLight = false)

val LocalZeroColors = staticCompositionLocalOf<ZeroColors> {
    error("ZeroColors not provided — wrap content in ZeroTheme")
}

object ZeroTheme {
    val colors: ZeroColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZeroColors.current
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/ZeroColors.kt
git commit -m "theme(zero-ui): add ZeroColors data class + LocalZeroColors + ZeroTheme accessor"
```

---

## Task 2: Wire `ZeroTheme` to provide `LocalZeroColors` and derive `MaterialTheme.colors` from it

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Theme.kt`

`Color.kt` top-level vals stay for now so existing callsites keep compiling — Task 8 deletes them once migrations are done.

- [ ] **Step 1: Rewrite `Theme.kt`**

```kotlin
package com.hluhovskyi.zero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ZeroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val zeroColors = if (darkTheme) DarkZeroColors else LightZeroColors
    val materialColors = if (darkTheme) {
        darkColors(
            primary = zeroColors.inversePrimary,
            primaryVariant = zeroColors.primaryContainer,
            secondary = zeroColors.secondaryContainer,
            background = zeroColors.inverseSurface,
            surface = zeroColors.inverseSurface,
            error = zeroColors.error,
            onPrimary = zeroColors.primary,
            onSecondary = zeroColors.onSecondaryContainer,
            onBackground = zeroColors.inverseOnSurface,
            onSurface = zeroColors.inverseOnSurface,
            onError = zeroColors.onError,
        )
    } else {
        lightColors(
            primary = zeroColors.primaryContainer,
            primaryVariant = zeroColors.primary,
            secondary = zeroColors.secondary,
            secondaryVariant = zeroColors.secondaryContainer,
            background = zeroColors.surface,
            surface = zeroColors.surface,
            error = zeroColors.error,
            onPrimary = zeroColors.onPrimary,
            onSecondary = zeroColors.onSecondary,
            onBackground = zeroColors.onSurface,
            onSurface = zeroColors.onSurface,
            onError = zeroColors.onError,
        )
    }
    CompositionLocalProvider(LocalZeroColors provides zeroColors) {
        MaterialTheme(
            colors = materialColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (warning that the top-level `Color.kt` consts are unused-by-Theme is fine — callsites still use them).

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Theme.kt
git commit -m "theme(zero-ui): provide LocalZeroColors from ZeroTheme; derive MaterialTheme.Colors from it"
```

---

## Tasks 3–7: Migrate callsites by area

> All five tasks are independent and can run in parallel. Each follows the same mechanical rule: every reference to a top-level token (`Primary`, `OnSurface`, …) or hardcoded literal (`Color(0xFF…)`, `Color.White`, `Color.Black`) becomes `ZeroTheme.colors.<token>`. **The mapping table is binding** — pick from it, don't improvise:

**Mapping (from the spec, lifted here so executors don't task-jump):**

| Source | Replace with |
|---|---|
| `Primary` token / `Color(0xFF000E2F)` | `ZeroTheme.colors.primary` |
| `PrimaryContainer` / `Color(0xFF0A2351)` | `ZeroTheme.colors.primaryContainer` |
| `PrimaryContainerLight` / `Color(0xFFC8D8FE)` | `ZeroTheme.colors.primaryContainerLight` |
| `OnPrimary` / `Color(0xFFFFFFFF)` as text-on-coloured-surface | `ZeroTheme.colors.onPrimary` |
| `OnPrimaryContainer` / `Color(0xFF778BBF)` | `ZeroTheme.colors.onPrimaryContainer` |
| `Secondary` / `Color(0xFF006C4A)` | `ZeroTheme.colors.secondary` |
| `SecondaryContainer` / `Color(0xFF82F5C1)` | `ZeroTheme.colors.secondaryContainer` |
| `OnSecondary` | `ZeroTheme.colors.onSecondary` |
| `OnSecondaryContainer` / `Color(0xFF00714E)` | `ZeroTheme.colors.onSecondaryContainer` |
| `Surface` / `Color(0xFFFAF8FD)` | `ZeroTheme.colors.surface` |
| `SurfaceContainerLowest` / `Color(0xFFFFFFFF)` as opaque bg | `ZeroTheme.colors.surfaceContainerLowest` |
| `SurfaceContainerLow` / `Color(0xFFF5F3F7)` | `ZeroTheme.colors.surfaceContainerLow` |
| `SurfaceContainer` / `Color(0xFFEFEDF2)` | `ZeroTheme.colors.surfaceContainer` |
| `SurfaceContainerHigh` / `Color(0xFFE9E7EC)` | `ZeroTheme.colors.surfaceContainerHigh` |
| `OnSurface` / `Color(0xFF1B1B1F)` (text) | `ZeroTheme.colors.onSurface` |
| `OnSurfaceVariant` / `Color(0xFF44464F)` (text/icon) | `ZeroTheme.colors.onSurfaceVariant` |
| `Outline` / `Color(0xFF757780)` | `ZeroTheme.colors.outline` |
| `OutlineVariant` / `Color(0xFFC5C6D0)` | `ZeroTheme.colors.outlineVariant` |
| `Error` / `Color(0xFFBA1A1A)` | `ZeroTheme.colors.error` |
| `ErrorContainer` / `Color(0xFFFFDAD6)` | `ZeroTheme.colors.errorContainer` |
| `OnError` | `ZeroTheme.colors.onError` |
| `InverseSurface` / `Color(0xFF303034)` | `ZeroTheme.colors.inverseSurface` |
| `InverseOnSurface` / `Color(0xFFF2F0F5)` | `ZeroTheme.colors.inverseOnSurface` |
| `InversePrimary` / `Color(0xFFB1C6FD)` | `ZeroTheme.colors.inversePrimary` |
| `SelectedPill` / `Color(0xFFD9E2FF)` | `ZeroTheme.colors.selectedPill` |
| `Color.Black.copy(alpha = 0.32f)` (scrim) | `ZeroTheme.colors.scrim` |
| `Color(0x40000000)` (overlay) | `ZeroTheme.colors.scrim` |
| `Color(0xFFE53935)` (badge) | `ZeroTheme.colors.error` |
| `Color(0xFFFFEBEE)` (import error bg) | `ZeroTheme.colors.importErrorContainer` |
| `Color(0xFF93000A)` (import error text) | `ZeroTheme.colors.importErrorContent` |
| `Color(0xFFE8EEFF)` (chip / iconBg) | `ZeroTheme.colors.importMergeContainer` |
| `Color(0xFFE8F5E9)` (chip bg) | `ZeroTheme.colors.importNewContainer` |
| `Color(0xFF1B5E20)` (chip icon) | `ZeroTheme.colors.importNewContent` |
| `Color(0xFF5DDBA8)` (budget OK tint, `BudgetViewProvider:658`) | `ZeroTheme.colors.transactionIncome` |
| `Color.White` as text/tint on coloured surface | `ZeroTheme.colors.onPrimary` |
| `Color.White` as standalone opaque background | `ZeroTheme.colors.surfaceContainerLowest` |
| `Color.White.copy(alpha = X)` in `WelcomeViewProvider` | `ZeroTheme.colors.welcomeCardLine.copy(alpha = X)` |
| `Color(0xFF1B1B1F)` as `TransactionExpenseView` default | `ZeroTheme.colors.onSurface` (move default to the call site — see Task 4 note) |

**Per-task contract:** every task must end with **zero** matches for `Color(0x` / `Color.White` / `Color.Black` / `Color\.Red` / direct theme-token imports in the files it owns. Verify before committing:

```bash
grep -EHn "Color\(0x|Color\.White|Color\.Black|import com\.hluhovskyi\.zero\.ui\.theme\.(Primary|Secondary|Surface|OnSurface|OnSurfaceVariant|Outline|OutlineVariant|Error|InverseSurface|InversePrimary|SelectedPill|PrimaryContainer|OnPrimary|OnPrimaryContainer|SecondaryContainer|OnSecondary|OnSecondaryContainer|SurfaceContainer|SurfaceContainerLow|SurfaceContainerLowest|SurfaceContainerHigh|InverseOnSurface|ErrorContainer|OnError|PrimaryContainerLight)" <files-you-touched>
```

Must return zero lines.

**Compose default-arg rule:** function-signature defaults cannot read `CompositionLocal`s. When a `@Composable` has e.g. `color: Color = Color(0xFF1B1B1F)`, change the parameter to `color: Color = ZeroTheme.colors.onSurface` **only** if the enclosing function is itself `@Composable` (default expressions of `@Composable` functions can call composables). If not feasible, hoist the default to the call site.

---

### Task 3: Migrate `zero-ui` module

**Files:** (Modify each)
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ZeroFab.kt:35`
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt:42`
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportErrorBanner.kt:48,71,88`
- `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt:55,69,93,110,116`
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SegmentedToggle.kt` (already uses MaterialTheme; check `Outline`/`SurfaceContainer` direct imports)
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountDisplay.kt` (check direct token imports)
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SelectorCard.kt` (check direct token imports)
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DatePickerCard.kt` (check direct token imports)
- Any other `zero-ui/.../*.kt` that the verification grep flags

**Do not touch** `UiColorScheme.kt` — its `default()` is an unrelated domain (entity `ColorScheme`) fallback, explicitly out of scope per spec.

- [ ] **Step 1: Run the verification grep across `zero-ui` to enumerate everything**

```bash
grep -rEHn "Color\(0x|Color\.White|Color\.Black|import com\.hluhovskyi\.zero\.ui\.theme\.(Primary|Secondary|Surface|OnSurface|OnSurfaceVariant|Outline|OutlineVariant|Error|InverseSurface|InversePrimary|SelectedPill|PrimaryContainer|OnPrimary|OnPrimaryContainer|SecondaryContainer|OnSecondary|OnSecondaryContainer|SurfaceContainer|SurfaceContainerLow|SurfaceContainerLowest|SurfaceContainerHigh|InverseOnSurface|ErrorContainer|OnError|PrimaryContainerLight)" zero-ui/src/main --include='*.kt' | grep -v "theme/Color.kt" | grep -v "theme/Theme.kt" | grep -v "theme/ZeroColors.kt" | grep -v "UiColorScheme.kt"
```

- [ ] **Step 2: Migrate each match using the mapping table**

For files reachable from non-`@Composable` defaults (e.g., `TransactionExpenseView`'s `amountColor: Color = Color(0xFF1B1B1F)`): change the default to `ZeroTheme.colors.onSurface` if the function is `@Composable` (it is); otherwise hoist.

- [ ] **Step 3: Re-run verification grep — must be empty**

- [ ] **Step 4: Build**

```bash
./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero
git commit -m "theme(zero-ui): route hardcoded colors through ZeroTheme.colors"
```

---

### Task 4: Migrate `app` module

**Files:**
- `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt:66` — scrim
- `app/src/main/java/com/hluhovskyi/zero/activity/screens/bottombar/BottomBarViewProvider.kt:47,52,53,58,59` — entire bottom-bar palette

- [ ] **Step 1: Verification grep on `app/src/main`**

```bash
grep -rEHn "Color\(0x|Color\.White|Color\.Black|import com\.hluhovskyi\.zero\.ui\.theme\.(Primary|Secondary|Surface|OnSurface|OnSurfaceVariant|Outline|OutlineVariant|Error|InverseSurface|InversePrimary|SelectedPill|PrimaryContainer|OnPrimary|OnPrimaryContainer|SecondaryContainer|OnSecondary|OnSecondaryContainer|SurfaceContainer|SurfaceContainerLow|SurfaceContainerLowest|SurfaceContainerHigh|InverseOnSurface|ErrorContainer|OnError|PrimaryContainerLight)" app/src/main --include='*.kt'
```

- [ ] **Step 2: Migrate** per mapping table.

- [ ] **Step 3: Re-grep — empty.**
- [ ] **Step 4: Build:** `./gradlew :app:compileDebugKotlin 2>&1 | tail -10`
- [ ] **Step 5: Commit:** `git add app/src/main && git commit -m "theme(app): route bottom bar + scrim through ZeroTheme.colors"`

---

### Task 5: Migrate `zero-core/imports/*`

**Files:**
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportStrategyChip.kt:38,39`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt:181`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt:124,131,132`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt:48,112`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt:50,117`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewProvider.kt:113,129,145`

Note `TransactionsPreviewViewProvider:145` — `amountColor = if (isIncome) Secondary else Color(0xFF1B1B1F)` → `if (isIncome) ZeroTheme.colors.transactionIncome else ZeroTheme.colors.onSurface`.

- [ ] **Step 1: Verification grep on `zero-core/src/main/java/com/hluhovskyi/zero/imports`**
- [ ] **Step 2: Migrate** per mapping table; the file-local `private val MergeBackground = Color(0xFFE8EEFF)` and `NewBackground = Color(0xFFE8F5E9)` in `ImportStrategyChip.kt` and the equivalent `ExistsBadgeBackground` consts in review providers become inline reads inside their `@Composable`s.
- [ ] **Step 3: Re-grep — empty.**
- [ ] **Step 4: Build:** `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
- [ ] **Step 5: Commit:** `git add zero-core/src/main/java/com/hluhovskyi/zero/imports && git commit -m "theme(zero-core/imports): route hardcoded colors through ZeroTheme.colors"`

---

### Task 6: Migrate `zero-core/transactions/*`

**Files:**
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilterSheet.kt:268,297,532,540,547`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt:198,242,252,328,347,378,446,452,476,486,576,582,589`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt:148,154`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt:104`

- [ ] **Step 1: Verification grep on `zero-core/src/main/java/com/hluhovskyi/zero/transactions`**
- [ ] **Step 2: Migrate** per mapping table.
- [ ] **Step 3: Re-grep — empty.**
- [ ] **Step 4: Build:** `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
- [ ] **Step 5: Commit:** `git add zero-core/src/main/java/com/hluhovskyi/zero/transactions && git commit -m "theme(zero-core/transactions): route hardcoded colors through ZeroTheme.colors"`

---

### Task 7: Migrate `zero-core/welcome`, `categories`, `budget`, `currencies`

**Files:**
- `zero-core/src/main/java/com/hluhovskyi/zero/welcome/WelcomeViewProvider.kt:176,177,178,187`
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt:212,220,311`
- `zero-core/src/main/java/com/hluhovskyi/zero/budget/BudgetViewProvider.kt:197,658`
- Any `zero-core/.../currencies/**` files flagged by grep

`WelcomeViewProvider` lines 176–187: keep the alpha values, swap base `Color.White` → `ZeroTheme.colors.welcomeCardLine`.

`CategoryViewProvider:212`: `val tintedBg = lerp(Color.White, categoryBg, 0.45f)` → `lerp(ZeroTheme.colors.surfaceContainerLowest, categoryBg, 0.45f)`.

- [ ] **Step 1: Verification grep on those subpaths**
- [ ] **Step 2: Migrate** per mapping table.
- [ ] **Step 3: Re-grep — empty.**
- [ ] **Step 4: Build:** `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
- [ ] **Step 5: Commit:** `git add zero-core/src/main/java/com/hluhovskyi/zero && git commit -m "theme(zero-core): route welcome/categories/budget/currencies colors through ZeroTheme.colors"`

---

## Task 8: Delete top-level token consts from `Color.kt`

This is the compiler-enforced safety net. If any migration was missed, the build breaks here.

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Color.kt`

- [ ] **Step 1: Replace file contents with**

```kotlin
package com.hluhovskyi.zero.ui.theme

// Color tokens live on ZeroColors. Read them via ZeroTheme.colors.<token>.
// See ZeroColors.kt and docs/agents/color-scheme.md.
```

- [ ] **Step 2: Full module build**

```bash
./gradlew assembleDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. Any unresolved-reference error names a file that was missed in Tasks 3–7 — go fix that file in a separate commit, then re-run.

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/Color.kt
git commit -m "theme(zero-ui): retire top-level color tokens — ZeroTheme.colors is the only entry"
```

---

## Task 9: Document the new theming entry point

**Files:**
- Modify: `docs/agents/color-scheme.md`

- [ ] **Step 1: Prepend a new "App theming" section** (keep the existing entity `ColorScheme` section as-is below it).

```markdown
## App theming

- `ZeroTheme.colors.<token>` is the **only** way to read app colors from a `@Composable`. Reading `MaterialTheme.colors.*` works for Material widgets but does not expose the extra tokens (`surfaceContainerHigh`, `selectedPill`, `importNewContainer`, …).
- Top-level color consts in `theme/Color.kt` are deleted on purpose. If you need a new semantic token, add it to `ZeroColors` and set values in `LightZeroColors` + `DarkZeroColors`.
- `DarkZeroColors` is a stub mirroring light. The real dark palette ships as a follow-up — do not assume dark-mode visuals are tuned.
- Function-signature defaults can read `ZeroTheme.colors.*` only inside `@Composable`s. For non-composable defaults, hoist the default to the call site.

---
```

- [ ] **Step 2: Commit**

```bash
git add docs/agents/color-scheme.md
git commit -m "docs(color-scheme): document ZeroTheme.colors as the app theming entry"
```

---

## Task 10: End-to-end verification

- [ ] **Step 1: Full repo sweep — must return zero matches outside `theme/`**

```bash
grep -rEHn "Color\(0x|Color\.White|Color\.Black|import com\.hluhovskyi\.zero\.ui\.theme\.(Primary|Secondary|Surface|OnSurface|OnSurfaceVariant|Outline|OutlineVariant|Error|InverseSurface|InversePrimary|SelectedPill|PrimaryContainer|OnPrimary|OnPrimaryContainer|SecondaryContainer|OnSecondary|OnSecondaryContainer|SurfaceContainer|SurfaceContainerLow|SurfaceContainerLowest|SurfaceContainerHigh|InverseOnSurface|ErrorContainer|OnError|PrimaryContainerLight)" app zero-core zero-ui --include='*.kt' | grep -v "theme/Color.kt" | grep -v "theme/Theme.kt" | grep -v "theme/ZeroColors.kt" | grep -v "ui/UiColorScheme.kt" | grep -v "/test/" | grep -v "/androidTest/"
```

Expected: no output.

- [ ] **Step 2: Unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: all tests green.

- [ ] **Step 3: Lint**

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Expected: no new errors.

- [ ] **Step 4: On-device UI parity check**

Acquire emulator (`./scripts/emulator/acquire`), install debug build, run `zero-project:android-ui-inspector`. Spot-check: bottom bar, transactions list, import flow chips, welcome cards, transaction edit. Visual parity with master is expected — dark palette stubs mirror light, so the only changes should be invisible.

If anything looks off, the migration mapping picked the wrong semantic token for that spot — fix the specific callsite and recommit.
