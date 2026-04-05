# zero-core — Agent Guide

Android library module. Contains all feature logic: ViewModels, UseCases, Dagger Components, and ViewProviders.

## Rules

1. **Cannot import from `app`** — no `Navigator`, no `Destinations`, no navigation types. Screen communication goes through handler callbacks.
2. **Follow the Component pattern** — every feature needs `FeatureComponent`, `FeatureViewModel` (interface + `Default` impl), `FeatureViewProvider`. See [Architecture Patterns](../docs/agents/architecture.md).
3. **`@BindsInstance` for lightweight values only** — handler callbacks (`OnXxxHandler`), IDs (`Id`). Never domain objects, repositories, or formatters. See [Dependency Injection](../docs/agents/dependency-injection.md).
4. **Resolve runtime state in `attach()`, not constructors** — repositories, data lookups, etc.
5. **Handler callbacks dispatch on `Dispatchers.Main`** — handlers often trigger navigation which requires the main thread.
6. **`internal` visibility for implementations** — `DefaultFeatureViewModel`, `DefaultFeatureUseCase`, `FeatureViewProvider` are all `internal`.

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

1. Create package under `com/hluhovskyi/zero/featurename/`
2. Define `FeatureViewModel` interface extending `AttachableActionStateModel<Action, State>`
3. Implement `DefaultFeatureViewModel` (internal)
4. Create `FeatureViewProvider` (internal)
5. Create `FeatureComponent` with `Dependencies`, `Builder`, and `Module`
6. Wire in `app` module's `MainActivityScreenComponent` for navigation
