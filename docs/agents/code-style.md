# Code Style

Conventions to keep code consistent across the codebase.

## Imports vs Fully Qualified Class Names
Always use imports instead of fully qualified class names.

## Use `Clock` for Timestamps
Never call `LocalDateTime.now()` directly. Inject `Clock` and use `clock.localDateTime()`.

## Use `Id.Unknown` Instead of `null`
For missing or unset identifiers, use `Id.Unknown` — never `null`.

## Extract Complex Logic into Named Private Functions
When a block exceeds ~5-10 lines — a `when` branch, a `launch` body, a nested lambda — extract it into a named private function. The calling site should read like a table of contents (e.g., `is Action.Save -> save()`), not contain inline logic. Same applies when a condition-check-then-action pattern repeats across methods — extract a helper (e.g., `fetchRateIfTransfer()`).

## Atomic State Updates
Perform async work **inside** `mutableState.update { }` to keep read-and-update atomic, wrapped in `coroutineScope.launch { }`. Don't read state separately then update — that creates race conditions.

## Reusable Extensions for Data Mapping
Encapsulate duplicated transformations into extension functions. Private extensions for class-scoped formatting (e.g., `BigDecimal.format()`), shared extensions in dedicated `zero-api` files (e.g., `String.toBigDecimalOrZero()` in `BigDecimals.kt`).

## Guard Clauses and Early Returns
Use guard clauses with early returns (`val account = state.selectedAccount ?: return@launch`) instead of nesting the entire body in `if` blocks.

## Expression-Body `when` for Multi-Condition Logic
Prefer `return when { condition -> result; else -> default }` over `if/else` chains for mutually exclusive conditions.

## Blank Lines Between `when` Branches
Add a blank line between `when` branches, especially when branches have multi-line bodies.

## Line Wrapping
Break long expressions before `?:` and `?.let` operators. Indent continuation on the next line.

## Trailing Commas
Use trailing commas on the last parameter in multi-line function declarations and calls.
