# First-Install Presets Design

**Date:** 2026-05-11
**Issue:** [#121](https://github.com/hluhovskyi/zero/issues/121)

## Goal

Seed a minimal set of preset accounts and categories on first install so the app is not empty when a new user opens it for the first time.

## Preset Data

### Accounts

| Name | Icon ID        | Color ID | AccountCategory |
|------|----------------|----------|-----------------|
| Bank | `bank`         | `blue`   | BANK            |
| Cash | `cash`         | `green`  | CASH            |

### Expense Categories

| Name          | Icon ID         | Color ID |
|---------------|-----------------|----------|
| Food & Drink  | `grocery`       | `orange` |
| Transport     | `car`           | `teal`   |
| Shopping      | `shopping_cart` | `pink`   |
| Entertainment | `game_controller` | `purple` |
| Health        | `health`        | `red`    |

### Income Categories

| Name         | Icon ID  | Color ID |
|--------------|----------|----------|
| Salary       | `salary` | `blue`   |
| Other Income | *(unknown/fallback)* | `grey` |

## New Icons

Three new vector drawables are required (added to `app/src/main/res/drawable/`):

| Resource name          | Icon ID         | Description       | IconCategory bucket |
|------------------------|-----------------|-------------------|---------------------|
| `ic_shopping_cart_24`  | `shopping_cart` | Shopping cart     | `shopping`          |
| `ic_health_24`         | `health`        | Stethoscope       | new `health` bucket |
| `ic_salary_24`         | `salary`        | Banknote/payslip  | `money_banking`     |

Registered in `KnownIconIds` and `PredefinedIconRepository`.

## Architecture

### PresetsUseCase

```kotlin
interface PresetsUseCase {
    suspend fun seed()
}
```

`seed()` is **idempotent**: it reads a `ConfigurationKey<Boolean>` flag (`presets_seeded`, default `false`). If false, it inserts the preset categories and accounts, then writes `true`. Subsequent calls are no-ops.

Lives in `zero-core`.

### DefaultPresetsUseCase

Dependencies:
- `CategoryRepository` — batch-insert preset categories
- `AccountRepository` — batch-insert preset accounts
- `CurrencyPrimaryUseCase` — resolve the user's primary currency for account inserts
- `ConfigurationRepository` — read/write the `presets_seeded` flag

The preset data (icon IDs, color IDs, names) are constants inside the implementation.

### PresetsConfigurationKey

```kotlin
object PresetsSeeded : PresetsConfigurationKey<Boolean>(
    name = "presets_seeded",
    defaultValue = false,
)
```

Lives in `zero-core` alongside `PresetsUseCase`.

### Triggering

`ActivityComponent.attach()` is currently a no-op (`Closeables.empty()`). It is overridden to launch a coroutine on `DispatcherProvider.io` that calls `presetsUseCase.seed()`. The coroutine is cancelled when the closeable returned by `attach()` is closed.

### DI Wiring

- `DefaultPresetsUseCase` provided as `PresetsUseCase` in `ApplicationComponent.Module` (application scope).
- `PresetsUseCase` added to `ActivityComponent.Dependencies`.
- `ActivityComponent.attach()` overridden to consume it.

## Future Extension

When a welcome screen is added, `seed()` can be called explicitly from that screen's "Get started" action instead of (or in addition to) the automatic `attach()` path. The idempotency guarantee means calling `seed()` twice is always safe.
