# Lint Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `lint-rules` Gradle module with 4 custom Android Lint rules that enforce the `zero-core` architectural contracts, runnable via `./gradlew :zero-core:lintDebug`.

**Architecture:** A pure JVM `lint-rules` module declares four `Detector` subclasses registered via a service file. `zero-core` wires it in via `lintChecks`. All rules target Kotlin class declarations using UAST + Kotlin PSI — detecting name patterns, then checking modifiers or constructor parameter types.

**Tech Stack:** `com.android.tools.lint:lint-api:32.1.0`, `com.android.tools.lint:lint-tests:32.1.0`, Kotlin JVM, JUnit 4

---

## File Map

**Create:**
- `lint-rules/build.gradle`
- `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`
- `lint-rules/src/main/resources/META-INF/services/com.android.tools.lint.client.api.IssueRegistry`
- `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/DefaultImplVisibilityDetector.kt`
- `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ViewProviderVisibilityDetector.kt`
- `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ViewProviderDependencyDetector.kt`
- `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/HandlerFunInterfaceDetector.kt`
- `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/DefaultImplVisibilityDetectorTest.kt`
- `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/ViewProviderVisibilityDetectorTest.kt`
- `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/ViewProviderDependencyDetectorTest.kt`
- `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/HandlerFunInterfaceDetectorTest.kt`

**Modify:**
- `settings.gradle` — add `include ':lint-rules'`
- `zero-core/build.gradle` — add `lintChecks project(":lint-rules")`

---

## Task 1: Scaffold lint-rules module

**Files:**
- Create: `lint-rules/build.gradle`
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`
- Create: `lint-rules/src/main/resources/META-INF/services/com.android.tools.lint.client.api.IssueRegistry`
- Modify: `settings.gradle`
- Modify: `zero-core/build.gradle`

- [ ] **Step 1: Create lint-rules/build.gradle**

```groovy
plugins {
    id 'java-library'
    id 'org.jetbrains.kotlin.jvm'
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly "com.android.tools.lint:lint-api:32.1.0"
    compileOnly "com.android.tools.lint:lint-checks:32.1.0"

    testImplementation "com.android.tools.lint:lint-tests:32.1.0"
    testImplementation "junit:junit:4.13.2"
}
```

- [ ] **Step 2: Create ZeroIssueRegistry.kt (empty issue list — detectors added in later tasks)**

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class ZeroIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = emptyList()

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(vendorName = "Zero")
}
```

- [ ] **Step 3: Create the service registration file**

File path: `lint-rules/src/main/resources/META-INF/services/com.android.tools.lint.client.api.IssueRegistry`

Contents (single line, no trailing newline):
```
com.hluhovskyi.zero.lint.ZeroIssueRegistry
```

- [ ] **Step 4: Register the module in settings.gradle**

Add after the last `include` line:
```groovy
include ':lint-rules'
```

- [ ] **Step 5: Wire into zero-core/build.gradle**

Add inside the `dependencies` block:
```groovy
lintChecks project(":lint-rules")
```

- [ ] **Step 6: Verify module builds**

```bash
./gradlew :lint-rules:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Verify lint task works end-to-end (zero issues yet)**

```bash
./gradlew :zero-core:lintDebug
```

Expected: `BUILD SUCCESSFUL` — no lint errors from the new module.

- [ ] **Step 8: Commit**

```bash
git add lint-rules/ settings.gradle zero-core/build.gradle
git commit -m "feat: scaffold lint-rules module with ZeroIssueRegistry"
```

---

## Task 2: DefaultImplMustBeInternal rule

Any class whose name starts with `Default` must have `internal` visibility.

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/DefaultImplVisibilityDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/DefaultImplVisibilityDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class DefaultImplVisibilityDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = DefaultImplVisibilityDetector()
    override fun getIssues(): List<Issue> = listOf(DefaultImplVisibilityDetector.ISSUE)

    @Test
    fun `reports when Default class is not internal`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class DefaultTransactionViewModel
                    """
                ).indented()
            )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `clean when Default class is internal`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    internal class DefaultTransactionViewModel
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun `ignores non-Default classes`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class TransactionViewModel
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run tests — verify they fail (class does not exist yet)**

```bash
./gradlew :lint-rules:test --tests "*.DefaultImplVisibilityDetectorTest"
```

Expected: FAIL — compilation error or `ClassNotFoundException`.

- [ ] **Step 3: Implement DefaultImplVisibilityDetector.kt**

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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.uast.UClass

class DefaultImplVisibilityDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.startsWith("Default")) return

            val sourcePsi = node.sourcePsi as? KtModifierListOwner ?: return
            if (!sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "DefaultXxx implementations must be internal. " +
                        "See zero-core/AGENTS.md naming conventions table."
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "DefaultImplMustBeInternal",
            briefDescription = "Default* implementations must be internal",
            explanation = "DefaultXxx implementations must be internal. " +
                "See zero-core/AGENTS.md naming conventions table.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                DefaultImplVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
```

- [ ] **Step 4: Register in ZeroIssueRegistry.kt**

