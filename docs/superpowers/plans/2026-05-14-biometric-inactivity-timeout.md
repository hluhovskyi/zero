# Biometric Inactivity Timeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current "lock on every background" behavior with a 30-minute inactivity timer, and auto-trigger the biometric prompt whenever the app foregrounds while locked.

**Architecture:** `BiometricLockUseCase` owns the timeout policy (in-memory `lastBackgroundedAt`, injected `Clock`) and exposes a `Flow<Unit>` of auto-prompt requests. The lifecycle observer becomes a thin lifecycle→use-case adapter. The gate ViewModel exposes a monotonic `promptToken: Int`; the ViewProvider's `LaunchedEffect(promptToken)` re-fires the prompt on each new token.

**Tech Stack:** Kotlin, kotlinx-datetime `Instant` + `Duration`, Dagger, `MutableSharedFlow`, JUnit + `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `advanceUntilIdle`).

**Spec:** `docs/superpowers/specs/2026-05-14-biometric-inactivity-timeout-design.md`

---

## Task 1: Plumbing — interface surface + Clock dependency

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/security/BiometricLockUseCase.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCase.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/security/BiometricLockComponent.kt`

- [ ] **Step 1: Extend `BiometricLockUseCase` interface**

Add three members and update `Noop`:

```kotlin
interface BiometricLockUseCase {

    val enabled: Flow<Boolean>
    val lockState: StateFlow<LockState>
    val autoPromptRequests: Flow<Unit>

    suspend fun setEnabled(value: Boolean)
    fun lock()
    fun unlock()
    fun onAppBackgrounded()
    fun onAppForegrounded()

    sealed interface LockState {
        object Locked : LockState
        object Unlocked : LockState
    }

    object Noop : BiometricLockUseCase {
        override val enabled: Flow<Boolean> = flowOf(false)
        override val lockState: StateFlow<LockState> = MutableStateFlow(LockState.Unlocked)
        override val autoPromptRequests: Flow<Unit> = emptyFlow()
        override suspend fun setEnabled(value: Boolean) = Unit
        override fun lock() = Unit
        override fun unlock() = Unit
        override fun onAppBackgrounded() = Unit
        override fun onAppForegrounded() = Unit
    }
}
```

Add `import kotlinx.coroutines.flow.emptyFlow` if not already present.

- [ ] **Step 2: Stub the new members on `DefaultBiometricLockUseCase`**

Make it compile against the new interface — real behavior comes in Task 2.

Add a `clock: Clock` constructor parameter, a `mutableAutoPromptRequests: MutableSharedFlow<Unit>` field (`extraBufferCapacity = 1`, `onBufferOverflow = BufferOverflow.DROP_OLDEST`), expose it via `autoPromptRequests`, and override `onAppBackgrounded` / `onAppForegrounded` as `= Unit` no-ops for now. Existing `enabled`, `lockState`, `setEnabled`, `lock`, `unlock` stay unchanged.

Imports to add:
```kotlin
import com.hluhovskyi.zero.common.time.Clock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
```

- [ ] **Step 3: Add `Clock` to `BiometricLockComponent.Dependencies` and module**

In `BiometricLockComponent.kt`:

```kotlin
interface Dependencies {
    val context: Context
    val configurationRepository: ConfigurationRepository
    val clock: Clock
}
```

Add `import com.hluhovskyi.zero.common.time.Clock` at the top.

In the `Module.biometricLockUseCase` provider, thread the clock through:

```kotlin
@Provides
@BiometricLockScope
fun biometricLockUseCase(
    configurationRepository: ConfigurationRepository,
    clock: Clock,
): BiometricLockUseCase = DefaultBiometricLockUseCase(
    configurationRepository = configurationRepository,
    clock = clock,
)
```

`ActivityComponent` already inherits `BiometricLockComponent.Dependencies` and already exposes `val clock: Clock`, so no caller-side edits are needed.

- [ ] **Step 4: Build to confirm wiring compiles**

