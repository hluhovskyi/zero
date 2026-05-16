# Biometric Inactivity Timeout & Auto-Prompt — Design

## Problem

Two issues with the biometric app-lock today:

1. **Locks on every background.** `BiometricLockLifecycleObserver.onStop` calls
   `biometricLockUseCase.lock()` unconditionally. Switching to another app for two seconds
   forces a re-auth on return. Users expect an inactivity window (~20–30 min) before
   re-locking.
2. **No auto-prompt on return.** When the app is locked and the user foregrounds it, the
   BiometricPrompt does not appear automatically — they must tap the unlock button. This
   regression happens because `BiometricLockGateViewModel.State.canPromptOnLaunch` flips to
   `false` after the first prompt and never resets until a successful unlock.

## Goal

- Lock on cold start (unchanged).
- After foregrounding, lock only if ≥ **30 minutes** elapsed since the last backgrounding.
- Whenever the app is foregrounded while locked, auto-trigger the system biometric prompt.

Non-goals: configurable timeout UI, persisted `lastBackgroundedAt` across process death,
PIN fallback changes.

## Architecture

Three units change, each with a single purpose:

- **`BiometricLockUseCase`** — owns lock state and timeout policy. Gains
  `onAppBackgrounded()`, `onAppForegrounded()`, and a `Flow<Unit>` of auto-prompt requests.
- **`BiometricLockLifecycleObserver`** — translates Android lifecycle into use-case
  calls. No timeout logic of its own.
- **`BiometricLockGateViewModel`** — surfaces a `promptToken: Int` counter to the
  ViewProvider; `LaunchedEffect(promptToken)` re-fires the prompt on each new token.

The use case stays the single source of truth for "is the app locked, and should the gate
prompt now?". The observer is a thin lifecycle adapter. The gate ViewModel exposes a
deterministic counter the Compose layer can key on.

## Components

### `BiometricLockUseCase` (zero-api)

New interface members:

```kotlin
fun onAppBackgrounded()
fun onAppForegrounded()
val autoPromptRequests: Flow<Unit>
```

Existing `lock()` / `unlock()` stay (still called by `setEnabled(false)` and the gate
view-model on success). `Noop` returns `emptyFlow()` and no-ops the new methods.

### `DefaultBiometricLockUseCase` (app module)

State:
- `lastBackgroundedAt: Instant?` — in-memory. Null on cold start.
- `mutableAutoPromptRequests: MutableSharedFlow<Unit>` — `extraBufferCapacity = 1`,
  `onBufferOverflow = DROP_OLDEST`. Replay = 0 (we only care about new foregrounds).

Behavior:
- `onAppBackgrounded()` → record `clock.now()` to `lastBackgroundedAt`.
- `onAppForegrounded()`:
  1. If `lastBackgroundedAt == null` *or* `(now − lastBackgroundedAt) ≥ INACTIVITY_TIMEOUT`,
     call internal `lock()` (sets `lockState = Locked`).
  2. If currently `Locked`, `tryEmit(Unit)` to `mutableAutoPromptRequests`.

The `lastBackgroundedAt == null` branch covers cold start: no recorded background = treat
as expired = lock. Same effect as the current `onCreate { lock() }` line, which is
removed.

`INACTIVITY_TIMEOUT = 30.minutes` as a `Duration` constant in the file.

Dependencies: add `clock: Clock` (parameter); already available in `ActivityComponent`.

### `BiometricLockLifecycleObserver` (app module)

```kotlin
override fun onStart(owner: LifecycleOwner)  { biometricLockUseCase.onAppForegrounded() }
override fun onStop(owner: LifecycleOwner)   {
    if (!activity.isFinishing) biometricLockUseCase.onAppBackgrounded()
}
```

`onCreate` override removed — its work moves to `onStart`'s timeout check. (Cold start
fires `onCreate → onStart` synchronously before the first frame, so user behavior is
identical.)

### `BiometricLockGateViewModel` (zero-core)

State change:

```kotlin
data class State(
    val isLocked: Boolean = false,
    val promptToken: Int = 0,
)
```

`canPromptOnLaunch` and `Action.PromptShown` are removed. The token strictly increases;
LaunchedEffect's value-keying handles "fire once per request" without manual reset.

`attach()` launches two collectors on `coroutineScope`:
1. The existing `combine(enabled, lockState)` → updates `isLocked`.
2. New: `biometricLockUseCase.autoPromptRequests.collect { … }` → if currently locked,
   `mutableState.update { it.copy(promptToken = it.promptToken + 1) }`.

