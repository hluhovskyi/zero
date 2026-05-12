# lint-rules — Codebase Guide for AI Agents

Custom Android Lint rules for the Zero project. `java-library` plugin (no Android dependency),
so run tests with `./gradlew :lint-rules:test` — not `:testDebugUnitTest`.

## Adding a New Rule

1. Create `Detector` in `src/main/kotlin/…/lint/`
2. Add its `ISSUE` to `ZeroIssueRegistry.issues`
3. Wire the module under test: `lintChecks project(":lint-rules")` in that module's `build.gradle`
4. Run `./gradlew :lint-rules:test` to verify

## UAST — Kotlin vs Java

**Never use `ULiteralExpression` to detect Kotlin strings** — it matches Java literals only. Kotlin
string literals are `KotlinStringTemplateUPolyadicExpression`. Use `ConstantEvaluator.evaluateString(context, expr, false)` for any string detection; it works for both languages and handles
constant folding.

**`ConstantEvaluator.evaluateString` traces parameter defaults** — if a parameter has a
`String` default, evaluating a reference to that parameter returns the default value. A function
with `label: String = "AMOUNT"` that passes `label` to `Text()` will be flagged as if the literal
appeared at the call site. Fix: remove the default and require callers to pass `stringResource()`.

**`getArgumentForParameter` takes an `Int` index, not a `PsiParameter`** — resolve the method,
find the parameter index with `parameterList.parameters.indexOfFirst { it.name == name }`, then
pass the index. This correctly handles both named and positional call sites.

## Annotation Detection

**Never use `hasAnnotation("full.qualified.Name")` for annotation detection** — full-name
resolution requires the annotation class to be on the classpath, which it is not in the lint test
sandbox. Use PSI short-name instead:

```kotlin
annotationEntries.any { it.shortName?.asString() == "Composable" }
```

## Walking the Call Hierarchy

**Walk PSI parents, not UAST parents, to find enclosing functions** — `uastParent` references are
not always populated in the test sandbox. Walk `sourcePsi?.parent` instead, casting to
`KtNamedFunction` when you find a function node.

## Testing

- Extend `LintDetectorTest`; override `getDetector()` and `getIssues()`
- Add `.testModes(TestMode.DEFAULT)` — other modes enable alias/import rewriting that
  can change how arguments resolve and produce confusing false negatives
- Stub every annotation and library type used in test snippets; the sandbox has no classpath:
  ```kotlin
  private val composableStub = kotlin("""
      package androidx.compose.runtime
      annotation class Composable
  """).indented()
  ```
- Prefer `expectContains("IssueId")` over `expect(...)` for positive cases — it is
  severity-agnostic and survives severity bumps without test changes
- `expectClean()` for negative cases