```bash
./gradlew :app:compileDebugKotlin :zero-api:compileKotlin :zero-core:compileDebugKotlin 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/security/BiometricLockUseCase.kt \
        app/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCase.kt \
        app/src/main/java/com/hluhovskyi/zero/security/BiometricLockComponent.kt
git commit -m "refactor(security): extend BiometricLockUseCase surface for inactivity timeout"
git push
```

---

## Task 2: TDD `DefaultBiometricLockUseCase` timeout + auto-prompt behavior

**Files:**
- Create: `app/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCaseTest.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCase.kt`

- [ ] **Step 1: Confirm `app/src/test` has Kotlin test infra**

```bash
ls app/src/test/java 2>&1 | head -5
find app/src/test -name "*.kt" -maxdepth 6 | head -3
grep -n "testImplementation" app/build.gradle.kts 2>&1 | head -10
```
If `app/src/test/java` does not yet exist, you'll need to create the directory. The deps (JUnit, coroutines-test, kotlinx-datetime) are inherited from the project's root config; verify quickly by reading `app/build.gradle.kts` and adding `testImplementation` lines mirroring `zero-core/build.gradle.kts` if they're missing.

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCaseTest.kt`:

```kotlin
package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.config.ConfigurationKey
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultBiometricLockUseCaseTest {

    private class MutableClock(private var current: Instant) : Clock {
        override fun now(): Instant = current
        fun advance(durationMs: Long) {
            current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + durationMs)
        }
    }

    private class FakeConfigurationRepository : ConfigurationRepository {
        private val enabled = MutableStateFlow(true)

        @Suppress("UNCHECKED_CAST")
        override fun <Value : Any> observe(
            key: ConfigurationKey<Value>,
            valueClass: KClass<Value>,
        ): Flow<Value> = enabled as Flow<Value>

        override suspend fun <Value : Any> write(
            key: ConfigurationKey<Value>,
            valueClass: KClass<Value>,
            value: Value,
        ) {
            enabled.value = value as Boolean
        }
    }

    private fun newUseCase(clock: MutableClock) = DefaultBiometricLockUseCase(
        configurationRepository = FakeConfigurationRepository(),
        clock = clock,
    )

    @Test
    fun `onAppForegrounded with no prior background locks and emits prompt request`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)

        useCase.onAppForegrounded()

        assertEquals(LockState.Locked, useCase.lockState.value)
        val emission = withTimeoutOrNull(100) { useCase.autoPromptRequests.first() }
        assertEquals(Unit, emission)
    }

    @Test
    fun `onAppForegrounded within 30 minutes of background does not lock`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.unlock()

        useCase.onAppBackgrounded()
        clock.advance(29.minutes.inWholeMilliseconds + 59.seconds.inWholeMilliseconds)
        useCase.onAppForegrounded()

        assertEquals(LockState.Unlocked, useCase.lockState.value)
    }

    @Test
    fun `onAppForegrounded at exactly 30 minutes locks`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.unlock()

        useCase.onAppBackgrounded()
        clock.advance(30.minutes.inWholeMilliseconds)
        useCase.onAppForegrounded()

        assertEquals(LockState.Locked, useCase.lockState.value)
    }

    @Test
    fun `onAppForegrounded while already locked emits prompt request even within timeout`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.lock()

        useCase.onAppBackgrounded()
        clock.advance(5.minutes.inWholeMilliseconds)
        useCase.onAppForegrounded()

        assertEquals(LockState.Locked, useCase.lockState.value)
        val emission = withTimeoutOrNull(100) { useCase.autoPromptRequests.first() }
        assertEquals(Unit, emission)
    }

    @Test
    fun `onAppForegrounded while unlocked and within timeout does not emit prompt request`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.unlock()

        useCase.onAppBackgrounded()
        clock.advance(1.minutes.inWholeMilliseconds)
        useCase.onAppForegrounded()

        val emission = withTimeoutOrNull(50) { useCase.autoPromptRequests.first() }
        assertNull(emission)
    }
}
```

- [ ] **Step 3: Run the tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.hluhovskyi.zero.security.DefaultBiometricLockUseCaseTest" 2>&1 | tail -30
```
Expected: tests fail because `onAppBackgrounded` and `onAppForegrounded` are still no-ops.

