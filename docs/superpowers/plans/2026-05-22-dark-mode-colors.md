# Dark Mode Colors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `DarkZeroColors` stub with the real dark palette from the design system and wire up `values-night/themes.xml` for the status bar.

**Architecture:** Single source of truth — `DarkZeroColors` in `zero-ui/.../theme/ZeroColors.kt`. The prep refactor (#241) already routed every callsite through `ZeroTheme.colors.*`, so flipping the palette values automatically updates the whole app. Status bar comes from Android resources, not Compose, so it needs a parallel `values-night/themes.xml`.

**Tech Stack:** Kotlin, Jetpack Compose, Android resource qualifiers.

---

### Task 1: Replace `DarkZeroColors` stub with real palette

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/ZeroColors.kt`

**Why these values:** see `docs/superpowers/specs/2026-05-22-dark-mode-colors-design.md` "Mapping" section. Each `Color(0x…)` below corresponds to a row in that table.

- [ ] **Step 1: Replace the stub `DarkZeroColors` declaration**

Current (lines 86–88):

```kotlin
// TODO(dark-mode): tune these to real dark values. Mirroring light for now keeps
// the wiring honest without committing to a palette before design lands.
val DarkZeroColors = LightZeroColors.copy(isLight = false)
```

Replace with:

```kotlin
val DarkZeroColors = ZeroColors(
    primary = Color(0xFFB1C6FD),
    primaryContainer = Color(0xFF2D4B7E),
    primaryContainerLight = Color(0xFF4F6FA8),
    onPrimary = Color(0xFF00132C),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF65D9A6),
    secondaryContainer = Color(0xFF005237),
    onSecondary = Color(0xFF003824),
    onSecondaryContainer = Color(0xFF82F5C1),
    surface = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF1B1D24),
    surfaceContainerLow = Color(0xFF181A20),
    surfaceContainer = Color(0xFF22252D),
    surfaceContainerHigh = Color(0xFF2A2D35),
    onSurface = Color(0xFFE3E2E9),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF44464F),
    error = Color(0xFFE5564C),
    errorContainer = Color(0xFF5A1F1B),
    onError = Color(0xFF680003),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Color(0xFF000E2F),
    selectedPill = Color(0xFF2D4B7E),
    scrim = Color(0x52000000),
    transactionExpense = Color(0xFFE5564C),
    transactionIncome = Color(0xFF65D9A6),
    importMergeContainer = Color(0xFF15193A),
    importNewContainer = Color(0xFF0E2A12),
    importNewContent = Color(0xFF7FD18C),
    importErrorContainer = Color(0xFF3A0F12),
    importErrorContent = Color(0xFFFFB4AB),
    welcomeCardLine = Color(0xFFFFFFFF),
    isLight = false,
)
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :zero-ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no missing fields — the existing `ZeroColors` data class has 36 fields and we're providing 36 named arguments).

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/ZeroColors.kt
git commit -m "theme(zero-ui): fill DarkZeroColors with design palette"
```

---

### Task 2: Add `values-night/themes.xml` for dark status bar

**Files:**
- Create: `app/src/main/res/values-night/themes.xml`

- [ ] **Step 1: Create the night-qualified theme**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.Zero" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#111318</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

`#111318` matches `DARK_PALETTE.surface` so the status bar blends with the app's top edge. The parent flips from `Material.Light.NoActionBar` to `Material.NoActionBar`, and `windowLightStatusBar=false` switches the system icons to light foreground for legibility against the dark bar.

- [ ] **Step 2: Verify both theme files resolve**

Run: `./gradlew :app:processDebugMainManifest`
Expected: BUILD SUCCESSFUL — confirms the new resource qualifier is picked up.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values-night/themes.xml
git commit -m "theme(app): add values-night/themes.xml for dark status bar"
```

---

### Task 3: Update `docs/agents/color-scheme.md`

**Files:**
- Modify: `docs/agents/color-scheme.md:7`

- [ ] **Step 1: Drop the "stub mirroring light" warning**

Current line 7:

```markdown
- `DarkZeroColors` is a stub mirroring light. The real dark palette ships as a follow-up — do not assume dark-mode visuals are tuned.
```

Replace with:

```markdown
- `DarkZeroColors` holds the tuned dark palette. Status bar dark mode comes from `app/src/main/res/values-night/themes.xml` (Android resource qualifier, not Compose).
```

- [ ] **Step 2: Commit**

```bash
git add docs/agents/color-scheme.md
git commit -m "docs(color-scheme): note dark palette is tuned, document night status bar"
```

---

### Task 4: Verify (tests + lint)

- [ ] **Step 1: Run unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (no logic changes, no test changes).

- [ ] **Step 2: Run lint**

Run: `./gradlew lintDebug`
Expected: BUILD SUCCESSFUL or only pre-existing warnings (no new errors).

UI inspection (toggling `cmd uimode night`) happens in the parent lets-do verification step, not as a plan task.
