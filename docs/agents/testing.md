# Testing Guidelines

This project uses **JUnit 4**, **Mockito-Kotlin**, and **Kotlinx-Coroutines-Test**.

## Test Placement

*   Unit tests should be placed in `src/test/java/com/hluhovskyi/zero/...` within their respective modules (`zero-core`, `zero-database`, etc.).
*   Mirror the package structure of the code being tested.

## Mockito Best Practices

### 1. Type Inference in Mockito + Flow

The Kotlin compiler often fails to infer types for generic methods (like `Repository.query<T>`) when used with Mockito matchers. **Always specify types explicitly** to avoid compilation errors.

**Bad:**
```kotlin
whenever(repo.query(any())).thenReturn(flowOf(emptyList()))
```

**Good:**
```kotlin
whenever(repo.query(any<Repository.Criteria<List<Item>>>())).thenReturn(flowOf(emptyList()))
```

### 2. Verified Stubbing
Use `whenever(...).thenReturn(...)` for stubbing and `verify(...)` for interaction testing. For complex matchers, use `isA<T>()` or `argumentCaptor<T>()`.

## DRY Subjects (SUT)

To keep tests readable and maintainable, **extract the creation of the Subject Under Test (SUT)** into a private factory function. This allows you to easily update dependencies or default configurations in one place.

```kotlin
class MyViewModelTest {
    @Mock lateinit var repo: MyRepository
    // ... other mocks

    private fun createViewModel(
        coroutineScope: CoroutineScope = TestScope()
    ) = MyViewModel(repo, ..., coroutineScope)

    @Test
    fun `some test`() = runTest {
        val viewModel = createViewModel(this)
        // ...
    }
}
```

## Coroutines Testing

*   **`runTest`:** Always use `runTest` for testing suspending functions or `Flow` collections.
*   **`runCurrent()`:** Use to trigger any pending tasks in the current dispatcher (useful after `attach()` or `perform()`).
## Manual & UI Verification

When fixing UI, layout, or interaction bugs (e.g., bottom sheets, overlapping views):

1.  **Compilation is not validation.** Do not claim success just because the project builds.
2.  **Verify via ADB.** Use the `android-ui-inspector` skill (`./scripts/dump-ui.sh`) to dump the view hierarchy and empirically verify `bounds="[x,y][w,h]"` and visibility.
3.  **Library updates first.** Before implementing complex UI workarounds, check if a minor version bump of the relevant library (e.g., `navigation-compose`) provides a native API fix.

