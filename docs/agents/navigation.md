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

Use a scoped use case (`Action.Request` / `Action.Pick` / `State.Picked`) — interface in `zero-core`, navigation-aware implementation in `app`. See `CategoryEditIconUseCase` + `DefaultCategoryEditIconUseCase` as the canonical example.

Do not relay results through ViewModel state or shared flows on components.

## Preselecting an Item in a Picker

**To preselect the current item in a picker, pass the selection as an optional `Argument<Id>` on the picker destination and thread it through the stack** — `Action.Request` carries the current selection; the `Default*UseCase` appends it as a nav arg only when `Id.Known`; the `NavigatorEntry` reads it and passes it via `@BindsInstance`; the ViewModel stores it as initial state; the ViewProvider renders the selection ring. See `TransactionEditCategoryUseCase` + `DefaultTransactionEditCategoryUseCase` as the canonical example.

## Bottom Sheet Destinations

Use `NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet` for destinations that should appear as a bottom sheet overlay. In `MainActivityScreenViewProvider` these are registered as `dialog {}` destinations (so they overlay the current screen rather than replacing it), wrapped in `BottomSheetNavDestination` which owns the `ModalBottomSheetLayout` show/dismiss logic.

## In-Screen Overlays and System Back

Some overlays aren't nav destinations — they're sheets/numpads/confirms rendered inside a `ViewProvider`, shown/hidden on a state flag (e.g. the Budget inline numpad and remove-confirm sheet). These do **not** get back-to-dismiss for free; only nav destinations and `Dialog` do.

- **A state-gated in-screen overlay must add `BackHandler(enabled = <overlay visible>) { dispatch(Dismiss) }`** — without it, system back falls through to the nav stack and leaves the whole screen. (`Dialog` is exempt; it consumes back via `onDismissRequest`.)
- **A sub-overlay opened from another overlay must layer on top and step back to it, not collapse to the screen** — keep the parent's state set, hide it while the child shows, and have the child's dismiss reveal it. So back walks `child → parent → screen`.
- **`BackHandler` priority is composition order — the last-composed enabled one wins** — register the topmost overlay's handler last so back targets it first.
