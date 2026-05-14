# Biometric Lock — Settings Toggle + MainActivity Gate

Branch: `worktree-biometric-lock-navigation`

## Goal

Add a Security section toggle in Settings to enable/disable a biometric lock. When enabled,
MainActivity content is gated behind a full-screen lock overlay on every cold start and every
return-from-background. The user unlocks with the system BiometricPrompt (fingerprint / Face Unlock
/ device credential). Until unlock succeeds, the lock overlay covers the nav graph and blocks all
interaction.

Out of scope: encrypting the Room DB at rest (tracked separately as a follow-up issue).

## Architecture

| Type | Module | Responsibility |
|------|--------|----------------|
| `BiometricConfigurationKey.Enabled` | zero-core/security | `ScopedConfigurationKey<Boolean>`, scope `"security"`, default `false`. |
| `BiometricLockUseCase` | zero-api/security | `enabled: Flow<Boolean>`, `suspend fun setEnabled(value)`, `lockState: StateFlow<LockState>`, `fun lock()`, `fun unlock()`. `LockState = Locked | Unlocked`. Has `Noop` impl. |
| `DefaultBiometricLockUseCase` | zero-core/security | Reads/writes setting via `ConfigurationRepository`. Manages `MutableStateFlow<LockState>` (starts `Unlocked`; `lock()` only flips to `Locked` when `enabled` is currently true). |
| `BiometricAuthenticator` | zero-api/security | `suspend fun authenticate(reason: AuthReason): Result` where `AuthReason = Unlock | EnableLock | DisableLock` and `Result = Success | Failure | Unavailable`. `Noop` returns `Unavailable`. |
| `AndroidBiometricAuthenticator` | app/security | Wraps `androidx.biometric.BiometricPrompt`. Holds a `WeakReference<FragmentActivity>` set per-activity-build. Allows both biometric and device-credential authenticators. |
| `BiometricLockGateComponent` | zero-core/security | Wraps activity content. Builder takes `content: @Composable () -> Unit` as `@BindsInstance`. ViewModel exposes `state: Flow<State(isLocked, canPromptOnLaunch)>` and actions `Unlock`, `PromptShown`. |

The setting is per-user (every entry in `ConfigurationRepository` is scoped to `currentUserId`),
which matches every existing config (currency, presets, etc.). Reads work without a current user
(returns default `false`); writes require a current user, which is guaranteed by the time the
user reaches Settings.

### Lock lifecycle

- App cold start → `BiometricLockUseCase` collects `enabled`; on the first emission, if `enabled`
  is true, calls `lock()`.
- Activity `onStop` → if `enabled`, calls `lock()` (so returning to foreground requires re-auth).
- `unlock()` is called only from `BiometricLockGateViewModel` after `BiometricAuthenticator`
  returns `Success`.
- The gate composable auto-launches the prompt on first composition when `isLocked` and
  `canPromptOnLaunch` are both true; after a prompt is shown, `PromptShown` sets the flag false
  so the user can retry manually.

### Settings toggle behavior

- Tapping the row when `enabled = false`:
  1. ViewModel dispatches `BiometricAuthenticator.authenticate(EnableLock)`.
  2. On `Success` → `setEnabled(true)`.
  3. On `Failure` / `Unavailable` → show a snackbar; setting stays `false`.
- Tapping the row when `enabled = true`:
  1. Dispatches `authenticate(DisableLock)`.
  2. On `Success` → `setEnabled(false)`.
  3. On `Failure` → snackbar; setting stays `true`.
- Both transitions require auth so a stolen unlocked device can't trivially disable the lock.

## Files

### New — `zero-api/src/main/java/com/hluhovskyi/zero/security/`
- `BiometricLockUseCase.kt` — interface + `Noop`
- `BiometricAuthenticator.kt` — interface (`AuthReason`, `Result` sealed types) + `Noop`

### New — `zero-core/src/main/java/com/hluhovskyi/zero/security/`
- `BiometricConfigurationKey.kt` — `ScopedConfigurationKey<Boolean>` for scope `"security"`,
  key `"biometric_lock_enabled"`, default `false`.
- `DefaultBiometricLockUseCase.kt` — reads/writes config, holds in-memory lockState.
- `BiometricLockGateComponent.kt` — Component scaffolding (Dagger). See [DI](docs/agents/dependency-injection.md).
- `BiometricLockGateViewModel.kt` + `DefaultBiometricLockGateViewModel.kt`
- `BiometricLockGateViewProvider.kt` — composable that renders `content()` and, when `state.isLocked`,
  overlays a full-screen `BiometricLockScreen`. Auto-launches `viewModel.perform(Unlock)` once on
  first locked composition.
- `BiometricLockScreen.kt` (private) — centered icon (`Icons.Outlined.Fingerprint`), title
  "Zero is locked", subtitle "Use biometrics to unlock", and an "Unlock" button. Uses theme colors
  consistent with the rest of the app.

### New — `app/src/main/java/com/hluhovskyi/zero/security/`
- `AndroidBiometricAuthenticator.kt` — `androidx.biometric.BiometricPrompt`-backed implementation.
  Holds a nullable `currentActivity: FragmentActivity?` set/cleared by MainActivity. Builds
  `PromptInfo` with title/subtitle text from `R.string.biometric_*` resources.
  Uses `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` so user can fall back to device PIN/pattern.