- [ ] **Step 4: Implement the timeout + emit logic**

Add to `DefaultBiometricLockUseCase`:

```kotlin
private var lastBackgroundedAt: Instant? = null

override fun onAppBackgrounded() {
    lastBackgroundedAt = clock.now()
}

override fun onAppForegrounded() {
    val backgroundedAt = lastBackgroundedAt
    val elapsed: Duration? = backgroundedAt?.let { clock.now() - it }
    if (elapsed == null || elapsed >= INACTIVITY_TIMEOUT) {
        mutableLockState.value = LockState.Locked
    }
    if (mutableLockState.value is LockState.Locked) {
        mutableAutoPromptRequests.tryEmit(Unit)
    }
}

companion object {
    private val INACTIVITY_TIMEOUT: Duration = 30.minutes
}
```

Add imports: `kotlinx.datetime.Instant`, `kotlin.time.Duration`, `kotlin.time.Duration.Companion.minutes`.

Notes:
- `Instant - Instant` returns `kotlin.time.Duration` via kotlinx-datetime's operator. If the build rejects it, use `(clock.now().toEpochMilliseconds() - it.toEpochMilliseconds()).milliseconds`.
- The emit is intentionally not gated on `enabled` — the gate ViewModel's `combine(enabled, lockState)` already short-circuits when disabled, so an extra emission is harmless.

- [ ] **Step 5: Run the tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.hluhovskyi.zero.security.DefaultBiometricLockUseCaseTest" 2>&1 | tail -20
```
Expected: 5 tests, all pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCaseTest.kt \
        app/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCase.kt
git commit -m "feat(security): inactivity timeout + auto-prompt request emission"
git push
```

---

## Task 3: Simplify `BiometricLockLifecycleObserver`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/security/BiometricLockLifecycleObserver.kt`

- [ ] **Step 1: Swap the lifecycle overrides**

Delete the `onCreate` and `onStop` overrides. Replace with:

```kotlin
override fun onStart(owner: LifecycleOwner) {
    biometricLockUseCase.onAppForegrounded()
}

override fun onStop(owner: LifecycleOwner) {
    if (!activity.isFinishing) {
        biometricLockUseCase.onAppBackgrounded()
    }
}
```

`onCreate` is removed: cold start fires `onCreate → onStart` synchronously before composition, and `onAppForegrounded()` handles the lock in the timeout-elapsed branch (because `lastBackgroundedAt == null`).

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/security/BiometricLockLifecycleObserver.kt
git commit -m "refactor(security): lifecycle observer delegates to onAppBackgrounded/onAppForegrounded"
git push
```

---

## Task 4: Update `BiometricLockGateViewModel` surface

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/security/BiometricLockGateViewModel.kt`

- [ ] **Step 1: Replace `canPromptOnLaunch` with `promptToken` and drop `PromptShown`**

```kotlin
package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface BiometricLockGateViewModel : AttachableActionStateModel<BiometricLockGateViewModel.Action, BiometricLockGateViewModel.State> {

    sealed interface Action {
        object Unlock : Action
    }

    data class State(
        val isLocked: Boolean = false,
        val promptToken: Int = 0,
    )

    object Noop : BiometricLockGateViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
```

- [ ] **Step 2: Build (will fail at `DefaultBiometricLockGateViewModel` and `BiometricLockGateViewProvider`)**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20
```
Expected: errors referencing `Action.PromptShown` and `canPromptOnLaunch` — fixed in Tasks 5 and 6.

Do not commit yet; the next two tasks restore the build.

---

## Task 5: TDD `DefaultBiometricLockGateViewModel` promptToken behavior

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModelTest.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModel.kt`

