# Feedback Phase 2 — Shake-to-Feedback UI + On-Device Capture

**Date:** 2026-05-12
**Issue:** #81 — `feat: shake-to-feedback screen with log collection and GitHub issue reporting`
**Scope:** Phase 2 (UI + on-device capture). Phase 1 (the abuse-protected `FeedbackService` pipe) shipped in [feedback-infra-design.md](./2026-05-12-feedback-infra-design.md).

## Goal

Let internal-testing users open a feedback bottom sheet by shaking the device, fill in a short description, and submit a report. The report is auto-enriched with device info, recent navigation history, and a manually-curated breadcrumb log, then sent through the existing `FeedbackService` pipe.

## Non-Goals

- Server-side rate limiting (deferred — Phase 1's distribution gate + Play Integrity is the bound).
- Promoting the app to Play Production.
- Capturing arbitrary `Timber` logs (rejected — PII risk; only an explicit `Breadcrumbs.log(...)` API).
- Persisting breadcrumb/nav rings across process death (in-memory only).

---

## 1. Public Surface Additions

### `zero-api/feedback/Breadcrumbs.kt`

```kotlin
interface Breadcrumbs {

    fun log(message: String)

    fun snapshot(): Snapshot

    data class Snapshot(
        val navigation: List<Entry>,
        val breadcrumbs: List<Entry>,
    )

    data class Entry(val timestamp: Instant, val message: String)
}
```

One interface, two rings inside the implementation. Rationale:

- `log(message)` writes to the **breadcrumb** ring. Callers must compose PII-safe messages by hand — there is no tag/level system. This is intentional: every call site is a decision about what's safe to attach to a public GitHub issue.
- The **navigation** ring is auto-populated by the implementation subscribing to `Navigator.state` — `log()` does not write to it.
- `snapshot()` returns both rings separately so the report renders them in distinct sections.
- `Entry.message` for navigation entries is the **templated route** (`transactions/{transactionId}`) — never the resolved URL with argument values. PII bound by construction.

### `zero-api/feedback/DeviceInfo.kt`

```kotlin
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,    // Build.VERSION.RELEASE
    val sdkInt: Int,
    val versionName: String,  // BuildConfig.VERSION_NAME
    val versionCode: Long,    // BuildConfig.VERSION_CODE
)
```

Pure data. Built once at app startup, passed via `@BindsInstance`. Lives in `zero-api` so `zero-core` can format it without seeing `BuildConfig`.

---

## 2. App-Side Wiring

New files in `app/src/main/java/com/hluhovskyi/zero/feedback/`:

- **`InMemoryBreadcrumbs`** — the only `Breadcrumbs` impl. Two `ArrayDeque<Entry>` rings (`navigation` cap 50, `breadcrumbs` cap 200) guarded by a single `Mutex`. Reads return defensive copies. Constructor takes a `Navigator` (or a `Flow<Navigator.State>`), a `Clock`, and a `CoroutineScope`. On `attach()` it launches a coroutine that maps `navigator.state.distinctUntilChanged { it.destination }` to `Entry(clock.now(), state.destination.route)` and appends to the navigation ring. `distinctUntilChanged` correctly admits back-navigation (different destination than previous) but rejects config-change re-emits.
- **`ShakeDetector`** — Android-only utility. Constructor: `SensorManager`, `onShake: () -> Unit`. `start()` registers a `SensorEventListener` on `Sensor.TYPE_ACCELEROMETER` with `SENSOR_DELAY_GAME`. Trigger condition: linear acceleration magnitude `sqrt(x² + y² + z²) − g > 13 m/s²` for 3+ consecutive samples within 500 ms. Internal 1.5-second debounce after each fire. `stop()` unregisters.
- **`ShakeFeedbackEntry`** — small `Closeable` glue. On `start()`, instantiates a `ShakeDetector` whose `onShake` is `navigator.navigateTo(Destinations.Feedback)`. On `close()`, stops the detector. Started from `MainActivityScreenComponent.attach()` — that method currently returns `Closeables.empty()`; the override returns `Closeables.combine(breadcrumbs.attach(), shakeFeedbackEntry.start())` so both the breadcrumb-history collector and the shake listener share the screen's lifecycle.
- **`FeedbackNavigationEntry`** — `@IntoSet` provider in `MainActivityScreenComponent.Module`. Registers `Destinations.Feedback` as a `BottomSheet` and builds the `FeedbackComponent` from `zero-core`, injecting `feedbackService`, `breadcrumbs`, `deviceInfo`, and `isDebugBuild = BuildConfig.DEBUG`. Provides an `OnFeedbackSubmittedHandler` that calls `navigator.back()`.

Changes to existing files:

- **`Destinations.kt`** — add `object Feedback : Destination by destinationOf("feedback")`. No arguments.
- **`MainActivityScreenComponent.kt`** — provide `InMemoryBreadcrumbs` as `@MainActivityScreenScope` (the scope where `Navigator` is bound); start `ShakeFeedbackEntry` from the component's `attach()`.
- **`ApplicationComponent.kt`** — provide `DeviceInfo` as `@ApplicationScope` from `Build.*` + `BuildConfig.VERSION_NAME` + `BuildConfig.VERSION_CODE`; expose through `ActivityComponent.Dependencies`.

Module placement: all activity / sensor / nav-controller coupling stays in `app`; the UI feature is in `zero-core`. This matches the existing `accounts/`, `transactions/` etc. split.

---

## 3. Feedback Bottom Sheet (`zero-core/feedback/`)

Same `FeatureComponent → FeatureViewModel → FeatureViewProvider` shape as `ColorPickerComponent`. Five files.

### Component

`FeedbackComponent : AttachableViewComponent`. Dependencies: `feedbackService`, `breadcrumbs`, `deviceInfo`, `clock`. Builder receives `@BindsInstance onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler` (a `fun interface`) and `@BindsInstance isDebugBuild: Boolean`.

### ViewModel

```kotlin
sealed interface Action {
    data class UpdateDescription(val text: String) : Action
    object Submit : Action
}

data class State(
    val description: String = "",
    val isSubmitting: Boolean = false,
    val deviceInfoPreview: String = "",
    val errorMessage: String? = null,
)
```

`DefaultFeedbackViewModel` behaviour on `Submit`:

1. If description blank → no-op (UI already disables the button).
2. Set `isSubmitting = true`, clear `errorMessage`.
3. Build a `FeedbackReport` via `FeedbackReportFormatter` (§4).
4. Call `feedbackService.submit(report)` on `Dispatchers.IO`.
5. On `Success` → invoke `onFeedbackSubmittedHandler.onFeedbackSubmitted()` (the activity-side entry dismisses the sheet).
6. On `Failure` → set `errorMessage` to a localised "Couldn't send. Try again." string. Description is preserved.

`UpdateDescription` always clears `errorMessage` (typing again resets the error).

### ViewProvider

Compose layout, plain Material 2 components matching the rest of the codebase:

- `Column` inside the bottom-sheet body (`MainActivityScreenViewProvider` already supplies the drag handle and `imePadding`).
- Headline `Text` (R.string).
- `OutlinedTextField` for description, `minLines = 4`, `maxLines = 8`.
- Read-only device-info preview row using `state.deviceInfoPreview` (e.g. `"Pixel 8 · Android 14 (34) · 1.4.2 (142)"`) in the muted theme colour. **No** raw breadcrumb/nav dump in the UI — those are only attached to the submitted body.
- Inline `Text` in error colour when `state.errorMessage != null`, rendered directly under the description field.
- `Button` "Submit" — disabled when `description.isBlank() || isSubmitting`; shows a `CircularProgressIndicator` when `isSubmitting`.

### Handler

`OnFeedbackSubmittedHandler` — `fun interface` invoked once on `Success`. The app-side navigation entry's implementation calls `navigator.back()`.

---

## 4. Report Assembly

`FeedbackReportFormatter` — internal in `zero-core/feedback/`. Pure function, unit-tested in isolation.

```kotlin
internal class FeedbackReportFormatter(
    private val deviceInfo: DeviceInfo,
    private val isDebugBuild: Boolean,
    private val clock: Clock,
) {
    fun format(description: String, snapshot: Breadcrumbs.Snapshot): FeedbackReport
}
```

### Title

First non-blank line of `description`, trimmed to 80 chars. Newlines and backticks stripped to keep the GitHub-issue title on one line. Fallback when description is blank: `"Feedback from ${manufacturer} ${model}"`.

### Body (Markdown)

```
## Description

```` <-- dynamic-length fence (see "Injection guard")
<user's description, verbatim>
````

## Device

- Device: <model> (<manufacturer>)
- OS: Android <osVersion> (SDK <sdkInt>)
- App: <versionName> (<versionCode>)

<details>
<summary>Diagnostics</summary>

### Navigation

- <HH:mm:ss.SSS>  <route>
- ...

### Breadcrumbs

- <HH:mm:ss.SSS>  <message>
- ...

</details>
```

Notes:

- The `<details>` block collapses by default on GitHub. The Diagnostics block is omitted entirely when both nav and breadcrumb rings are empty. Individual sub-sections (Navigation / Breadcrumbs) are omitted when their ring is empty.
- Timestamps are local time `HH:mm:ss.SSS`. The formatter takes `Clock` so unit tests are deterministic.

### Injection guard

Defense against Markdown injection via the description:

1. Compute `n = longestBacktickRunInDescription`.
2. Use a fence of length `max(3, n + 1)`.
3. Inside the fenced block GitHub Markdown does **not** process `@mentions`, `#references`, HTML, or any formatting. This neutralises spam, autolink abuse, and HTML/script.

Device strings (`Build.MANUFACTURER`, `Build.MODEL`) are technically attacker-controlled on rooted devices; the formatter ASCII-sanitises them by replacing any character outside `[A-Za-z0-9 ._-]` with `?`. Navigation routes are templated literals from `Destinations.kt` and need no escaping. Breadcrumb messages come from developer code under the PII-safe convention and pass through unmodified.

### Labels

`listOf("feedback")` always; `+ "debug"` when `isDebugBuild`. The baseline `feedback` label lets you filter issues by origin without query expression hacks.

### Wiring

`isDebugBuild` arrives via `@BindsInstance` on `FeedbackComponent.Builder` (sourced from `BuildConfig.DEBUG` in `FeedbackNavigationEntry`). This keeps `zero-core` free of any `BuildConfig` reference, preserving the module's pure-Kotlin posture.

---

## 5. PII Discipline

- `Breadcrumbs.log(message)` has no tag/level — every call site is an explicit decision about what's safe to attach to a public GitHub issue. Reviewers must reject `log()` calls that include user-typed strings, IDs, currency amounts, account names, or anything else that could identify a person or leak financial detail.
- Navigation entries store the **templated route**, not the resolved URL — IDs never appear.
- Device-info strings are ASCII-sanitised before submission.
- The user's free-text description is the only path for PII to leave the device, and it's explicit (the user typed it).
- The `<details>` collapse keeps diagnostics out of the default GitHub issue view so reviewers see the description first.

---

## 6. Verification

### Unit tests

- **`InMemoryBreadcrumbsTest`** (`app/src/test`):
  1. `log()` appends to the breadcrumbs ring; `snapshot().breadcrumbs` returns entries in order.
  2. Exceeding 200 entries evicts oldest (FIFO).
  3. Navigator state flow drives `snapshot().navigation`; consecutive identical destinations are deduplicated; back-navigation (different destination) produces a fresh entry.
  4. Concurrent writes from two coroutines don't lose entries.
- **`FeedbackReportFormatterTest`** (`zero-core/src/test`):
  1. Title: first non-blank line, truncated at 80 chars; fallback when description blank.
  2. Body sections omitted when their inputs are empty.
  3. Injection guard: description containing ```` ``` ```` produces 4-backtick fence; ````` ```` ````` produces 5-backtick fence.
  4. Device-info sanitiser: `MANUFACTURER = "[evil](http://x)"` → `?evil??http???x?`.
  5. Labels: `feedback` always; `+ debug` when `isDebugBuild = true`.
  6. `<details>` block omitted when both rings empty.
- **`DefaultFeedbackViewModelTest`** (`zero-core/src/test`):
  1. `UpdateDescription` flows to state and clears any prior `errorMessage`.
  2. `Submit` while blank — no `feedbackService.submit` call.
  3. Happy path: `Success` → handler invoked; `isSubmitting` cleared; no error.
  4. Failure path: `Failure` → `errorMessage` populated; description preserved; handler not invoked.

No unit test for `ShakeDetector` (hardware integration, low marginal value).

### Manual / inspector

1. Debug build on emulator → trigger shake via `adb emu sensor set acceleration 0:0:20` → feedback bottom sheet opens.
2. UI inspector after shake: verify bounds, drag-handle visibility, error-text colour on a simulated failure (e.g., point endpoint to localhost to force `Failure`).
3. End-to-end on a registered Play Integrity test device with the real endpoint: submit → verify GitHub issue lands, `debug` and `feedback` labels present, Diagnostics block collapsed in the rendered issue.

### Lint

`./gradlew :app:lintDebug` clean. No new lint rule needed in this phase — `RemoteComponentEncapsulationDetector` from Phase 1 still guards the module boundary.

---

## 7. Out of Scope

- Server-side rate limiting.
- Promotion to Play Production track.
- Persisting breadcrumbs across process death (in-memory only by design).
- Auto-capturing arbitrary `Timber` lines into the breadcrumb ring.
- A dev-menu / settings UI to tune shake sensitivity.
- Attaching screenshots or logcat to the report.
