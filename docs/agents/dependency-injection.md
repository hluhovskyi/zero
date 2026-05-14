# Dependency Injection (Dagger)

## Component Structure

- Components have `Dependencies` (provided by parent graph) and `@BindsInstance` builder methods
- **`@BindsInstance` is for lightweight values**: handler callbacks, IDs — not resolved domain objects
- Complex domain data (e.g. `ColorScheme`) should be resolved inside the component via a repository in `Dependencies`, not passed through the builder
- When a component needs a repository, add it to its `Dependencies` interface; the parent `ActivityComponent` likely already implements it

## Qualifying Multiple Bindings of the Same Primitive Type

**When a component needs two `@BindsInstance` values of the same type, use `@Qualifier` annotations — not `@Named("string")`** — typos in name strings are silent bugs; dedicated annotations are checked at compile time. Declare them `private` in the same file:

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) private annotation class ColorId
@Qualifier @Retention(AnnotationRetention.BINARY) private annotation class SelectedIconId
```

Applies to any repeated primitive type (`Id`, `String`, `Boolean`, etc.).

## Use Case Scope

**Scope a use case to the smallest component that needs it** — create it in `FeatureComponent.Module` via `@Provides`. Only add it to `Dependencies` and promote to `ActivityComponent` if two or more feature components need it.

## Lifecycle Timing

Resolve dependencies that require runtime state (e.g. looking up a scheme by ID) in `attach()`, not in the constructor or `@Provides` methods. This ensures the component is fully wired before resolution occurs.

## Attach-Only Components

**For background work with no UI, create a Dagger component that exposes `abstract val attachable: Attachable` — not a standalone factory function.** A companion `create(...)` factory builds the component; the Module provides the use case and an `Attachable` impl. See `PresetsComponent` as the canonical example.

The private `Attachable` impl owns a default `CoroutineScope` as a constructor default — **never inject `CoroutineScope` via `@BindsInstance`**:

```kotlin
private class PresetsAttachable(
    private val useCase: PresetsUseCase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : Attachable {
    override fun attach(): Closeable = Closeables.of { scope.launch { useCase.seed() } }
}
```

## Mixed-Lifecycle Features Get Their Own Component

**If you find yourself adding `protected abstract val X` to a parent component just to call `.attach()` on it, stop — extract a dedicated `@Component` with its own `@Scope` instead.** That field is the signal that you've leaked a feature's internals into the parent's graph; the parent should only see the built feature component and ask it for `attach()` and (where relevant) its `NavigatorEntry`.

Single-binding features (e.g., a picker that registers one `NavigatorEntry`) stay in the parent's module — this rule kicks in only when there's lifecycle state to attach. See `FeedbackComponent` for the canonical example.

## Feature Components Live in `zero-core`; `app` Owns Navigation Wiring

**Feature `@Component` classes go in `zero-core`, not `app`.** Activity-shell / navigation-host components (`MainActivityScreenComponent`, `BottomBarComponent`) stay in `app` — they're what defines the seam. Everything else: in `zero-core`. `Navigator`, `NavigatorScope`, `NavigatorEntry`, `Destinations`, and `BuildConfig` are all `internal` to `app`, so a feature component in `zero-core` exposes plain factories or callbacks (`val sheetComponentFactory: () -> SheetComponent`, `onTriggered: () -> Unit`); `app`'s wiring module wraps those in a `NavigatorEntry` that calls into them.

## Collapse `Factory.create()` Parameters Into `Dependencies` When Constant

**`Factory.create(...)` parameters earn their place only when at least one call site needs a different value.** If every call site passes the same constants (single `BuildConfig.DEBUG`, same string resource, same back handler), move them into `Dependencies` and let Dagger build the component directly — drop the factory indirection. Reserve `Factory.create(...)` for genuinely reused components like `ColorPickerComponent`, which gets a different `OnColorSelectedHandler` per registration site.
