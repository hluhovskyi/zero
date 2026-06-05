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

### 2. Default Interface Methods Return Null When Mocked

Mockito does **not** call the real implementation of default interface methods — it returns null like any other unstubbed method. This differs from Kotlin extension functions, which always execute real code regardless of mocking.

**Stub the method directly:**
```kotlin
whenever(zonedClock.localDateTime()).thenReturn(LocalDateTime(2024, 1, 16, 9, 0))
```

**Do not** rely on the default implementation running because you stubbed its dependencies:
```kotlin
// WRONG — localDateTime() returns null, not now().toLocalDateTime(timeZone())
whenever(zonedClock.now()).thenReturn(someInstant)
whenever(zonedClock.timeZone()).thenReturn(TimeZone.UTC)
```

### 3. Verified Stubbing
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

## E2e: Seed Before the Activity Launches

**Don't use `createAndroidComposeRule<MainActivity>()` as a `@Rule` for e2e tests that seed data** — it launches the activity *before* the test body, so the seed runs after the app has subscribed, and the transaction list's `selectAfter(updatedDateTime > initialTimestamp)` races the seed (past-timestamp rows always lose, blank screens time out). Use `createEmptyComposeRule()` + a lazy `ActivityScenario.launch(MainActivity::class.java)` inside `onTransactions()` / `onBudget()`, so the body seeds first and the activity attaches against a populated DB — no `Clock.now() + 1.hour` timestamp hacks. (PR #282, `BaseE2eTest`.)

**Lazy launch skips the production preset seed** (`PresetsAttachable.attach()` seeds default categories/accounts on attach) — restore it explicitly via `DatabaseTestBridge.seedPresets()` in `clearDataRule` so every test starts at the fresh-install baseline.

## Coroutines Testing

*   **`runTest`:** Always use `runTest` for testing suspending functions or `Flow` collections.
*   **`runCurrent()` after `attach()`** — `attach()` launches work on `backgroundScope`, which uses `StandardTestDispatcher`. Use `runCurrent()` to drain it. `advanceUntilIdle()` does **not** drain `backgroundScope` from the outer test scope and will leave state at its initial defaults — a silent failure.
*   **`advanceUntilIdle()`**: Use only for coroutines launched on the test's own scope (not `backgroundScope`).
