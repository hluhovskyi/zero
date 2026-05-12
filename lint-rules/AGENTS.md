# lint-rules — Codebase Guide for AI Agents

Custom Android Lint rules. `java-library` plugin (no Android dependency) — run tests with
`./gradlew :lint-rules:test`, not `:testDebugUnitTest`.

## Adding a Rule

1. Create detector in `src/main/kotlin/…/lint/`
2. Add `ISSUE` to `ZeroIssueRegistry.issues`
3. Wire target module: `lintChecks project(":lint-rules")` in its `build.gradle`

## Detector Skeleton

Every detector follows the same three-part structure:

```kotlin
class MyDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)
    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) { … }
    }
    companion object {
        val ISSUE: Issue = Issue.create(id = "MyIssue", …)
    }
}
```

Pick the UAST type that matches what you're inspecting:

| Goal | `getApplicableUastTypes()` | Handler override |
|------|---------------------------|-----------------|
| Inspect a class/interface | `UClass` | `visitClass` |
| Inspect a method call | `UCallExpression` | `visitCallExpression` |
| Inspect a qualified reference | `UQualifiedReferenceExpression` | `visitQualifiedReferenceExpression` |

## Kotlin-Specific Checks (PSI)

UAST is language-agnostic; Kotlin-only properties (modifiers, `fun` keyword, etc.) require a
PSI cast:

```kotlin
// Check for `internal` modifier
val sourcePsi = node.sourcePsi as? KtModifierListOwner ?: return
if (!sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD)) { … }

// Check for `fun interface`
val ktClass = node.sourcePsi as? KtClass ?: return
if (!ktClass.hasModifier(KtTokens.FUN_KEYWORD)) { … }
```

## Annotation Detection

**Use `findAnnotation(FQN)` only for library annotations that are on the compile classpath**
(e.g. `kotlinx.serialization.Serializable`). For annotations that won't be resolved in the lint
test sandbox, check by short name via PSI instead:

```kotlin
// Works in tests — no classpath resolution required
annotationEntries.any { it.shortName?.asString() == "Composable" }
```

## Type and Interface Checks

To check if a type implements an interface, use the evaluator (handles supertype chains):

```kotlin
val psiClass = (node.returnType as? PsiClassType)?.resolve() ?: return
if (!context.evaluator.implementsInterface(psiClass, "java.io.Closeable", false)) return
```

## Walking the Tree

UAST parent walking (`node.uastParent`) works in most detectors. Use it to walk up from a
call expression toward the containing block or method.

**Exception — detecting an enclosing named function by annotation:** `uastParent` is not reliably
set inside lambda/composable contexts in the test sandbox. Walk `sourcePsi?.parent` instead,
casting to `KtNamedFunction` when you reach a function node.

## Reporting

```kotlin
context.report(ISSUE, node, context.getLocation(node), "Message text.")
```

Use `context.getLocation(node as UElement)` when the node type needs an explicit cast.

## Testing

- Extend `LintDetectorTest`; override `getDetector()` and `getIssues()`
- Add `.testModes(TestMode.DEFAULT)` — other modes rewrite imports/aliases and can cause
  silent false negatives
- The test sandbox has no classpath. Stub every external type used in snippets:
  ```kotlin
  private val composableStub = kotlin("""
      package androidx.compose.runtime
      annotation class Composable
  """).indented()
  ```
- `expectContains("IssueId")` for positive cases — severity-agnostic, survives severity bumps
- `expectClean()` for negative cases

## String Value Detection

**Do not check `expr is ULiteralExpression` for Kotlin strings** — Kotlin string literals
are `KotlinStringTemplateUPolyadicExpression`, not `ULiteralExpression`. Use
`ConstantEvaluator.evaluateString(context, expr, false)` — it handles both languages and
folds constants. It also traces `String` parameter references to their default values, so
`foo: String = "literal"` evaluates to `"literal"` at every use site inside the function.
