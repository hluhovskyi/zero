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

When navigating to a picker that should highlight the currently selected item, pass the current selection as an additional optional `Argument<Id>` on the destination (e.g. `SelectedCategoryId`, `SelectedCurrencyId`, `SelectedIconId`). The caller encodes it in `Action.Request`; the `Default*UseCase` implementation reads it and appends it as a nav arg only when it is `Id.Known`:

```kotlin
// In Action.Request
data class Request(val selectedCategoryId: Id = Id.Unknown) : Action

// In DefaultTransactionEditCategoryUseCase.perform()
val args = buildList {
    add(Destinations.Category.Picker.RequestId.withValue(id))
    (action.selectedCategoryId as? Id.Known)?.let { selectedId ->
        add(Destinations.Category.Picker.SelectedCategoryId.withValue(selectedId))
    }
}
navigator.navigateTo(Destinations.Category.Picker, *args.toTypedArray())
```

The picker's `NavigatorEntry` reads the arg and passes it to the component builder via `@BindsInstance`. The ViewModel stores it in initial state; the ViewProvider uses it to render a selection ring.

The caller passes its current selection when performing `Action.Request` — not a hardcoded default. This way the ring appears on whatever was last picked, including across re-opens.

## Bottom Sheet Destinations

Use `NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet` for destinations that should appear as a bottom sheet overlay. In `MainActivityScreenViewProvider` these are registered as `dialog {}` destinations (so they overlay the current screen rather than replacing it), wrapped in `BottomSheetNavDestination` which owns the `ModalBottomSheetLayout` show/dismiss logic.
