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
