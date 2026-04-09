# Design: Mechanical Architecture Enforcement via Custom Lint Rules

## Goal

Enforce the intra-module behavioral contracts of `zero-core` mechanically, so that violations are caught at lint-check time (CI / PR gate) rather than relying on agents reading documentation. Rules are severity **Error** so `./gradlew lintDebug` fails on violation.

## Context

The Gradle module graph already enforces inter-module boundaries at compile time (e.g. `zero-core` cannot import from `app` because `app` is not a declared dependency). What is not enforced is the naming and layering contract *within* `zero-core`:

- `Default*` implementations must be `internal`
- `*ViewProvider` classes must be `internal`
- `*ViewProvider` classes must not inject `*Repository` or `*UseCase` directly
- `On*Handler` interfaces must be `fun interface`

## Implementation Approach

Custom Android Lint rules using `com.android.tools.lint:lint-api:32.1.0` (matches AGP 9.1.0). Lint rules live in a dedicated `lint-rules/` Gradle module and are wired into `zero-core` via `lintChecks`. No new tooling is introduced — `./gradlew lintDebug` is the existing lint entrypoint.

## Module Structure

```
lint-rules/
  build.gradle                          ← java-library, lint-api compileOnly
  src/
    main/
      kotlin/com/hluhovskyi/zero/lint/
        ZeroIssueRegistry.kt            ← registers all 4 issues
        DefaultImplVisibilityDetector.kt
        HandlerFunInterfaceDetector.kt
        ViewProviderVisibilityDetector.kt
        ViewProviderDependencyDetector.kt
      resources/META-INF/services/
        com.android.tools.lint.client.api.IssueRegistry
    test/kotlin/com/hluhovskyi/zero/lint/
      DefaultImplVisibilityDetectorTest.kt
      HandlerFunInterfaceDetectorTest.kt
      ViewProviderVisibilityDetectorTest.kt
      ViewProviderDependencyDetectorTest.kt
```

`settings.gradle`: add `include ':lint-rules'`

`zero-core/build.gradle`: add `lintChecks project(":lint-rules")` to dependencies

No other module needs `lintChecks` — all 4 rules target patterns exclusive to `zero-core`.

## The 4 Rules

### Rule 1: `DefaultImplMustBeInternal`

**Detector:** `UastScanner`, visits class declarations.
**Trigger:** Class name starts with `Default` in package `com.hluhovskyi.zero.**`.
**Check:** Class does not have `internal` visibility modifier.
**Error message:**
```
DefaultXxx implementations must be internal.
See zero-core/AGENTS.md naming conventions table.
```

### Rule 2: `ViewProviderMustBeInternal`

**Detector:** `UastScanner`, visits class declarations.
**Trigger:** Class name ends with `ViewProvider`.
**Check:** Class does not have `internal` visibility modifier.
**Error message:**
```
*ViewProvider is internal by convention — it is wired by its FeatureComponent,
never called directly. See zero-core/AGENTS.md.
```

### Rule 3: `ViewProviderMustNotInjectRepository`

**Detector:** `UastScanner`, visits class declarations.
**Trigger:** Class name ends with `ViewProvider`.
**Check:** No constructor parameter or property type name ends with `Repository` or `UseCase`.
**Error message:**
```
ViewProvider must not depend on *Repository or *UseCase directly.
Pass state/actions through ViewModel. See docs/agents/architecture.md.
```

### Rule 4: `HandlerMustBeFunInterface`

**Detector:** `UastScanner`, visits class declarations.
**Trigger:** Interface name matches `On*Handler`.
**Check:** Interface is declared as a functional interface (has `@FunctionalInterface` annotation at UAST level, which Kotlin `fun interface` compiles to).
**Error message:**
```
OnXxxHandler must be a fun interface to allow lambda syntax at call sites.
See docs/agents/architecture.md.
```

## Detector Implementation Pattern

Each detector follows this structure:

```kotlin
class XxxDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                // name check
                // violation check
                // context.report(ISSUE, node, context.getLocation(node), "message")
            }
        }

    companion object {
        val ISSUE = Issue.create(
            id = "...",
            briefDescription = "...",
            explanation = "...",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(XxxDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
```

## Testing Pattern

Each detector has two tests using the `lint-tests` framework:

```kotlin
class XxxDetectorTest {
    @Test fun `reports violation`() {
        lint().files(kotlin("""...""")).issues(XxxDetector.ISSUE)
            .run().expect("""src/test.kt:N: Error: ...""")
    }

    @Test fun `clean code passes`() {
        lint().files(kotlin("""...""")).issues(XxxDetector.ISSUE)
            .run().expectClean()
    }
}
```

## Gradle Dependency Versions

```
com.android.tools.lint:lint-api:32.1.0      (compileOnly)
com.android.tools.lint:lint-checks:32.1.0   (compileOnly)
com.android.tools.lint:lint-tests:32.1.0    (testImplementation)
junit:junit:4.13.2                           (testImplementation)
```

## Out of Scope

- Lint rules for modules other than `zero-core` (no such patterns exist elsewhere)
- CI pipeline wiring (CI is not set up yet; `./gradlew lintDebug` is the manual gate)
- Lint baseline file (start clean, no suppression of existing violations)
