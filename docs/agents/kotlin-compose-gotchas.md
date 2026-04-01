# Kotlin / Compose Gotchas

## Interface Default Methods + `@Composable`

Interface default methods with `@Composable` and default parameters: Kotlin's `DefaultImpls` may execute the interface body instead of dispatching to the class override. Fix: make the method abstract (no body); add explicit overrides to all implementations.

## ComposeColor Constructor

`ComposeColor(packedLong)` encodes colorspace index in lower 6 bits — invalid for arbitrary hex values. Always use `ComposeColor(argb: Int)`.

## Type Inference in Mockito + Flow

When using Mockito's `whenever` with generic methods that return `Flow<T>`, the Kotlin compiler often fails to infer the correct type, leading to "Cannot infer type for type parameter 'T'" errors.

### The Fix

Explicitly specify the type parameter in the `any()` matcher or the `whenever` call.

**Bad:**
```kotlin
whenever(repository.query(any())).thenReturn(flowOf(emptyList()))
```

**Good:**
```kotlin
whenever(repository.query(any<Repository.Criteria<List<Item>>>())).thenReturn(flowOf(emptyList()))
```

Similarly, for default parameters in interfaces:
```kotlin
// Bad
fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow()): Flow<T>

// Good
fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow<Any>()): Flow<T>
```