- [ ] **Step 1: Write the failing tests**

Create `zero-core/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModelTest.kt`:

```kotlin
package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.security.BiometricAuthenticator.AuthReason
import com.hluhovskyi.zero.security.BiometricAuthenticator.Result
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultBiometricLockGateViewModelTest {

    private class FakeBiometricLockUseCase(
        initialEnabled: Boolean = true,
        initialLock: LockState = LockState.Locked,
    ) : BiometricLockUseCase {

        private val mutableEnabled = MutableStateFlow(initialEnabled)
        private val mutableLockState = MutableStateFlow(initialLock)
        val mutablePromptRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

        override val enabled: Flow<Boolean> = mutableEnabled
        override val lockState: StateFlow<LockState> = mutableLockState
        override val autoPromptRequests: Flow<Unit> = mutablePromptRequests

        override suspend fun setEnabled(value: Boolean) { mutableEnabled.value = value }
        override fun lock() { mutableLockState.value = LockState.Locked }
        override fun unlock() { mutableLockState.value = LockState.Unlocked }
        override fun onAppBackgrounded() = Unit
        override fun onAppForegrounded() = Unit
    }

    private class FakeBiometricAuthenticator(
        private val result: Result = Result.Success,
    ) : BiometricAuthenticator {
        var calls: Int = 0
            private set
        override suspend fun authenticate(reason: AuthReason): Result {
            calls++
            return result
        }
    }

    private fun newViewModel(
        useCase: BiometricLockUseCase,
        authenticator: BiometricAuthenticator,
        scope: CoroutineScope,
    ): DefaultBiometricLockGateViewModel = DefaultBiometricLockGateViewModel(
        biometricLockUseCase = useCase,
        biometricAuthenticator = authenticator,
        coroutineScope = scope,
    )

    @Test
    fun `autoPromptRequest while locked increments promptToken`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = FakeBiometricLockUseCase()
        val viewModel = newViewModel(useCase, FakeBiometricAuthenticator(), CoroutineScope(dispatcher))
        viewModel.attach()
        advanceUntilIdle()

        useCase.mutablePromptRequests.tryEmit(Unit)
        advanceUntilIdle()
        useCase.mutablePromptRequests.tryEmit(Unit)
        advanceUntilIdle()

        val state = viewModel.state.dropWhile { it.promptToken < 2 }.first()
        assertEquals(2, state.promptToken)
        assertEquals(true, state.isLocked)
    }

    @Test
    fun `autoPromptRequest while unlocked does not increment promptToken`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = FakeBiometricLockUseCase(initialLock = LockState.Unlocked)
        val viewModel = newViewModel(useCase, FakeBiometricAuthenticator(), CoroutineScope(dispatcher))
        viewModel.attach()
        advanceUntilIdle()

        useCase.mutablePromptRequests.tryEmit(Unit)
        advanceUntilIdle()

        val state = viewModel.state.first()
        assertEquals(0, state.promptToken)
        assertEquals(false, state.isLocked)
    }

    @Test
    fun `Unlock action delegates to authenticator and unlocks on success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = FakeBiometricLockUseCase()
        val authenticator = FakeBiometricAuthenticator(Result.Success)
        val viewModel = newViewModel(useCase, authenticator, CoroutineScope(dispatcher))
        viewModel.attach()
        advanceUntilIdle()

        viewModel.perform(BiometricLockGateViewModel.Action.Unlock)
        advanceUntilIdle()

        assertEquals(1, authenticator.calls)
        assertEquals(LockState.Unlocked, useCase.lockState.value)
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail to compile**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.security.DefaultBiometricLockGateViewModelTest" 2>&1 | tail -20
```
Expected: compile error — `DefaultBiometricLockGateViewModel` still references `Action.PromptShown` and `canPromptOnLaunch`.

- [ ] **Step 3: Rewrite `DefaultBiometricLockGateViewModel`**

Two changes from the current file:

1. Remove the `Action.PromptShown` branch from `perform()` (only `Action.Unlock` remains — the existing `Unlock` body is unchanged).
2. Replace the single `attach()` collector with two collectors:

```kotlin
override fun attach(): Closeable = Closeables.of {
    coroutineScope.launch {
        combine(
            biometricLockUseCase.enabled,
            biometricLockUseCase.lockState,
        ) { enabled, lockState ->
            enabled && lockState is LockState.Locked
        }.collect { isLocked ->
            mutableState.update { it.copy(isLocked = isLocked) }
        }
    }
    coroutineScope.launch {
        biometricLockUseCase.autoPromptRequests.collect {
            if (mutableState.value.isLocked) {
                mutableState.update { it.copy(promptToken = it.promptToken + 1) }
            }
        }
    }
}
```

`Action.PromptShown` no longer exists, so its branch must be removed from `perform()`.

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.security.DefaultBiometricLockGateViewModelTest" 2>&1 | tail -20
```
Expected: 3 tests pass.