### Modified — app
- `app/build.gradle` — add `implementation 'androidx.biometric:biometric:1.1.0'`.
- `MainActivity.kt` — extend `FragmentActivity` (was `ComponentActivity`). On `onCreate`:
  register the activity with the `AndroidBiometricAuthenticator`. On `onStop` and `onDestroy`:
  unregister + call `biometricLockUseCase.lock()` if `onStop` and enabled.
- `MainActivityViewProvider.kt` — wrap the existing screen-component content with
  `BiometricLockGateComponent.Builder().content(...).AttachWithView()`.
- `ActivityComponent.kt` — add `BiometricLockUseCase`, `BiometricAuthenticator`,
  `BiometricLockGateComponent.Dependencies` to the parent type list. Add `@Provides` for
  `biometricLockGateComponentBuilder`. Add `BiometricLockUseCase` and `BiometricAuthenticator`
  to the `Dependencies` interface (so they come from ApplicationComponent).
- `ApplicationComponent.kt` — provide `BiometricLockUseCase` (`DefaultBiometricLockUseCase`,
  scoped) and `BiometricAuthenticator` (`AndroidBiometricAuthenticator`, scoped). Add both
  to the dependencies exposed via the abstract class (so `ActivityComponent.Dependencies` is
  satisfied).

### Modified — zero-core
- `SettingsComponent.kt` — add `BiometricLockUseCase` and `BiometricAuthenticator` to
  `Dependencies`. Pass both into `DefaultSettingsViewModel`. Add `R.string` snackbar messages.
- `SettingsViewModel.kt` — extend `State` with `biometricLockEnabled: Boolean` and
  `biometricFeedback: BiometricFeedback?`. Add `Action.ToggleBiometricLock`. Add
  `sealed interface BiometricFeedback { Unavailable; AuthFailed }`.
- `DefaultSettingsViewModel.kt` — collect `biometricLockUseCase.enabled` into state; handle
  `ToggleBiometricLock` by calling `authenticator.authenticate()` (reason picked from current
  state) and writing back via `setEnabled` on success.
- `SettingsViewProvider.kt` — replace the placeholder Security MoreRow with a new
  `MoreToggleRow` (trailing `Switch`, no chevron). Bind to `state.biometricLockEnabled`. Show
  snackbars for `BiometricFeedback.Unavailable` and `AuthFailed`. Subtitle updates between
  enabled/disabled copies.

### Modified — strings (`zero-core/src/main/res/values/strings.xml`)
- Update `settings_biometric_lock_description` copies for on/off states:
  - `settings_biometric_lock_description_enabled` = "Unlock required on open"
  - `settings_biometric_lock_description_disabled` = "Face ID or Fingerprint required on open"
- New strings: `settings_biometric_unavailable`, `settings_biometric_auth_failed`,
  `biometric_prompt_title_unlock`, `biometric_prompt_title_enable`,
  `biometric_prompt_title_disable`, `biometric_prompt_subtitle_*`, `biometric_lock_screen_title`,
  `biometric_lock_screen_subtitle`, `biometric_lock_screen_action`.

## Tasks (execution order)

1. **Add `androidx.biometric` dep + extend FragmentActivity** — bump `app/build.gradle`,
   change `MainActivity` superclass, verify `:app:assembleDebug` compiles.
2. **Define API surfaces in `zero-api/security/`** — `BiometricLockUseCase`,
   `BiometricAuthenticator` (with `Noop`s).
3. **Implement `DefaultBiometricLockUseCase` + `BiometricConfigurationKey` in zero-core** —
   plus a JUnit test that toggles `setEnabled` and observes `enabled.first()`.
4. **Implement `AndroidBiometricAuthenticator` in app/security** — register/unregister activity,
   surface result via suspend callback (`suspendCoroutine`). No prompt is triggered without an
   activity; returns `Result.Unavailable` then.
5. **Build `BiometricLockGateComponent` + ViewModel + ViewProvider in zero-core/security** —
   wire scaffold per scaffold-feature; ViewModel collects `lockState` and `enabled` to compute
   `State(isLocked, canPromptOnLaunch)`. Add JUnit test for ViewModel state composition.
6. **Wire Settings toggle** — extend `SettingsViewModel.State`, add `ToggleBiometricLock`,
   collect `enabled` flow in `attach()`, handle action via authenticator. Add `MoreToggleRow`
   composable in `SettingsViewProvider`. Update strings.
7. **Wire MainActivity** — register activity with authenticator in onCreate, lock on onStop,
   wrap content with `BiometricLockGateComponent`. Hook `ApplicationComponent` providers and
   `ActivityComponent.Module` providers.
8. **MainActivityViewProvider wraps content with gate** — verify the existing nav graph is the
   inner content slot.
9. **Run verification** — see Verification section.

## Verification

- `./gradlew :app:lintDebug :zero-core:lintDebug` — no new errors.
- `./gradlew testDebugUnitTest` — `DefaultBiometricLockUseCaseTest` and any updated
  `DefaultSettingsViewModelTest` pass.
- `./gradlew :app:assembleDebug` — compiles.
- `zero-project:android-ui-inspector`:
  - Settings → Security row shows a toggle. Toggling triggers BiometricPrompt (test on emulator
    with biometric enrolled, or fall back to device PIN).
  - With biometric enabled: cold start shows lock overlay; backgrounding + foregrounding shows
    lock overlay; tapping bottom-bar items behind the overlay does nothing.
  - With biometric disabled: app behaves as before; lock overlay never shown.
- Manual emulator setup: `adb -e emu finger touch 1` once biometrics are enrolled; if none are
  enrolled, the prompt falls back to device credential (PIN/pattern) thanks to
  `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
