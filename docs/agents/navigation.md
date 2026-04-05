# Navigation

Zero uses a custom layer on top of Jetpack Navigation with URL-based routes.

## Key Types

- `Destination` — a screen/route definition (`destinationOf("path", ...args)`)
- `Argument<T>` — typed nav argument; supports `Id`, `String`
- `ArgumentValue<T>` — argument + its value; created via `argument.withValue(value)`
- `Navigator` — performs navigation actions, observes back stack
- `NavigatorScope.buildable(destination) { ... }` — registers a destination whose component is built each time it's navigated to; the lambda receives `this: NavigatorScope.Context` with `navigator` and `arguments`

## Argument Conventions

- Use `Argument<Id>` for domain identifiers — **not** `Argument<String>`
- Use `idOptionalValueOf("key")` for optional args (falls back to `Id.Unknown`)
- Read arguments with `arguments.getValue(key)` (extension), not `arguments[key].value`
- Multiple optional args in a route are separated by `&` in the URL

## Id Semantics

- `Id.Unknown` — the null-equivalent; use instead of `null` for missing IDs
- `Id.Known` — wraps a real string value; cast via `as? Id.Known`
- `Id("string")` always constructs `Id.Known`

## Passing Data Between Screens

Data flows as navigation arguments — not stored state on use cases or view models. When a picker screen needs context (e.g., a color ID to preview icons), encode it as an `Argument<Id>` on the destination and resolve it at the destination using a repository.

## Returning a Result from a Screen

When screen A navigates to screen B and needs a result back (a picked value, a confirmation, etc.), use the Request / Deliver / Result use case pattern established by `DefaultCategoryEditIconUseCase` and `DefaultAccountEditIconUseCase`:

1. **Interface in `zero-core`** — `XxxUseCase` with `Action.Request`, `Action.Deliver(value)`, `State.Result(value)`. Implement `Noop`.
2. **Implementation in `app`** — navigates to the destination on `Request` (with a generated `requestId` as nav argument), buffers `Deliver` results in a `MutableSharedFlow`, emits `Result` only when the `requestId` matches (guards against stale results from a previous navigation). Calls `navigator.back()` on emit.
3. **Wire in `MainActivityScreenComponent.Module`** — scope to `@MainActivityScreenScope`. Screen B's `onXxxSelectedHandler` calls `perform(Deliver(...))`. Screen A's component receives the use case via `@BindsInstance`.
4. **Caller (screen A)** — calls `perform(Request)` to open screen B; observes `state` for `Result` in `attach()`.

Do not relay results through ViewModel state, shared flows on the component, or any other mechanism. Navigation is the source of truth.

## Bottom Sheet Destinations

Use `NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet` for destinations that should appear as a bottom sheet overlay. In `MainActivityScreenViewProvider` these are registered as `dialog {}` destinations (so they overlay the current screen rather than replacing it), wrapped in `BottomSheetNavDestination` which owns the `ModalBottomSheetLayout` show/dismiss logic.

**Known issue**: The dialog window background transparency is not yet fully resolved — the sheet may render full-screen. Investigation ongoing.
