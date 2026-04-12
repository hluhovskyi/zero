# Dependency Injection (Dagger)

## Component Structure

- Components have `Dependencies` (provided by parent graph) and `@BindsInstance` builder methods
- **`@BindsInstance` is for lightweight values**: handler callbacks, IDs — not resolved domain objects
- Complex domain data (e.g. `ColorScheme`) should be resolved inside the component via a repository in `Dependencies`, not passed through the builder
- When a component needs a repository, add it to its `Dependencies` interface; the parent `ActivityComponent` likely already implements it

## Qualifying Multiple Bindings of the Same Primitive Type

When a component needs two or more `@BindsInstance` values of the same type (e.g. two `Id` parameters), use dedicated `@Qualifier` annotations — **not** `@Named("string")`. Declare them as `private` annotations in the same file as the component:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class ColorId

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class SelectedIconId
```

Then annotate both the builder method parameter and the `@Provides` method parameter:

```kotlin
@BindsInstance
fun colorId(@ColorId colorId: Id): Builder

@Provides
fun viewModel(@ColorId colorId: Id, @SelectedIconId selectedIconId: Id): ViewModel
```

This applies to any primitive type (`Id`, `String`, `Boolean`, etc.) — not just `Id`.

## Lifecycle Timing

Resolve dependencies that require runtime state (e.g. looking up a scheme by ID) in `attach()`, not in the constructor or `@Provides` methods. This ensures the component is fully wired before resolution occurs.
