# Code Style

Conventions to keep code consistent across the codebase.

## Imports vs Fully Qualified Class Names
Always use specific imports. NEVER use fully qualified class names in code bodies. NEVER use wildcard imports (`import com.package.*`).

## Date / Time
Use `kotlinx.datetime` types throughout the codebase:
- `kotlinx.datetime.LocalDateTime` — timestamps stored in DB, passed between layers
- `kotlinx.datetime.LocalDate` — calendar dates (e.g. transaction date filter)
- `kotlinx.datetime.Instant` — what `Clock.now()` returns
- `kotlinx.datetime.TimeZone` — what `ZoneProvider.timeZone()` returns
- `kotlin.time.Duration` — durations (Kotlin stdlib, no extra import needed)

Do NOT use `java.time.*` in domain code (`zero-api`, `zero-core`, `zero-database`).
`java.time.format.DateTimeFormatter` is allowed only in `app` (for locale-aware formatting)
and `zero-ui` (via `toJavaLocalDateTime()` bridge). This boundary is intentional — it will
become an `expect/actual` when the project goes KMP.

Always inject `Clock` from `com.hluhovskyi.zero.common.time.Clock` instead of calling
`LocalDateTime.now()` or `kotlinx.datetime.Clock.System.now()` directly. This keeps code testable.

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
