# Test-Bridge Boundary Lint Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two custom Android Lint rules that enforce the `zero-test-bridge` boundary at build time — Rule 1 stops `app/androidTest` from importing production internals (everything must go through `zero-test-bridge` or `MainActivity`), Rule 2 stops `zero-test-bridge` itself from absorbing test-framework imports (it ships in the production APK).

**Architecture:** Two detectors in `:lint-rules` modelled on `DatabaseComponentEncapsulationDetector` / `RemoteComponentEncapsulationDetector`. Both visit `UImportStatement` nodes. They self-scope by file path so wiring is forgiving:

- `TestBridgeBoundaryDetector` no-ops unless `context.file.path` contains `/src/androidTest/`; otherwise resolves each import to a `PsiClass`, inspects the resolved class's `containingFile.virtualFile.path`, and fires when that path is in a `/src/main/` directory (== production) and the FQN is not on the allowlist (`com.hluhovskyi.zero.testbridge.*` or `com.hluhovskyi.zero.activity.MainActivity`). Third-party deps (AndroidX, JUnit, kotlin stdlib) come from jars and have no `/src/main/` substring — naturally exempt. AndroidTest helpers resolve to a `/src/androidTest/` file — also naturally exempt.
- `TestBridgeProductionPurityDetector` no-ops unless `context.file.path` contains `/zero-test-bridge/src/main/`; otherwise checks each import's FQN against a forbidden-prefix list (`org.junit.`, `androidx.test.`, `androidx.compose.ui.test.`, `kotlin.test.`, `org.mockito.`, `io.mockk.`).

The detectors register in `ZeroIssueRegistry`. `:app` already has `lintChecks project(":lint-rules")`; `:zero-test-bridge` needs that line added so Rule 2 fires on its module's lint task.

**Tech Stack:** Android Lint 32.1.0, UAST, Kotlin, JUnit (lint-tests harness).

---

## File Structure

- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetector.kt`
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetectorTest.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt` — append two ISSUE entries
- Modify: `zero-test-bridge/build.gradle` — add `lintChecks project(":lint-rules")`
- Modify: `lint-rules/AGENTS.md` — add one bullet per new rule under a "Current rules" pointer (or extend the existing "Adding a Rule" section). Keep terse.

Structural template: `DatabaseComponentEncapsulationDetector` and its tests (none exist yet — model tests after `ScopedComponentBuilderDetectorTest`).

---

## Task 1: `TestBridgeBoundaryDetector` — Rule 1 (androidTest may not reach past zero-test-bridge)

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Write the failing test**

`lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetectorTest.kt`:

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class TestBridgeBoundaryDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = TestBridgeBoundaryDetector()
    override fun getIssues(): List<Issue> = listOf(TestBridgeBoundaryDetector.ISSUE)

    // Production stub — note the explicit `app/src/main/...` path so the
    // detector's source-set check (containingFile path contains "/src/main/")
    // classifies it as production.
    private val prodRepoStub = kotlin(
        "app/src/main/java/com/hluhovskyi/zero/accounts/AccountRepository.kt",
        """
        package com.hluhovskyi.zero.accounts
        class AccountRepository
        """,
    ).indented()

    private val testBridgeStub = kotlin(
        "zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/DatabaseTestBridge.kt",
        """
        package com.hluhovskyi.zero.testbridge
        interface DatabaseTestBridge
        """,
    ).indented()

    private val mainActivityStub = kotlin(
        "app/src/main/java/com/hluhovskyi/zero/activity/MainActivity.kt",
        """
        package com.hluhovskyi.zero.activity
        class MainActivity
        """,
    ).indented()

    private val otherAndroidTestStub = kotlin(
        "app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionsRobot.kt",
        """
        package com.hluhovskyi.zero.robots
        class TransactionsRobot
        """,
    ).indented()

    fun `test flags androidTest import of production repository`() {
        lint()
            .files(
                prodRepoStub,
                kotlin(
                    "app/src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.accounts.AccountRepository
                    class SomeTest { val repo: AccountRepository? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("TestBridgeBoundary")
    }

    fun `test allows androidTest import of zero-test-bridge type`() {
        lint()
            .files(
                testBridgeStub,
                kotlin(
                    "app/src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.testbridge.DatabaseTestBridge
                    class SomeTest { val bridge: DatabaseTestBridge? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows androidTest import of MainActivity`() {
        lint()
            .files(
                mainActivityStub,
                kotlin(
                    "app/src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.activity.MainActivity
                    class SomeTest { val activity: MainActivity? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows androidTest robot importing another androidTest robot`() {
        lint()
            .files(
                otherAndroidTestStub,
                kotlin(
                    "app/src/androidTest/java/com/hluhovskyi/zero/AnotherRobot.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.robots.TransactionsRobot
                    class AnotherRobot { val r: TransactionsRobot? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test ignores files outside androidTest source set`() {
        lint()
            .files(
                prodRepoStub,
                kotlin(
                    "app/src/main/java/com/hluhovskyi/zero/SomeProdFile.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.accounts.AccountRepository
                    class SomeProdFile { val repo: AccountRepository? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :lint-rules:test --tests 'com.hluhovskyi.zero.lint.TestBridgeBoundaryDetectorTest' -i`

Expected: All five tests fail with "Unresolved reference: TestBridgeBoundaryDetector" (class doesn't exist yet).

- [ ] **Step 3: Implement detector**

`lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetector.kt`:

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class TestBridgeBoundaryDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            // Self-scope: only enforce inside app/src/androidTest/**.
            val ourPath = context.file.path.replace('\\', '/')
            if ("/src/androidTest/" !in ourPath) return

            val resolved = node.resolve() as? PsiClass ?: return
            val containingPath = resolved.containingFile
                ?.virtualFile?.path?.replace('\\', '/') ?: return

            // Production iff the resolved class lives under any module's /src/main/.
            // Jars (AndroidX, JUnit, kotlin stdlib) and androidTest helpers
            // never match this substring.
            if ("/src/main/" !in containingPath) return

            val fqn = resolved.qualifiedName ?: return
            if (isAllowed(fqn)) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Tests must go through zero-test-bridge — add a method to DatabaseTestBridge " +
                    "(or a new bridge interface) instead of importing $fqn directly.",
            )
        }
    }

    private fun isAllowed(fqn: String): Boolean =
        fqn.startsWith("com.hluhovskyi.zero.testbridge.") ||
            fqn == "com.hluhovskyi.zero.activity.MainActivity"

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "TestBridgeBoundary",
            briefDescription = "androidTest may not import production internals",
            explanation = "Code under app/src/androidTest/** must only reach into production through " +
                "zero-test-bridge (com.hluhovskyi.zero.testbridge.*) or the activity under test " +
                "(MainActivity). If a test needs new production state, grow the DatabaseTestBridge " +
                "interface (or add a new bridge) rather than importing repositories, components, or " +
                "other prod internals directly. See zero-test-bridge/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                TestBridgeBoundaryDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
```

- [ ] **Step 4: Register issue**

In `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`, add `TestBridgeBoundaryDetector.ISSUE` to the `issues` list (preserve alphabetic-ish grouping with related detectors — append near `RemoteComponentEncapsulationDetector.ISSUE`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :lint-rules:test --tests 'com.hluhovskyi.zero.lint.TestBridgeBoundaryDetectorTest' -i`

Expected: All five tests pass.

If a test fails because the lint test sandbox normalizes paths differently than expected (e.g. resolved class's `containingFile.virtualFile.path` does not include `/src/main/`), fall back to checking the *file name's parent directory chain* via `resolved.containingFile?.virtualFile?.parent?.path` and re-run. Do **not** weaken to package-name matching — the AGENTS.md guidance is explicit.

- [ ] **Step 6: Commit**

```bash
git add lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetector.kt \
        lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeBoundaryDetectorTest.kt \
        lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt
git commit -m "lint: add TestBridgeBoundary detector for app/androidTest"
git push
```

---

## Task 2: `TestBridgeProductionPurityDetector` — Rule 2 (zero-test-bridge must not import test frameworks)

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Write the failing test**

`lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetectorTest.kt`:

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class TestBridgeProductionPurityDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = TestBridgeProductionPurityDetector()
    override fun getIssues(): List<Issue> = listOf(TestBridgeProductionPurityDetector.ISSUE)

    private val junitStub = kotlin(
        "stubs/org/junit/Test.kt",
        """
        package org.junit
        annotation class Test
        """,
    ).indented()

    private val androidxTestStub = kotlin(
        "stubs/androidx/test/core/app/ApplicationProvider.kt",
        """
        package androidx.test.core.app
        class ApplicationProvider
        """,
    ).indented()

    fun `test flags zero-test-bridge file importing org junit`() {
        lint()
            .files(
                junitStub,
                kotlin(
                    "zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/Bad.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import org.junit.Test
                    class Bad { val t: Test? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("TestBridgeProductionPurity")
    }

    fun `test flags zero-test-bridge file importing androidx test`() {
        lint()
            .files(
                androidxTestStub,
                kotlin(
                    "zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/Bad.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import androidx.test.core.app.ApplicationProvider
                    class Bad { val p: ApplicationProvider? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("TestBridgeProductionPurity")
    }

    fun `test allows zero-test-bridge file with production-only imports`() {
        lint()
            .files(
                kotlin(
                    "zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/Ok.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import kotlin.collections.List
                    class Ok { val xs: List<String> = emptyList() }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test ignores files outside zero-test-bridge main source set`() {
        lint()
            .files(
                junitStub,
                kotlin(
                    "app/src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import org.junit.Test
                    class SomeTest { val t: Test? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :lint-rules:test --tests 'com.hluhovskyi.zero.lint.TestBridgeProductionPurityDetectorTest' -i`

Expected: All four tests fail with "Unresolved reference: TestBridgeProductionPurityDetector".

- [ ] **Step 3: Implement detector**

`lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetector.kt`:

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class TestBridgeProductionPurityDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            val ourPath = context.file.path.replace('\\', '/')
            if ("/zero-test-bridge/src/main/" !in ourPath) return

            val fqn = node.importReference?.asSourceString() ?: return
            val matched = FORBIDDEN_PREFIXES.firstOrNull { fqn.startsWith(it) } ?: return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "zero-test-bridge ships in the production APK — test framework code belongs " +
                    "in app/androidTest, not here. Forbidden import: $fqn (matched prefix $matched).",
            )
        }
    }

    companion object {
        private val FORBIDDEN_PREFIXES = listOf(
            "org.junit.",
            "androidx.test.",
            "androidx.compose.ui.test.",
            "kotlin.test.",
            "org.mockito.",
            "io.mockk.",
        )

        val ISSUE: Issue = Issue.create(
            id = "TestBridgeProductionPurity",
            briefDescription = "zero-test-bridge must not import test framework code",
            explanation = "The :zero-test-bridge module is a regular implementation dependency of " +
                ":app and ships into the production APK. Test-framework symbols (JUnit, AndroidX-test, " +
                "Compose-test, kotlin.test, Mockito, MockK) belong in app/androidTest, not here. " +
                "See zero-test-bridge/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                TestBridgeProductionPurityDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
```

- [ ] **Step 4: Register issue**

Add `TestBridgeProductionPurityDetector.ISSUE` to `ZeroIssueRegistry.issues` (next to Task 1's entry).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :lint-rules:test --tests 'com.hluhovskyi.zero.lint.TestBridgeProductionPurityDetectorTest' -i`

Expected: All four tests pass.

If `node.importReference?.asSourceString()` is null in the test sandbox for some imports (UAST can be coy about Kotlin import references), fall back to `(node.sourcePsi as? org.jetbrains.kotlin.psi.KtImportDirective)?.importedFqName?.asString()`. Same enforcement, different access path. Do not switch to scanning usage sites — the spec says imports.

- [ ] **Step 6: Commit**

```bash
git add lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetector.kt \
        lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/TestBridgeProductionPurityDetectorTest.kt \
        lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt
git commit -m "lint: add TestBridgeProductionPurity detector for zero-test-bridge"
git push
```

---

## Task 3: Wire `lintChecks` in `:zero-test-bridge` and update AGENTS.md

**Files:**
- Modify: `zero-test-bridge/build.gradle`
- Modify: `lint-rules/AGENTS.md`

- [ ] **Step 1: Wire lint-rules into `:zero-test-bridge`**

In `zero-test-bridge/build.gradle`, inside the `dependencies` block, add:

```groovy
lintChecks project(":lint-rules")
```

Place it as the first dependency in the block to match the convention in `app/build.gradle:102`.

- [ ] **Step 2: Update `lint-rules/AGENTS.md`**

Append at the bottom (or merge into a "Current rules" section if you choose to add one):

```markdown
## Current rules (boundary enforcement)

- **TestBridgeBoundary** — app/src/androidTest/** may only import production code through `com.hluhovskyi.zero.testbridge.*` or `com.hluhovskyi.zero.activity.MainActivity`.
- **TestBridgeProductionPurity** — zero-test-bridge/src/main/** must not import `org.junit.*`, `androidx.test.*`, `androidx.compose.ui.test.*`, `kotlin.test.*`, `org.mockito.*`, or `io.mockk.*`.
```

(Only these two new bullets are required by the spec; you do not need to backfill bullets for the existing detectors.)

- [ ] **Step 3: Commit**

```bash
git add zero-test-bridge/build.gradle lint-rules/AGENTS.md
git commit -m "lint: wire lint-rules into zero-test-bridge; document new boundary rules"
git push
```

---

## Task 4: End-to-end verification

**Files:** none (verification only)

- [ ] **Step 1: Run lint-rules unit tests**

Run: `./gradlew :lint-rules:test`

Expected: BUILD SUCCESSFUL. All `TestBridgeBoundaryDetectorTest` and `TestBridgeProductionPurityDetectorTest` cases pass alongside existing tests.

- [ ] **Step 2: Run app lint (main + androidTest source sets) and zero-test-bridge lint**

Run: `./gradlew :app:lintDebug :app:lintDebugAndroidTest :zero-test-bridge:lintDebug`

Expected: BUILD SUCCESSFUL. No `TestBridgeBoundary` or `TestBridgeProductionPurity` errors reported (current code is compliant: `BaseE2eTest` only imports `com.hluhovskyi.zero.testbridge.*` + `MainActivity` from production; robots are Compose-test-only; `:zero-test-bridge` has zero test-framework imports).

If `:app:lintDebugAndroidTest` is not a defined task in this project, run `:app:lintDebug` with `android.lintOptions.checkTestSources true` or whichever task exposes androidTest scanning for this AGP version. If you can't find the task at all, use `./gradlew :app:tasks --all | grep -i lint` to discover the correct one and update this plan in place.

- [ ] **Step 3: Negative verification — manually break the rule and confirm it fires**

Temporarily add `import com.hluhovskyi.zero.accounts.AccountRepository` (and an unused property of that type) to `app/src/androidTest/java/com/hluhovskyi/zero/BaseE2eTest.kt`.

Run: `./gradlew :app:lintDebugAndroidTest` (or whichever task scans androidTest).

Expected: lint reports `TestBridgeBoundary` error at the new import line.

Revert the change (`git restore app/src/androidTest/java/com/hluhovskyi/zero/BaseE2eTest.kt`).

Similarly, temporarily add `import org.junit.Test` to any file under `zero-test-bridge/src/main/`, run `:zero-test-bridge:lintDebug`, confirm `TestBridgeProductionPurity` fires, then revert.

These ad-hoc checks are validation only — do not commit either of them.

- [ ] **Step 4: Final clean-tree confirmation**

Run: `git status --short`

Expected: empty (all task commits already pushed; no stray edits from Step 3).

---

## Self-review

- **Spec coverage:**
  - Rule 1 detector → Task 1.
  - Rule 2 detector → Task 2.
  - Allowlist (`testbridge.*`, `MainActivity`) → Task 1 Step 3 (`isAllowed`).
  - Forbidden prefix list (Rule 2 — 6 prefixes) → Task 2 Step 3 (`FORBIDDEN_PREFIXES`).
  - "UAST + `PsiClass`, no package matching" for Rule 1 → Task 1 uses `node.resolve() as? PsiClass` and inspects `containingFile.virtualFile.path`.
  - Registry wiring → Task 1 Step 4 + Task 2 Step 4.
  - `lintChecks` already in `:app`, add to `:zero-test-bridge` → Task 3 Step 1.
  - Tests: ≥1 passing + ≥1 failing case per rule → Task 1 has 5, Task 2 has 4.
  - Verification commands → Task 4.
  - AGENTS.md update with one bullet per new rule → Task 3 Step 2.
- **Placeholder scan:** Every code step contains full code. No TBD / TODO / "appropriate error handling" / "similar to" references.
- **Type consistency:** Detector class names match between source files, test files, and `ZeroIssueRegistry` references (`TestBridgeBoundaryDetector`, `TestBridgeProductionPurityDetector`). Issue IDs (`TestBridgeBoundary`, `TestBridgeProductionPurity`) used in both `expectContains` assertions and `Issue.create` calls.