- [ ] **Step 5: Build core**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL (ViewProvider will fail in Task 6 — that's the next file).

- [ ] **Step 6: Hold commit until Task 6 finishes**

The ViewProvider still references the old state field, so the build is still red. Don't commit yet.

---

## Task 6: Update `BiometricLockGateViewProvider` to key on promptToken

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/security/BiometricLockGateViewProvider.kt`

- [ ] **Step 1: Swap the LaunchedEffect**

Replace the `View()` function body:

```kotlin
@Composable
override fun View() {
    val state by viewModel.state.collectAsState(initial = BiometricLockGateViewModel.State())
    if (!state.isLocked) return

    LaunchedEffect(state.promptToken) {
        if (state.promptToken > 0) {
            viewModel.perform(BiometricLockGateViewModel.Action.Unlock)
        }
    }

    BiometricLockScreen(
        onUnlockClick = { viewModel.perform(BiometricLockGateViewModel.Action.Unlock) },
    )
}
```

The `> 0` guard prevents a never-locked gate from auto-prompting before any real foreground event arrives.

- [ ] **Step 2: Build everything**

```bash
./gradlew :app:compileDebugKotlin :zero-core:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit Tasks 4–6 together**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/security/BiometricLockGateViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/security/BiometricLockGateViewProvider.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModelTest.kt
git commit -m "feat(security): auto-prompt biometric unlock on foreground via promptToken"
git push
```

---

## Task 7: Verification

- [ ] **Step 1: Full unit test run**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: no failures.

- [ ] **Step 2: Lint**

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```
Expected: no new errors.

- [ ] **Step 3: UI verification on device**

Use `zero-project:android-ui-inspector` with the worktree's emulator (`emulator-5556`, per `.emulator-serial`).

Manual checks:
1. Enable biometric lock in Settings.
2. Press Home → switch to another app → return immediately. Expect: **no** lock screen.
3. Lock the app via Settings toggle off → on, then return. Expect: lock screen + BiometricPrompt auto-shown.
4. While on the lock screen, cancel the prompt → press Home → return. Expect: BiometricPrompt re-shown automatically.

Cold-start case is covered by unit tests (`onAppForegrounded with no prior background`). The 30-minute boundary is also covered by unit tests; not practical to verify on-device without temporarily lowering the constant.

---

## Self-Review

- **Spec coverage:** Inactivity timeout (Tasks 1–3), auto-prompt on foreground (Tasks 1, 4–6), tests for both (Tasks 2, 5), UI verification (Task 7). All spec sections addressed.
- **Placeholders:** None.
- **Type consistency:** `promptToken: Int`, `autoPromptRequests: Flow<Unit>`, `onAppBackgrounded()`/`onAppForegrounded()` — names match across interface, default impl, observer, ViewModel, ViewProvider, and tests.
- **Action removed:** `PromptShown` action is gone from interface, default impl, tests — no dangling references.
