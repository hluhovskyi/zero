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

**When a feature combines long-lived bindings (attached for the screen) with per-navigation bindings (rebuilt per visit), make it a dedicated `@Component` with its own `@Scope` — not a `@Module` included into the parent.** The parent receives only the built feature component and asks it for `attach()` and (where relevant) its `NavigatorEntry`.

Why: a `@Module` included into the parent puts every binding into the parent's graph, forcing the parent to expose abstract fields just to call `.attach()` on them, and leaks the parent's `@Scope` into the feature's file. The parent ends up knowing internals it shouldn't. See `FeedbackComponent` for the canonical example.

Single-binding features (e.g., a picker that registers one `NavigatorEntry`) stay in the parent's module — this rule kicks in only once binding count climbs and the lifetimes diverge.

## Feature Components Live in `zero-core`; `app` Owns Navigation Wiring

**Put the feature `@Component` in `zero-core`, not `app`.** `Navigator`, `NavigatorScope`, `NavigatorEntry`, `Destinations`, and `BuildConfig` are all `internal` to `app` — that's the seam. A feature component in `zero-core` exposes plain factories or callbacks (`val sheetComponentFactory: () -> SheetComponent`, `onTriggered: () -> Unit`); `app`'s wiring module wraps those in a `NavigatorEntry` that calls into them. Keeps feature logic testable without Android-navigation coupling and limits `app` to gluing components to the nav graph.

## Collapse `Factory.create()` Parameters Into `Dependencies` When Constant

**`Factory.create(...)` parameters earn their place only when at least one call site needs a different value.** If every call site passes the same constants (single `BuildConfig.DEBUG`, same string resource, same back handler), move them into `Dependencies` and let Dagger build the component directly — drop the factory indirection. Reserve `Factory.create(...)` for genuinely reused components like `ColorPickerComponent`, which gets a different `OnColorSelectedHandler` per registration site.