Replace `override val issues: List<Issue> = emptyList()` with:
```kotlin
override val issues: List<Issue> = listOf(
    DefaultImplVisibilityDetector.ISSUE,
)
```

- [ ] **Step 5: Run tests — verify they pass**

```bash
./gradlew :lint-rules:test --tests "*.DefaultImplVisibilityDetectorTest"
```

Expected: `BUILD SUCCESSFUL`, all 3 tests pass.

- [ ] **Step 6: Verify no false positives on the real codebase**

```bash
./gradlew :zero-core:lintDebug
```

Expected: `BUILD SUCCESSFUL` — all existing `Default*` classes are already `internal`.

- [ ] **Step 7: Commit**

```bash
git add lint-rules/
git commit -m "feat: add DefaultImplMustBeInternal lint rule"
```

---

## Task 3: ViewProviderMustBeInternal rule

Any class whose name ends with `ViewProvider` must have `internal` visibility.

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ViewProviderVisibilityDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/ViewProviderVisibilityDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ViewProviderVisibilityDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = ViewProviderVisibilityDetector()
    override fun getIssues(): List<Issue> = listOf(ViewProviderVisibilityDetector.ISSUE)

    @Test
    fun `reports when ViewProvider is not internal`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class TransactionViewProvider
                    """
                ).indented()
            )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `clean when ViewProvider is internal`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    internal class TransactionViewProvider
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun `ignores classes not named ViewProvider`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class TransactionHelper
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :lint-rules:test --tests "*.ViewProviderVisibilityDetectorTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement ViewProviderVisibilityDetector.kt**

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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.uast.UClass

class ViewProviderVisibilityDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.endsWith("ViewProvider")) return

            val sourcePsi = node.sourcePsi as? KtModifierListOwner ?: return
            if (!sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "*ViewProvider is internal by convention — it is wired by its FeatureComponent, " +
                        "never called directly. See zero-core/AGENTS.md."
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "ViewProviderMustBeInternal",
            briefDescription = "*ViewProvider must be internal",
            explanation = "*ViewProvider is internal by convention — it is wired by its FeatureComponent, " +
                "never called directly. See zero-core/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ViewProviderVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
```

- [ ] **Step 4: Register in ZeroIssueRegistry.kt**

```kotlin
override val issues: List<Issue> = listOf(
    DefaultImplVisibilityDetector.ISSUE,
    ViewProviderVisibilityDetector.ISSUE,
)
```

- [ ] **Step 5: Run tests — verify they pass**

```bash
./gradlew :lint-rules:test --tests "*.ViewProviderVisibilityDetectorTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify no false positives on the real codebase**

```bash
./gradlew :zero-core:lintDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add lint-rules/
git commit -m "feat: add ViewProviderMustBeInternal lint rule"
```

---

## Task 4: ViewProviderMustNotInjectRepository rule

Any class named `*ViewProvider` must not have constructor parameters whose type name ends with `Repository` or `UseCase`.

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ViewProviderDependencyDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/ViewProviderDependencyDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ViewProviderDependencyDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = ViewProviderDependencyDetector()
    override fun getIssues(): List<Issue> = listOf(ViewProviderDependencyDetector.ISSUE)

    @Test
    fun `reports when ViewProvider injects a Repository`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class TransactionRepository
                    class TransactionViewModel

                    internal class TransactionViewProvider(
                        private val viewModel: TransactionViewModel,
                        private val repo: TransactionRepository,
                    )
                    """
                ).indented()
            )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `reports when ViewProvider injects a UseCase`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class TransactionUseCase
                    class TransactionViewModel

                    internal class TransactionViewProvider(
                        private val viewModel: TransactionViewModel,
                        private val useCase: TransactionUseCase,
                    )
                    """
                ).indented()
            )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `clean when ViewProvider only injects ViewModel and other safe types`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class ImageLoader
                    class TransactionViewModel

                    internal class TransactionViewProvider(
                        private val viewModel: TransactionViewModel,
                        private val imageLoader: ImageLoader,
                    )
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun `ignores non-ViewProvider classes`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    class TransactionRepository

                    class TransactionHelper(
                        private val repo: TransactionRepository,
                    )
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :lint-rules:test --tests "*.ViewProviderDependencyDetectorTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement ViewProviderDependencyDetector.kt**

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
import org.jetbrains.uast.UClass

class ViewProviderDependencyDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.endsWith("ViewProvider")) return

            for (constructor in node.constructors) {
                for (param in constructor.uastParameters) {
                    val simpleTypeName = param.type.canonicalText
                        .substringAfterLast('.')
                        .substringBefore('<')
                        .trimEnd('?')
                    if (simpleTypeName.endsWith("Repository") || simpleTypeName.endsWith("UseCase")) {
                        context.report(
                            ISSUE,
                            param,
                            context.getLocation(param),
                            "ViewProvider must not depend on *Repository or *UseCase directly. " +
                                "Pass state/actions through ViewModel. See docs/agents/architecture.md."
                        )
                    }
                }
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "ViewProviderMustNotInjectRepository",
            briefDescription = "*ViewProvider must not inject *Repository or *UseCase",
            explanation = "ViewProvider must not depend on *Repository or *UseCase directly. " +
                "Pass state/actions through ViewModel. See docs/agents/architecture.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ViewProviderDependencyDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
```

- [ ] **Step 4: Register in ZeroIssueRegistry.kt**

```kotlin
override val issues: List<Issue> = listOf(
    DefaultImplVisibilityDetector.ISSUE,
    ViewProviderVisibilityDetector.ISSUE,
    ViewProviderDependencyDetector.ISSUE,
)
```

- [ ] **Step 5: Run tests — verify they pass**

```bash
./gradlew :lint-rules:test --tests "*.ViewProviderDependencyDetectorTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify no false positives on the real codebase**

```bash
./gradlew :zero-core:lintDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add lint-rules/
git commit -m "feat: add ViewProviderMustNotInjectRepository lint rule"
```

---

## Task 5: HandlerMustBeFunInterface rule

Any interface whose name matches `On*Handler` must be declared as `fun interface`.

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/HandlerFunInterfaceDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/HandlerFunInterfaceDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class HandlerFunInterfaceDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = HandlerFunInterfaceDetector()
    override fun getIssues(): List<Issue> = listOf(HandlerFunInterfaceDetector.ISSUE)

    @Test
    fun `reports when On-Handler is a plain interface`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    interface OnTransactionSavedHandler {
                        fun onSaved()
                    }
                    """
                ).indented()
            )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `clean when On-Handler is a fun interface`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    fun interface OnTransactionSavedHandler {
                        fun onSaved()
                    }
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun `ignores interfaces not matching On-Handler pattern`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.transactions

                    interface TransactionListener {
                        fun onEvent()
                    }
                    """
                ).indented()
            )
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :lint-rules:test --tests "*.HandlerFunInterfaceDetectorTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement HandlerFunInterfaceDetector.kt**

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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UClass

class HandlerFunInterfaceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.startsWith("On") || !name.endsWith("Handler")) return
            if (!node.isInterface) return

            val ktClass = node.sourcePsi as? KtClass ?: return
            if (!ktClass.hasModifier(KtTokens.FUN_KEYWORD)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "OnXxxHandler must be a fun interface to allow lambda syntax at call sites. " +
                        "See docs/agents/architecture.md."
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "HandlerMustBeFunInterface",
            briefDescription = "On*Handler must be a fun interface",
            explanation = "OnXxxHandler must be a fun interface to allow lambda syntax at call sites. " +
                "See docs/agents/architecture.md.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                HandlerFunInterfaceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
```

- [ ] **Step 4: Register all issues in ZeroIssueRegistry.kt**

```kotlin
override val issues: List<Issue> = listOf(
    DefaultImplVisibilityDetector.ISSUE,
    ViewProviderVisibilityDetector.ISSUE,
    ViewProviderDependencyDetector.ISSUE,
    HandlerFunInterfaceDetector.ISSUE,
)
```

- [ ] **Step 5: Run tests — verify they pass**

```bash
./gradlew :lint-rules:test --tests "*.HandlerFunInterfaceDetectorTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run all lint-rules tests together**

```bash
./gradlew :lint-rules:test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Final lint check on zero-core**

```bash
./gradlew :zero-core:lintDebug
```

Expected: `BUILD SUCCESSFUL` — zero errors from any of the 4 new rules.

- [ ] **Step 8: Commit**

```bash
git add lint-rules/
git commit -m "feat: add HandlerMustBeFunInterface lint rule"
```

---

## Task 6: Document enforcement in agent docs

**Files:**
- Modify: `docs/agents/module-boundaries.md`
- Modify: `zero-core/AGENTS.md`

- [ ] **Step 1: Update docs/agents/module-boundaries.md**

Append a new section after the existing content:

```markdown

## Mechanical Enforcement

The above rules are enforced by custom Android Lint rules in the `:lint-rules` module.
Run `./gradlew :zero-core:lintDebug` to check. Violations are errors (build fails).

The 4 enforced invariants:
- `Default*` classes must be `internal` (`DefaultImplMustBeInternal`)
- `*ViewProvider` classes must be `internal` (`ViewProviderMustBeInternal`)
- `*ViewProvider` must not inject `*Repository` or `*UseCase` (`ViewProviderMustNotInjectRepository`)
- `On*Handler` interfaces must be `fun interface` (`HandlerMustBeFunInterface`)
```

- [ ] **Step 2: Update zero-core/AGENTS.md**

Add after the Rules section, before "## What Lives Here":

```markdown
## Lint Enforcement

The naming convention table below and the `internal` visibility rules are mechanically enforced
by custom Lint rules. Run `./gradlew :zero-core:lintDebug` to check.
Violations are errors — fix them before opening a PR.
```

- [ ] **Step 3: Commit**

```bash
git add docs/agents/module-boundaries.md zero-core/AGENTS.md
git commit -m "docs: document lint enforcement in agent guides"
```
