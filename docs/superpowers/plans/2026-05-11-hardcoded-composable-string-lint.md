# HardcodedComposableString Lint Rule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `HardcodedComposableString` lint rule that warns when a string literal is passed to `Text()` or `contentDescription` inside a `@Composable` function, then wire the lint module into all four modules that contain composables.

**Architecture:** Single `UCallExpression` visitor; composable guard walks the UAST parent chain to the nearest enclosing `UMethod` and checks for `@Composable`. Named-arg lookup uses Kotlin PSI (`KtCallExpression.valueArgumentList`) without method resolution, so no Compose stubs are needed in tests — only a minimal `@Composable` annotation stub.

**Tech Stack:** `com.android.tools.lint:lint-api:32.1.0`, Kotlin PSI (`org.jetbrains.kotlin.psi`), UAST (`org.jetbrains.uast`)

---

### Task 1: Detector + tests

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/HardcodedComposableStringDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/HardcodedComposableStringDetectorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/HardcodedComposableStringDetectorTest.kt`:

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class HardcodedComposableStringDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = HardcodedComposableStringDetector()
    override fun getIssues(): List<Issue> = listOf(HardcodedComposableStringDetector.ISSUE)

    private val composableStub = kotlin(
        """
        package androidx.compose.runtime
        annotation class Composable
        """,
    ).indented()

    fun `test flags positional Text argument`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("Save") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("HardcodedComposableString")
    }

    fun `test flags named text argument`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text(text = "Save") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("HardcodedComposableString")
    }

    fun `test flags contentDescription literal`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Icon(contentDescription = "Close") }
                    fun Icon(contentDescription: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("HardcodedComposableString")
    }

    fun `test clean when text uses stringResource`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text(text = stringResource(1)) }
                    fun Text(text: String) {}
                    fun stringResource(id: Int): String = ""
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean when Text called outside composable`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    fun notComposable() { Text("Save") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for empty string`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for single-char string`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("x") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for pure-numeric string`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("0") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure (class missing)**

```bash
./gradlew :lint-rules:testDebugUnitTest --tests "*HardcodedComposableStringDetectorTest*" 2>&1 | tail -20
```

- [ ] **Step 3: Implement the detector**

Create `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/HardcodedComposableStringDetector.kt`:

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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod

class HardcodedComposableStringDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (!isInsideComposable(node)) return

            if (node.methodName == "Text") {
                val textArg = node.findArgByName("text") ?: node.findFirstPositionalArg()
                checkLiteral(context, textArg)
            }

            checkLiteral(context, node.findArgByName("contentDescription"))
        }
    }

    private fun isInsideComposable(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UMethod) {
                return parent.hasAnnotation("androidx.compose.runtime.Composable")
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun UCallExpression.findArgByName(name: String): UExpression? {
        val ktCall = sourcePsi as? KtCallExpression ?: return null
        val ktArgs = ktCall.valueArgumentList?.arguments ?: return null
        val index = ktArgs.indexOfFirst { it.getArgumentName()?.asName?.identifier == name }
        if (index < 0) return null
        return valueArguments.getOrNull(index)
    }

    private fun UCallExpression.findFirstPositionalArg(): UExpression? {
        val ktCall = sourcePsi as? KtCallExpression ?: return null
        val firstArg = ktCall.valueArgumentList?.arguments?.firstOrNull() ?: return null
        if (firstArg.getArgumentName() != null) return null
        return valueArguments.firstOrNull()
    }

    private fun checkLiteral(context: JavaContext, expr: UExpression?) {
        if (expr !is ULiteralExpression || !expr.isString) return
        val value = expr.value as? String ?: return
        if (isExcluded(value)) return
        context.report(ISSUE, expr, context.getLocation(expr), MESSAGE)
    }

    private fun isExcluded(value: String): Boolean =
        value.isEmpty() || value.length == 1 || value.all { it.isDigit() }

    companion object {
        private const val MESSAGE =
            "Hardcoded string in @Composable — use `stringResource()` instead."

        val ISSUE: Issue = Issue.create(
            id = "HardcodedComposableString",
            briefDescription = "Hardcoded string in @Composable",
            explanation =
            "String literals in Composable functions should come from string resources " +
                "so the app can be localized. Use `stringResource(R.string.xxx)` instead of a " +
                "hardcoded literal.",
            category = Category.I18N,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                HardcodedComposableStringDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew :lint-rules:testDebugUnitTest --tests "*HardcodedComposableStringDetectorTest*" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/HardcodedComposableStringDetector.kt
git add lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/HardcodedComposableStringDetectorTest.kt
git commit -m "feat: HardcodedComposableStringDetector lint rule"
```

---

### Task 2: Register in ZeroIssueRegistry

**Files:**
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Add entry to registry**

In `ZeroIssueRegistry.kt`, add `HardcodedComposableStringDetector.ISSUE` to the `issues` list:

```kotlin
override val issues: List<Issue> = listOf(
    DefaultImplVisibilityDetector.ISSUE,
    ViewProviderVisibilityDetector.ISSUE,
    ViewProviderDependencyDetector.ISSUE,
    HandlerFunInterfaceDetector.ISSUE,
    DatabaseComponentEncapsulationDetector.ISSUE,
    SyncEntitySerialNameDetector.ISSUE,
    NoNamedAnnotationDetector.ISSUE,
    UnhandledCloseableDetector.ISSUE,
    UnhandledJobDetector.ISSUE,
    FullyQualifiedReferenceDetector.ISSUE,
    SealedSubtypeDuplicatePropertyDetector.ISSUE,
    HardcodedComposableStringDetector.ISSUE,
)
```

- [ ] **Step 2: Run full lint-rules tests to confirm nothing broken**

```bash
./gradlew :lint-rules:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt
git commit -m "feat: register HardcodedComposableString in ZeroIssueRegistry"
```

---

### Task 3: Wire lintChecks to zero-ui, app, zero-image-loading

**Files:**
- Modify: `zero-ui/build.gradle` (add `lintChecks` to dependencies block)
- Modify: `app/build.gradle` (add `lintChecks` to dependencies block)
- Modify: `zero-image-loading/build.gradle` (add `lintChecks` to dependencies block)

- [ ] **Step 1: Add lintChecks to zero-ui/build.gradle**

In `zero-ui/build.gradle`, inside the `dependencies { }` block, add:

```groovy
lintChecks project(":lint-rules")
```

- [ ] **Step 2: Add lintChecks to app/build.gradle**

In `app/build.gradle`, inside the `dependencies { }` block, add:

```groovy
lintChecks project(":lint-rules")
```

- [ ] **Step 3: Add lintChecks to zero-image-loading/build.gradle**

In `zero-image-loading/build.gradle`, inside the `dependencies { }` block, add:

```groovy
lintChecks project(":lint-rules")
```

- [ ] **Step 4: Run lintDebug across all four modules to confirm wiring**

```bash
./gradlew :zero-core:lintDebug :zero-ui:lintDebug :app:lintDebug :zero-image-loading:lintDebug 2>&1 | grep -E "BUILD|error:|Error" | head -30
```

Expected: `BUILD SUCCESSFUL` (the rule is `WARNING` so it won't block the build).

- [ ] **Step 5: Commit**

```bash
git add zero-ui/build.gradle app/build.gradle zero-image-loading/build.gradle
git commit -m "build: wire lint-rules to zero-ui, app, zero-image-loading"
```
