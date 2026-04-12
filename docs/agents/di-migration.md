# DI Migration — Dagger → Manual (Experimental)

> **Status: Experimental.** `ColorPickerComponent` has been migrated as a proof of concept. The approach is validated and documented here. Full migration has not been committed to.

## Why

Dagger relies on KAPT (annotation processing), which is Android-only and cannot run in a KMP `commonMain` source set. Removing Dagger is a prerequisite for sharing business logic across platforms.

Secondary benefits: faster builds (no annotation processing), simpler stack traces, no generated code to debug.

## Alternatives Considered

| Option | Code gen | KMP | Verdict |
|---|---|---|---|
| **Koin** | No | Yes | Best pragmatic choice if a framework is wanted. Service-locator pattern, runtime errors instead of compile-time. Low migration effort. |
| **kotlin-inject** | Yes (KSP) | Yes | Architecturally closest to Dagger. KSP works on KMP. Less mature, smaller community. |
| **Manual DI** | No | Yes | Maximum control, zero runtime overhead. More boilerplate than Koin but fully explicit. **Chosen approach.** |

## The Migration Pattern

### Component

`@dagger.Component abstract class` → concrete class with `private constructor`.

```kotlin
// Before
@MyScope
@dagger.Component(modules = [Module::class], dependencies = [Dependencies::class])
abstract class FooComponent : AttachableViewComponent { ... }

// After
class FooComponent private constructor(
    dep1: Dep1,
    dep2: Dep2,
) : AttachableViewComponent { ... }
```

### Scoping

`@MyScope` on `@Provides` → `by lazy` on the component property. Created once, lives with the component instance.

```kotlin
// Before (@Provides @MyScope)
fun viewModel(dep: Dep): FooViewModel = DefaultFooViewModel(dep)

// After
private val viewModel: FooViewModel by lazy {
    DefaultFooViewModel(dep = dep)
}
```

### Factory (replaces Builder + @BindsInstance)

`@BindsInstance` builder methods → named parameters on `Factory.create()`. `Factory` holds only the wired `Dependencies`; all runtime overrides are passed to `create()`.

```kotlin
// Before
@dagger.Component.Builder
interface Builder : Buildable<FooComponent> {
    fun dependencies(d: Dependencies): Builder
    @BindsInstance fun onSelected(h: OnSelectedHandler): Builder
}
// Call site: FooComponent.builder(deps).onSelected(handler).build()

// After
class Factory(private val dependencies: Dependencies) {
    fun create(
        onSelected: OnSelectedHandler = OnSelectedHandler.Noop,
    ): FooComponent = FooComponent(
        dep = dependencies.dep,
        onSelected = onSelected,
    )
}
// Call site: factory.create(onSelected = handler)
```

Named Kotlin parameters replace builder chaining. Default values replace the defaults previously set in `companion object { fun builder(...) }`.

### Dependencies interface

Unchanged. The `Dependencies` interface on each component is already the right abstraction — it documents exactly what the parent must provide. Keep it as-is.

### Multiple values of the same type (was: @Qualifier)

Before, two `Id` values required `@ColorId` / `@SelectedIconId` qualifier annotations. With `Factory.create()`, they become distinct named parameters — no qualifiers needed.

```kotlin
// Before
@BindsInstance fun colorId(@ColorId id: Id): Builder
@BindsInstance fun selectedIconId(@SelectedIconId id: Id): Builder

// After
fun create(colorId: Id = Id.Unknown, selectedIconId: Id = Id.Unknown): FooComponent
```

## Infrastructure Changes

Two small changes were made to the shared infrastructure to support migrated components alongside un-migrated ones.

### Buildable now extends () -> T

```kotlin
interface Buildable<T> : () -> T {
    fun build(): T
    override fun invoke(): T = build()
}
```

This lets `AttachWithView` work uniformly on any `() -> Component` — whether the caller passes a Dagger `Buildable` or a plain lambda wrapping `factory.create(...)`.

### AttachWithView on () -> Component

```kotlin
@Composable
fun <Component : AttachableViewComponent> (() -> Component).AttachWithView() {
    val component = remember(this)
    component.AttachAndRetainWithView()
}
```

Existing `Buildable` builders work unchanged (they are now `() -> T`). New factory lambdas work the same way.

### NavigatorScope.component

`NavigatorScope` has two entry points with identical implementations. The name signals intent:

```kotlin
// Dagger-based component (un-migrated)
navigatorScope.buildable(...) {
    componentBuilder.onXxx(handler).logging(logger)  // returns Buildable = () -> T ✓
}

// Manual factory (migrated)
navigatorScope.component(...) {
    { factory.create(onXxx = handler).logging(logger) }
}
```

As each component is migrated, its call site switches from `buildable` to `component`.

## Migration Checklist (per component)

1. Convert `abstract class FooComponent` to `class FooComponent private constructor(...)`
2. Remove `@dagger.Component`, `@dagger.Module`, all scope and qualifier annotations
3. Replace `@Provides @Scope fun viewModel(...)` with `private val viewModel by lazy { ... }`
4. Replace `@Provides @Scope fun viewProvider(...)` with `override val viewProvider by lazy { ... }`
5. Replace `Builder : Buildable<FooComponent>` with `Factory(dependencies)` + `create(overrides...)`
6. Update `companion object` from `DaggerFooComponent.builder()` to `Factory(dependencies)`
7. Remove Dagger imports
8. Update the parent component's `@Provides fun fooBuilder(...)` to return `FooComponent.Factory`
9. Update the `Dependencies` interface in the parent that referenced `FooComponent.Builder` to `FooComponent.Factory`
10. Update the `navigatorScope.buildable { builder... }` call site to `navigatorScope.component { { factory.create(...) } }`

## Migration Order

Work leaves-first, root-last:

```
Leaf components (no sub-components):
  ColorPickerComponent    ✅ done
  CurrencyPickerComponent
  IconPickerComponent
  CategoryPickerComponent
  SettingsComponent
  TransactionPreviewComponent
  ImportFilePickerComponent
  ImportAccountPickerComponent
  ImportCategoryPickerComponent
  ImportTransactionPreviewComponent

Mid-level (have sub-component dependencies):
  AccountEditComponent
  CategoryEditComponent
  TransactionEditExpenseIncomeComponent
  TransactionEditTransferComponent
  TransactionEditComponent
  ImportComponent
  AccountComponent
  CategoryComponent
  TransactionComponent

Screen-level:
  BottomBarComponent
  MainActivityScreenComponent

Root:
  ActivityComponent
  DatabaseComponent
  ApplicationComponent
```

`ActivityComponent` and `DatabaseComponent` are the hardest — they wire the whole graph and have more `@BindsInstance` params and multibindings (`@IntoSet` for `NavigatorEntry`). The `@IntoSet` set becomes an explicit `listOf(...)` in the manual version.

## Pros and Cons

**Pros**
- No code generation, no KAPT — faster builds, works in KMP `commonMain`
- All wiring is explicit plain Kotlin — readable, debuggable, no generated class layer
- `Factory.create()` with named params is cleaner than builder chaining for call sites with multiple overrides
- Each component file gets shorter (no `abstract class`, no `@Module object`, no annotations)

**Cons**
- No compile-time graph validation — a missing dependency is a runtime crash, not a build error
- `by lazy` scoping is manual — easy to accidentally break singleton semantics by using a property directly instead of going through lazy
- `@IntoSet` multibindings require a manual `listOf(...)` assembly at the root — slightly more fragile than Dagger's automatic set wiring
- More boilerplate at the root components (`ActivityComponent`, `ApplicationComponent`) where many dependencies must be threaded explicitly
