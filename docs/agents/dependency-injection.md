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

**For any feature with a lifecycle side effect — background work, SDK init, subscribers, schedulers — create a Dagger component that exposes `abstract val attachable: Attachable` rather than a standalone factory or a direct call from `MainApplication.onCreate`.** A companion `create(...)` or `builder(...)` factory builds the component; the Module provides the use case and an `Attachable` impl. `MainApplication.onCreate` triggers the cascade through `ApplicationComponent.attachable → AttachApplicationComponent → child components`. See `PresetsComponent` (background subscriber) and `CrashComponent` (SDK init) as canonical examples.

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

## Choose the Module That Matches the Feature's Dependencies

**Feature `@Component` classes live where their dependencies allow.** A feature with no `app`-internal types (`Navigator`, `Destinations`, `BuildConfig`) and no Android-only types belongs in `zero-core` — and benefits from being KMP-portable later. A feature that needs sensors, Android lifecycle, navigation, or `BuildConfig` lives in `app` — splitting it would require an abstraction layer that isn't worth the cost for a single consumer.

The split inside a feature can be hybrid: keep the pure UI/logic (`FeedbackSheetComponent`, `InMemoryBreadcrumbs`) in `zero-core` and put the Android-coupled orchestrator (`FeedbackComponent`, `ShakeDetector`) in `app`. The `app`-side Dagger component consumes the zero-core pieces directly via its graph.

## Collapse `Factory.create()` Parameters Into `Dependencies` When Constant

**`Factory.create(...)` parameters earn their place only when at least one call site needs a different value.** If every call site passes the same constants (single `BuildConfig.DEBUG`, same string resource, same back handler), move them into `Dependencies` and let Dagger build the component directly — drop the factory indirection. Reserve `Factory.create(...)` for genuinely reused components like `ColorPickerComponent`, which gets a different `OnColorSelectedHandler` per registration site.
