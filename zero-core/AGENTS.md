# zero-core — Agent Guide

Android library module. Contains all feature logic: ViewModels, UseCases, Dagger Components, and ViewProviders.

## Rules

1. **Cannot import from `app`** — no `Navigator`, no `Destinations`, no navigation types. Screen communication goes through handler callbacks. If a feature needs to open another screen and receive a result, read `docs/agents/navigation.md` before building anything.
2. **Follow the Component pattern** — every feature needs `FeatureComponent`, `FeatureViewModel` (interface + `Default` impl), `FeatureViewProvider`. See [Architecture Patterns](../docs/agents/architecture.md).
3. **`@BindsInstance` for lightweight values only** — handler callbacks (`OnXxxHandler`), IDs (`Id`). Never domain objects, repositories, or formatters. See [Dependency Injection](../docs/agents/dependency-injection.md).
4. **Resolve runtime state in `attach()`, not constructors** — repositories, data lookups, etc.
5. **Handler callbacks dispatch on `Dispatchers.Main`** — handlers often trigger navigation which requires the main thread.
6. **`internal` visibility for implementations** — `DefaultFeatureViewModel`, `DefaultFeatureUseCase`, `FeatureViewProvider` are all `internal`. (Enforced by Android Lint).
7. **ViewModel exposes screen shape, View renders it** — when a list has variant rows, the VM emits a sealed `Item` and the composable does `when (item) { is X -> ...; is Y -> ... }`. No `.filter`/`.any`/`.sortedBy`/`if (raw.field != null)` in the ViewProvider, no UseCase imports in the ViewProvider. Domain derivation (sort, thresholds, aggregation) lives in the UseCase; type-discrimination and joining live in the VM. Reference: `TransactionViewModel.Item`.

## What Lives Here

- **Feature packages**: `transactions/`, `accounts/`, `categories/`, `settings/`, `imports/`, `currencies/`, `colors/`, `icons/`
- **Each feature package** contains: Component, ViewModel (interface + Default), ViewProvider, optional UseCase, Handler interfaces
- **Edit sub-packages** (e.g., `transactions/edit/`): Separate components for create/edit flows
- **Preview sub-packages** (e.g., `transactions/preview/`): Read-only detail views

## Naming Conventions

| Type | Name | Visibility |
|------|------|-----------|
| Dagger Component | `FeatureComponent` | `public` (abstract class) |
| ViewModel interface | `FeatureViewModel` | `public` |
| ViewModel impl | `DefaultFeatureViewModel` | `internal` |
| UseCase interface | `FeatureUseCase` | `internal` (or public if cross-feature) |
| UseCase impl | `DefaultFeatureUseCase` | `internal` |
| ViewProvider | `FeatureViewProvider` | `internal` |
| Handler | `OnFeatureActionHandler` | `public` (fun interface) |

## Adding a New Feature

**Run `/zero-project:scaffold-feature` first** — it generates the Component/ViewModel/ViewProvider/Handler stubs so the plan only needs to specify business logic (state fields, data sources, layout). Do not inline this boilerplate in the plan; reference existing components (e.g. "follow `CategoryEditComponent`") for any pattern not covered by the scaffold.

1. Run `/zero-project:scaffold-feature` with `name`, `package`, and `handlers`
2. Verify `./gradlew :zero-core:compileDebugKotlin` passes on the stubs
3. Fill in ViewModel state fields and Flow sources
4. Implement ViewProvider layout
5. Wire in `app` module's `MainActivityScreenComponent` for navigation (see [Navigation](../docs/agents/navigation.md))
