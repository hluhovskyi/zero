# lint-rules — Codebase Guide for AI Agents

Custom Android Lint rules. `java-library` plugin — run tests with `./gradlew :lint-rules:test`
(not `:testDebugUnitTest`).

## Adding a Rule

1. Create detector in `src/main/kotlin/…/lint/`
2. Add `ISSUE` to `ZeroIssueRegistry.issues`
3. Wire target module: `lintChecks project(":lint-rules")` in its `build.gradle`

## Detector Skeleton

```kotlin
class MyDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)
    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) { … }
    }
    companion object { val ISSUE: Issue = Issue.create(id = "MyIssue", …) }
}
```

| Inspect… | `getApplicableUastTypes()` | override |
|----------|---------------------------|----------|
| class / interface | `UClass` | `visitClass` |
| method call | `UCallExpression` | `visitCallExpression` |
| qualified reference (`a.b.C`) | `UQualifiedReferenceExpression` | `visitQualifiedReferenceExpression` |

## Kotlin Modifiers and Annotations

UAST is language-agnostic. For Kotlin-specific checks, cast `sourcePsi`:

```kotlin
// visibility / fun-interface keyword
val psi = node.sourcePsi as? KtModifierListOwner ?: return
psi.hasModifier(KtTokens.INTERNAL_KEYWORD)   // or FUN_KEYWORD, OVERRIDE_KEYWORD, …

// annotation — use short-name, NOT findAnnotation(FQN)
// findAnnotation() requires the annotation class on the classpath, which the test sandbox lacks
node.sourcePsi?.annotationEntries?.any { it.shortName?.asString() == "Composable" }

// findAnnotation(FQN) is fine for library types that ARE on the classpath (e.g. @Serializable)
node.findAnnotation("kotlinx.serialization.Serializable")
```

## Type / Interface Checks

```kotlin
val psiClass = (node.returnType as? PsiClassType)?.resolve() ?: return
context.evaluator.implementsInterface(psiClass, "java.io.Closeable", false)
```

## Testing

- `.testModes(TestMode.DEFAULT)` — other modes rewrite imports, causing silent false negatives
- Stub every external type; the sandbox has no classpath:
  ```kotlin
  kotlin("package androidx.compose.runtime\nannotation class Composable").indented()
  ```
- `expectContains("IssueId")` for positive cases (severity-agnostic)
- `expectClean()` for negative cases

## Gotchas

**`ULiteralExpression` does not match Kotlin strings.** Use `ConstantEvaluator.evaluateString(context, expr, false)` — works for both Java and Kotlin, handles constant folding. Note: it traces `String` parameter references to their default value, so `foo: String = "x"` evaluates to `"x"` at every internal use site.

**`uastParent` is not set inside lambdas / composable contexts in the test sandbox.** To find the nearest enclosing named function, walk `sourcePsi?.parent` (PSI, not UAST) and cast to `KtNamedFunction`.

## Boundary rules

- **TestBridgeBoundary** — app/src/androidTest/** may only import production code through `com.hluhovskyi.zero.testbridge.*` or `com.hluhovskyi.zero.activity.MainActivity`. Grow `DatabaseTestBridge` (or add a new bridge) instead of poking holes.
- **TestBridgeProductionPurity** — zero-test-bridge/src/main/** must not import `org.junit.*`, `androidx.test.*`, `androidx.compose.ui.test.*`, `kotlin.test.*`, `org.mockito.*`, or `io.mockk.*` — the module ships in the production APK.
