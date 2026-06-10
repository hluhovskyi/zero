# Code Style

Conventions to keep code consistent across the codebase.

## Formatting — run spotless before pushing

**Run `./gradlew spotlessApply` before pushing any Kotlin change** — most of the conventions below (trailing commas, line wrapping, blank lines, import ordering) are spotless-enforced. CI's `spotlessCheck` gates the merge and no local git hook runs it, so an unformatted commit is only caught after CI finishes — the most expensive point to discover it.

## Naming Conventions

**Name concrete implementations `Default*`, never `*Impl`** — `DefaultFooViewModel`, not `FooViewModelImpl`. A lint rule (`DefaultImplMustBeInternal`) enforces that `Default*` classes are `internal`; the `NoImplSuffix` lint blocks the `*Impl` names that would bypass that guard.

## Imports vs Fully Qualified Class Names
Always use specific imports. NEVER use fully qualified class names in code bodies (enforced by the `FullyQualifiedReference` lint). NEVER use wildcard imports (`import com.package.*`) — ktlint via spotless rejects them.

## Date / Time
Use `kotlinx.datetime` types throughout the codebase:
- `kotlinx.datetime.LocalDateTime` — timestamps stored in DB, passed between layers
- `kotlinx.datetime.LocalDate` — calendar dates (e.g. transaction date filter)
- `kotlinx.datetime.Instant` — what `Clock.now()` returns
- `kotlinx.datetime.TimeZone` — what `ZoneProvider.timeZone()` returns
- `kotlin.time.Duration` — durations (Kotlin stdlib, no extra import needed)

Do NOT use `java.time.*` in the KMP-bound modules (`zero-api`, `zero-sync`, `zero-backup`) —
enforced by the `KmpReadiness` lint; these modules will become `commonMain`. View-layer
*formatting* (`app`, `zero-ui` components, and `zero-core` ViewProviders/parsers) may use
`java.time.format.DateTimeFormatter` and friends via the `toJavaLocalDateTime()` bridge for
locale-aware output. Domain logic — ViewModels, UseCases, repositories, entities — stays on
`kotlinx.datetime`.

Always inject `Clock` from `com.hluhovskyi.zero.common.time.Clock` instead of calling
`LocalDateTime.now()` or `kotlinx.datetime.Clock.System.now()` directly. This keeps code
testable. Enforced by the `DirectClockUsage` lint — only the Clock impls under `common/time/`
and the test bridge may read `Clock.System`.

## Use `Id.Unknown` Instead of `null`
For missing or unset identifiers, use `Id.Unknown` — never `null`.

**`Id(nullableString)` handles the null case via companion operator** — produces `Id.Known` or `Id.Unknown`; no null guard before `Id.Known(it)` is needed. Because `Id.Unknown` can never appear in a `Set<Id.Known>`, set-membership checks work naturally: `Id(transaction.categoryId) !in excluded` replaces `categoryId == null || Id.Known(categoryId) !in excluded`.

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

## Kotlin Idioms Over Verbose Alternatives

**Use Kotlin idioms: `to` over `Pair(a, b)`, destructuring with meaningful names over `.first`/`.second`, operators on domain types over unwrapping `.value` at the call site** — if a domain type is missing an operator, add it to the interface rather than working around it inline.

## Trailing Commas
Use trailing commas on the last parameter in multi-line function declarations and calls.