`Action.Unlock` is unchanged.

### `BiometricLockGateViewProvider` (zero-core)

```kotlin
LaunchedEffect(state.promptToken) {
    if (state.promptToken > 0) {
        viewModel.perform(Action.Unlock)
    }
}
```

The `> 0` guard skips the initial `0` so a never-locked gate doesn't auto-prompt.

### Wiring

`BiometricLockComponent.Dependencies` gains `val clock: Clock`. `ActivityComponent`
already exposes one and inherits `BiometricLockComponent.Dependencies`, so no other call
site changes.

## Data flow

Cold start, lock enabled:
```
onCreate → onStart → useCase.onAppForegrounded()
  → lastBackgroundedAt is null → lock()
  → lockState = Locked → combine emits isLocked=true
  → emits autoPromptRequest → gate VM bumps promptToken to 1
  → ViewProvider composes lock screen
  → LaunchedEffect(1) fires → Action.Unlock → BiometricPrompt shows
```

Quick background round-trip (≤ 30 min):
```
onStop → useCase.onAppBackgrounded() (records timestamp)
… user in another app …
onStart → useCase.onAppForegrounded()
  → elapsed < 30 min → no lock() call → lockState stays Unlocked
  → currently Unlocked → no autoPromptRequest emitted
  → state unchanged; no UI flicker
```

Long background round-trip (> 30 min):
```
onStop → onAppBackgrounded()
… 30+ min later …
onStart → onAppForegrounded() → elapsed ≥ 30 min → lock()
  → lockState = Locked → autoPromptRequest emitted
  → promptToken bumped → BiometricPrompt shows
```

Locked, prompt cancelled, then re-foregrounded (covers Problem 2):
```
[user cancels prompt — Result.Failure, lockState stays Locked]
… user backgrounds, returns within 30 min …
onStart → onAppForegrounded() → elapsed < 30 min, no extra lock()
  → currently Locked → autoPromptRequest emitted
  → gate VM bumps promptToken → LaunchedEffect re-fires → prompt shows
```

## Error handling

- `tryEmit` on a buffered SharedFlow can drop if the gate VM is not collecting yet —
  acceptable because `attach()` starts the collector before the activity reaches RESUMED,
  and a missed event during a cold race is harmless (the initial `combine` emission still
  fires the prompt via the `lock()` → state-update path).
- `Result.Failure` from the authenticator stays a no-op; user re-enters via the visible
  unlock button if they cancelled, or via the next foreground event.

## Testing

Unit tests, JUnit + coroutines `runTest`, fixed `Clock`:

**`DefaultBiometricLockUseCaseTest`** (new file, app module):
- `onAppForegrounded` with no prior background → locks, emits prompt request.
- `onAppBackgrounded → onAppForegrounded` after 29:59 → does not lock, but still emits
  prompt request when already-locked (regression case for Problem 2).
- `onAppBackgrounded → onAppForegrounded` after exactly 30:00 → locks.
- `onAppForegrounded` while `Unlocked` and within window → no lock, no emit.

**`DefaultBiometricLockGateViewModelTest`** (new file, zero-core):
- Two `autoPromptRequests` emissions while locked → `promptToken` reaches 2.
- `autoPromptRequest` while `lockState = Unlocked` → token stays 0.

UI verification via `zero-project:android-ui-inspector`:
- Enable biometric lock in Settings.
- Background app → return immediately → no lock screen.
- Background app → wait → simulate timeout (we'll temporarily lower the constant for the
  manual check or use `adb shell am broadcast` to drive a state change) → return → lock
  screen appears + prompt auto-shows.

## Affected files

```
zero-api/src/main/java/com/hluhovskyi/zero/security/BiometricLockUseCase.kt
app/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCase.kt
app/src/main/java/com/hluhovskyi/zero/security/BiometricLockComponent.kt
app/src/main/java/com/hluhovskyi/zero/security/BiometricLockLifecycleObserver.kt
zero-core/src/main/java/com/hluhovskyi/zero/security/BiometricLockGateViewModel.kt
zero-core/src/main/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModel.kt
zero-core/src/main/java/com/hluhovskyi/zero/security/BiometricLockGateViewProvider.kt
app/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockUseCaseTest.kt        (new)
zero-core/src/test/java/com/hluhovskyi/zero/security/DefaultBiometricLockGateViewModelTest.kt (new)
```

Roughly ~150 LOC of production code change + tests.
