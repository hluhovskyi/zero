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

## Component Builder Providers Must Be Unscoped

**Never put a `@Scope` annotation on a `@Provides` function that returns a `Component.Builder`** — scoping makes the Builder a shared mutable singleton across the scope's lifetime. Different consumers call different `@BindsInstance` setters on it, and values from one consumer leak into the next. Leave the Builder provider unscoped so each consumer gets a fresh Builder with the factory defaults intact. Enforced by lint rule `ScopedComponentBuilder`.

## Pre-Configured Builder Defaults via Qualifier

**When a Builder handler is identical across every consumer (typical: a Navigator-bound "navigate to fixed destination" handler), set it as a default on a qualifier-tagged `@Provides` at the layer that owns the Navigator** — don't thread the handler through each consumer's `@BindsInstance`. The qualifier marks the pre-configured Builder so injection sites opt in explicitly:

```kotlin
@Qualifier @Retention(AnnotationRetention.RUNTIME)
private annotation class ForMainActivity

@Provides @ForMainActivity
fun transactionComponentBuilderForMainActivity(
    builder: TransactionComponent.Builder,
    navigator: Navigator,
): TransactionComponent.Builder = builder.onDuplicateTransactionHandler { id ->
    navigator.navigateTo(Destinations.Transaction.Item.Duplicate, ...)
}

// At injection site:
fun homeNavigationEntry(
    @ForMainActivity transactionComponentBuilder: TransactionComponent.Builder,
    ...
)
```

Reuse a single qualifier across multiple Builder types — Dagger picks the matching `@Provides` by parameter type at each injection site.

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
