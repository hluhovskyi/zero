# `ZeroThemeBypass` Lint Rule — Implementation Plan

> **For agentic workers:** Mechanical implementation; execute inline (no subagents). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lint error on any `Color(...)` constructor call or `Color.<NamedColor>` reference outside `com.hluhovskyi.zero.ui.theme.*` and `UiColorScheme.kt`.

**Architecture:** UAST scanner detector in `lint-rules/.../ZeroThemeBypassDetector.kt`. Modeled on `HardcodedComposableStringDetector`.

**Spec:** [2026-05-22-zero-theme-bypass-lint-design.md](../specs/2026-05-22-zero-theme-bypass-lint-design.md)

---

## Task 1: Detector

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroThemeBypassDetector.kt`
- Analog: `HardcodedComposableStringDetector.kt` (same scanner pattern, same Issue.create idioms).

Detect both:
1. **Constructor calls** — `UCallExpression` whose resolved method's containing class FQN is `androidx.compose.ui.graphics.ColorKt` and method name is `Color`. The Kotlin top-level `Color(...)` builders in `androidx.compose.ui.graphics` resolve to functions in `ColorKt`.
2. **Named-color refs** — `USimpleNameReferenceExpression` where the resolved element is a property on the `androidx.compose.ui.graphics.Color.Companion` and its name is in `BANNED_NAMES = setOf("White", "Black", "Red", "Green", "Blue", "Gray", "Yellow", "Magenta", "Cyan")`.

Allowlist:
- `context.uastFile?.packageName?.startsWith("com.hluhovskyi.zero.ui.theme") == true` → skip.
- `context.file.name == "UiColorScheme.kt"` → skip.

Report with message `"Use ZeroTheme.colors.<token> instead of constructing or referencing Color directly. See docs/agents/color-scheme.md."` and severity `ERROR`, category `CORRECTNESS`.

- [ ] **Step 1: Create the file** with both visitor branches and the allowlist guard.
- [ ] **Step 2: Build** — `./gradlew :lint-rules:compileKotlin 2>&1 | tail -5`. Expect BUILD SUCCESSFUL.

---

## Task 2: Tests

**Files:**
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/ZeroThemeBypassDetectorTest.kt`
- Analog: `HardcodedComposableStringDetectorTest.kt` (LintDetectorTest skeleton, stub Compose types via `kotlin(...)`).

11 test cases per the spec table. Stub `Color`:

```kotlin
private val colorStub = kotlin(
    """
    package androidx.compose.ui.graphics
    class Color(val value: ULong = 0UL) {
        fun copy(alpha: Float = 0f): Color = this
        companion object {
            val White = Color()
            val Black = Color()
            val Red = Color()
            val Green = Color()
            val Blue = Color()
            val Gray = Color()
            val Yellow = Color()
            val Magenta = Color()
            val Cyan = Color()
            val Unspecified = Color()
            val Transparent = Color()
        }
    }
    fun Color(argb: Int): Color = Color()
    fun Color(argb: Long): Color = Color()
    fun Color(red: Float, green: Float, blue: Float): Color = Color()
    fun Color(red: Float, green: Float, blue: Float, alpha: Float): Color = Color()
    """,
).indented()
```

For each case: a single `lint().files(colorStub, kotlin("…").indented()).run().expectContains("ZeroThemeBypass")` or `.expectClean()`. Allowlist cases override the package declaration.

- [ ] **Step 1: Create the file** with all 11 cases.
- [ ] **Step 2: Run tests** — `./gradlew :lint-rules:test --tests "*ZeroThemeBypass*" 2>&1 | tail -10`. Expect all green.

---

## Task 3: Register in `ZeroIssueRegistry`

**Files:**
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] Add `ZeroThemeBypassDetector.ISSUE` to the `issues` list (alphabetically near the other recent additions).
- [ ] **Sanity build** — `./gradlew :lint-rules:assemble 2>&1 | tail -5`.

---

## Task 4: Suppress the 12 known bespoke violations

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/budget/SummaryBar.kt` (9 vals on lines 37–45)
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/budget/BudgetCard.kt` (3 vals on lines 45–47)

Pattern (per val):

```kotlin
@Suppress("ZeroThemeBypass")
private val SummaryBg = Color(0xFF1A2E52)
```

- [ ] **Step 1: Add suppressions** to both files.
- [ ] **Step 2: Full lint sweep** — `./gradlew lintDebug 2>&1 | grep -E "error:|ZeroThemeBypass" | head -20`. Expect zero remaining matches.

---

## Task 5: End-to-end verification

- [ ] **Lint-rules tests** — `./gradlew :lint-rules:test 2>&1 | tail -10`. All green (no other rule regressed).
- [ ] **Full lintDebug across the project** — `./gradlew lintDebug 2>&1 | tail -20`. BUILD SUCCESSFUL. Zero new errors.
- [ ] **No unit-test regression** — `./gradlew testDebugUnitTest 2>&1 | tail -10`.
- [ ] **Spot grep** — `grep -rEHn "Color\(0x|Color\.White|Color\.Black" app zero-core zero-ui --include='*.kt' | grep -v "theme/ZeroColors.kt" | grep -v "ui/UiColorScheme.kt" | grep -v "/test/" | grep -v "/androidTest/" | grep -v "@Suppress.*ZeroThemeBypass"`. Should return zero — every remaining match is either in an allowlisted file or annotated.

No UI verification step — this PR is purely infrastructural (lint rule + per-file annotations on already-rendering code).
