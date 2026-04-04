# zero-api — Agent Guide

Pure Kotlin module. No Android dependencies allowed.

## Purpose

Domain interfaces, types, and contracts shared by all other modules. This is the "contract layer" — defines what exists without how it's implemented.

## Rules

1. **No Android imports** — this module must remain pure Kotlin/JVM. No `android.*`, no `androidx.*`.
2. **Interfaces only for repositories** — implementations live in `zero-database`. Define `query()` returning `Flow<T>` and `suspend fun insert()`.
3. **Every repository needs a `Noop` object** — used for safe defaults in Dagger builders and testing.
4. **Sealed `Criteria<T>`** — repository query criteria must be sealed with a type parameter for type-safe returns.

## What Lives Here

- **Repository interfaces**: `TransactionRepository`, `AccountRepository`, `CategoryRepository`, `CurrencyRepository`, `ColorRepository`, `IconRepository`
- **Domain types**: `Id`, `Amount`, `Rate`, `Currency`, `ColorScheme`, `ColorValue`, `Image`
- **Base architecture interfaces**: `Buildable`, `Attachable`, `ActionStateModel`, `AttachableActionStateModel`, `Identifiable`
- **Flow extensions**: `onStartWithEmptyList()`, `onEmptyReturnEmptyList()`, `associateById()`
- **Utilities**: `Clock`, `Closeables`, `Logger`, `IncorrectStateDetector`

## Key Types

| Type | Notes |
|------|-------|
| `Id.Known` / `Id.Unknown` | Never use `null` — use `Id.Unknown` |
| `Amount` | Wraps `BigDecimal`. Use `Amount.zero()`, arithmetic operators `+`, `-`, `withRate()` |
| `Rate` | Exchange rate. `Rate.Same` = 1.0 |
| `Clock` | Always use instead of `LocalDateTime.now()` |

## Adding a New Repository

1. Define interface in this module following the `TransactionRepository` pattern
2. Add `Criteria<T>` sealed interface with `All` and `ById` at minimum
3. Add `Noop` object
4. Implement in `zero-database`
5. Wire through `DatabaseComponent.Dependencies`
