# Design: HardcodedComposableString Lint Rule

**Date:** 2026-05-11
**Issue:** #132

## Goal

Prevent hardcoded string literals from being introduced in `@Composable` functions after
the string-extraction cleanup in issue #103. Any `Text("Hardcoded")` or
`contentDescription = "Hardcoded"` in a composable will fail `./gradlew lintDebug`.

## What Gets Flagged

Two patterns, both only inside `@Composable`-annotated functions:

1. **`Text()` calls** — positional or named `text` arg:
   ```kotlin
   Text("Save")               // flagged
   Text(text = "Save")        // flagged
   Text(stringResource(R.string.save))  // clean
   ```

2. **`contentDescription` named arg** in any composable call:
   ```kotlin
   Icon(painter = …, contentDescription = "Close")  // flagged
   Icon(painter = …, contentDescription = stringResource(R.string.close))  // clean
   ```

**Excluded (not flagged):** empty strings `""`, single-char strings `"x"`,
pure-numeric strings `"42"` (used as display placeholders).

## Severity

`WARNING` in this PR. Promoted to `ERROR` in a follow-up after #103 lands and
clears all existing violations.

## Detection Approach

- **UAST type:** `UCallExpression` (all function calls)
- **Composable guard:** walk parent chain to nearest enclosing `UMethod`; skip if
  not annotated `@Composable`. Composable lambdas (`LazyColumn { … }`) are handled
  because the walk continues past `ULambdaExpression` to find the outer composable method.
- **Argument lookup:** Kotlin PSI (`KtCallExpression.valueArgumentList`) for named-arg
  matching without needing method resolution.
- **`Text()` positional fallback:** if no `text =` named arg exists and the first
  argument has no explicit label, treat it as the `text` value.
- **Category:** `Category.I18N`

## Module Wiring

`lintChecks project(":lint-rules")` added to:

| Module | Status |
|---|---|
| `zero-core` | already wired |
| `zero-ui` | add |
| `app` | add |
| `zero-image-loading` | add |

## Files Changed

```
lint-rules/src/main/kotlin/…/HardcodedComposableStringDetector.kt  (new)
lint-rules/src/test/kotlin/…/HardcodedComposableStringDetectorTest.kt  (new)
lint-rules/src/main/kotlin/…/ZeroIssueRegistry.kt  (add entry)
zero-ui/build.gradle  (add lintChecks)
app/build.gradle  (add lintChecks)
zero-image-loading/build.gradle  (add lintChecks)
```

## Test Cases

- `Text("literal")` inside composable → warned
- `Text(text = "literal")` inside composable → warned
- `contentDescription = "literal"` inside composable → warned
- `Text(stringResource(…))` inside composable → clean
- `Text("literal")` in a plain `fun` (non-composable) → clean
- Empty / single-char / pure-numeric strings → clean
