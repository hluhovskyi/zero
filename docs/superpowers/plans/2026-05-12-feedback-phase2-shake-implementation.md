# Feedback Phase 2 (Shake-to-Feedback) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire shake-detection, in-memory breadcrumbs, a navigation-history ring, and a feedback bottom sheet that calls `FeedbackService.submit(report)` per the Phase 1 pipe.

**Architecture:** New `Breadcrumbs` + `DeviceInfo` interfaces in `zero-api`; `InMemoryBreadcrumbs` + `ShakeDetector` + activity wiring in `app/feedback/`; bottom-sheet feature (`FeedbackComponent → FeedbackViewModel → FeedbackViewProvider`) in `zero-core/feedback/`; report-body assembly + injection guard in `FeedbackReportFormatter`; new `BreadcrumbsLiteralOnly` lint rule.

**Approved spec:** [docs/superpowers/specs/2026-05-12-feedback-phase2-shake-design.md](../specs/2026-05-12-feedback-phase2-shake-design.md). Refer to it for any open architectural question — do not reinvent.

**Reference docs:** [Architecture Patterns](../../agents/architecture.md), [Dependency Injection](../../agents/dependency-injection.md), [Navigation](../../agents/navigation.md), [Code Style](../../agents/code-style.md).

---

## Task 1: zero-api types

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/feedback/Breadcrumbs.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/feedback/DeviceInfo.kt`

- [ ] **Step 1: Create `Breadcrumbs.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

import kotlinx.datetime.Instant

interface Breadcrumbs {

    fun log(message: String)

    fun snapshot(): Snapshot

    data class Snapshot(
        val navigation: List<Entry>,
        val breadcrumbs: List<Entry>,
    )

    data class Entry(
        val timestamp: Instant,
        val message: String,
    )
}
```

- [ ] **Step 2: Create `DeviceInfo.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val versionName: String,
    val versionCode: Long,
)
```

- [ ] **Step 3: Compile + commit**

```bash
./gradlew :zero-api:compileKotlin
git add zero-api/src/main/java/com/hluhovskyi/zero/feedback/
git commit -m "feat(zero-api): Breadcrumbs and DeviceInfo for feedback"
```

---

## Task 2: `InMemoryBreadcrumbs` (TDD)

**Files:**
- Create: `app/src/test/java/com/hluhovskyi/zero/feedback/InMemoryBreadcrumbsTest.kt`
- Create: `app/src/main/java/com/hluhovskyi/zero/feedback/InMemoryBreadcrumbs.kt`

- [ ] **Step 1: Test file**

Tests required (see spec §7 Verification):

1. `log()` appends; `snapshot().breadcrumbs` ordered.
2. >200 entries → FIFO eviction.
3. Navigator state flow drives `snapshot().navigation`; consecutive identical destinations dedupe; new destination produces entry.
4. Concurrent writes from two coroutines preserve every entry (count assertion).

Use `kotlinx.coroutines.test.runTest`, a fake `Clock` returning a counter, a `MutableSharedFlow<Navigator.State>` for nav input. Match existing test style; see `zero-core/src/test/java/com/hluhovskyi/zero/colors/DefaultColorPickerViewModelTest.kt` for the project's coroutine-test pattern.

- [ ] **Step 2: Run the test, expect FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests "*InMemoryBreadcrumbsTest"
```

Expected: compilation error (class not found).

- [ ] **Step 3: Implement `InMemoryBreadcrumbs.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import java.io.Closeable
import java.util.ArrayDeque

private const val NAV_CAPACITY = 50
private const val LOG_CAPACITY = 200

internal class InMemoryBreadcrumbs(
    private val navigator: Navigator,
    private val clock: Clock,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : Breadcrumbs {

    private val lock = Any()
    private val navigation = ArrayDeque<Breadcrumbs.Entry>(NAV_CAPACITY)
    private val breadcrumbs = ArrayDeque<Breadcrumbs.Entry>(LOG_CAPACITY)

    override fun log(message: String) {
        val entry = Breadcrumbs.Entry(clock.now(), message)
        synchronized(lock) {
            if (breadcrumbs.size == LOG_CAPACITY) breadcrumbs.removeFirst()
            breadcrumbs.addLast(entry)
        }
    }

    override fun snapshot(): Breadcrumbs.Snapshot = synchronized(lock) {
        Breadcrumbs.Snapshot(
            navigation = navigation.toList(),
            breadcrumbs = breadcrumbs.toList(),
        )
    }

    fun attach(): Closeable {
        val job: Job = navigator.state
            .map { it.destination.route }
            .distinctUntilChanged()
            .onEach { route ->
                val entry = Breadcrumbs.Entry(clock.now(), route)
                synchronized(lock) {
                    if (navigation.size == NAV_CAPACITY) navigation.removeFirst()
                    navigation.addLast(entry)
                }
            }
            .launchIn(scope)
        return Closeables.of {
            job.cancel()
            scope.cancel()
        }
    }
}
```

Verify `Clock.now()` returns `Instant`. If it returns something else, adapt: look at `zero-api/src/main/java/com/hluhovskyi/zero/common/time/Clock.kt`.

- [ ] **Step 4: Run tests, expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*InMemoryBreadcrumbsTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/feedback/InMemoryBreadcrumbs.kt
git add app/src/test/java/com/hluhovskyi/zero/feedback/InMemoryBreadcrumbsTest.kt
git commit -m "feat(app): InMemoryBreadcrumbs with nav-history ring"
```

---

## Task 3: `ShakeDetector`

**Files:**
- Create: `app/src/main/java/com/hluhovskyi/zero/feedback/ShakeDetector.kt`

No unit test (hardware integration). Manual verification in Task 10.

- [ ] **Step 1: Implementation**

```kotlin
package com.hluhovskyi.zero.feedback

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

private const val THRESHOLD_MS2 = 13f
private const val WINDOW_MS = 500L
private const val REQUIRED_SAMPLES = 3
private const val DEBOUNCE_MS = 1_500L

internal class ShakeDetector(
    private val sensorManager: SensorManager,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val gravity = SensorManager.GRAVITY_EARTH
    private val timestamps = ArrayDeque<Long>()
    private var lastFireMs: Long = 0L

    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        timestamps.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z) - gravity
        if (magnitude < THRESHOLD_MS2) return

        val now = System.currentTimeMillis()
        while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
            timestamps.removeFirst()
        }
        timestamps.addLast(now)

        if (timestamps.size >= REQUIRED_SAMPLES && now - lastFireMs > DEBOUNCE_MS) {
            lastFireMs = now
            timestamps.clear()
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :app:compileDebugKotlin
git add app/src/main/java/com/hluhovskyi/zero/feedback/ShakeDetector.kt
git commit -m "feat(app): accelerometer-based ShakeDetector"
```

---

## Task 4: zero-core feedback feature scaffold

**Files (create all under `zero-core/src/main/java/com/hluhovskyi/zero/feedback/`):**
- `OnFeedbackSubmittedHandler.kt`
- `FeedbackViewModel.kt`
- `DefaultFeedbackViewModel.kt`
- `FeedbackViewProvider.kt`
- `FeedbackComponent.kt`
- `FeedbackReportFormatter.kt`

Read [Architecture Patterns](../../agents/architecture.md) for the Component/ViewModel/ViewProvider shape. Mirror `ColorPickerComponent` (file naming, lazy ViewModel/ViewProvider, `attach()` returning ViewModel's closeable).

- [ ] **Step 1: `OnFeedbackSubmittedHandler.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

fun interface OnFeedbackSubmittedHandler {

    fun onFeedbackSubmitted()

    companion object {
        val Noop: OnFeedbackSubmittedHandler = OnFeedbackSubmittedHandler { }
    }
}
```

- [ ] **Step 2: `FeedbackViewModel.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.AttachableActionStateModel

interface FeedbackViewModel : AttachableActionStateModel<FeedbackViewModel.Action, FeedbackViewModel.State> {

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
}
```

- [ ] **Step 3: `DefaultFeedbackViewModel.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultFeedbackViewModel(
    private val feedbackService: FeedbackService,
    private val breadcrumbs: Breadcrumbs,
    private val reportFormatter: FeedbackReportFormatter,
    private val onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler,
    private val errorMessageProvider: () -> String,
    deviceInfoPreview: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : FeedbackViewModel {

    private val mutableState = MutableStateFlow(
        FeedbackViewModel.State(deviceInfoPreview = deviceInfoPreview),
    )
    override val state: Flow<FeedbackViewModel.State> = mutableState

    override fun perform(action: FeedbackViewModel.Action) {
        when (action) {
            is FeedbackViewModel.Action.UpdateDescription -> mutableState.update {
                it.copy(description = action.text, errorMessage = null)
            }
            FeedbackViewModel.Action.Submit -> submit()
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.description.isBlank() || current.isSubmitting) return
        mutableState.update { it.copy(isSubmitting = true, errorMessage = null) }
        coroutineScope.launch {
            val report = reportFormatter.format(current.description, breadcrumbs.snapshot())
            when (feedbackService.submit(report)) {
                is FeedbackSubmitResult.Success -> {
                    mutableState.update { it.copy(isSubmitting = false) }
                    onFeedbackSubmittedHandler.onFeedbackSubmitted()
                }
                FeedbackSubmitResult.Failure -> {
                    mutableState.update {
                        it.copy(isSubmitting = false, errorMessage = errorMessageProvider())
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of { coroutineScope.cancel() }
}
```

(The `errorMessageProvider` lambda lets the ViewProvider supply a localised `stringResource(R.string.feedback_error_generic)` lazily — keeping the ViewModel free of `Context`. Wire it via `FeedbackComponent.Builder`.)

- [ ] **Step 4: `FeedbackReportFormatter.kt`**

```kotlin
package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class FeedbackReportFormatter(
    private val deviceInfo: DeviceInfo,
    private val isDebugBuild: Boolean,
    private val clock: Clock,
) {

    fun format(description: String, snapshot: Breadcrumbs.Snapshot): FeedbackReport {
        val title = title(description)
        val body = body(description, snapshot)
        val labels = buildList {
            add("feedback")
            if (isDebugBuild) add("debug")
        }
        return FeedbackReport(title = title, body = body, labels = labels)
    }

    private fun title(description: String): String {
        val firstLine = description.lineSequence().firstOrNull { it.isNotBlank() }
            ?.replace("`", "")
            ?.trim()
        return if (firstLine.isNullOrBlank()) {
            "Feedback from ${sanitize(deviceInfo.manufacturer)} ${sanitize(deviceInfo.model)}".trim()
        } else {
            firstLine.take(80)
        }
    }

    private fun body(description: String, snapshot: Breadcrumbs.Snapshot): String = buildString {
        appendLine("## Description")
        appendLine()
        val fence = "`".repeat(maxOf(3, longestBacktickRun(description) + 1))
        appendLine(fence)
        appendLine(description)
        appendLine(fence)
        appendLine()
        appendLine("## Device")
        appendLine()
        appendLine("- Device: ${sanitize(deviceInfo.model)} (${sanitize(deviceInfo.manufacturer)})")
        appendLine("- OS: Android ${sanitize(deviceInfo.osVersion)} (SDK ${deviceInfo.sdkInt})")
        appendLine("- App: ${sanitize(deviceInfo.versionName)} (${deviceInfo.versionCode})")
        if (snapshot.navigation.isNotEmpty() || snapshot.breadcrumbs.isNotEmpty()) {
            appendLine()
            appendLine("<details>")
            appendLine("<summary>Diagnostics</summary>")
            appendLine()
            if (snapshot.navigation.isNotEmpty()) {
                appendLine("### Navigation")
                appendLine()
                snapshot.navigation.forEach { entry -> appendLine("- ${formatTimestamp(entry)}  ${entry.message}") }
                appendLine()
            }
            if (snapshot.breadcrumbs.isNotEmpty()) {
                appendLine("### Breadcrumbs")
                appendLine()
                snapshot.breadcrumbs.forEach { entry -> appendLine("- ${formatTimestamp(entry)}  ${entry.message}") }
                appendLine()
            }
            appendLine("</details>")
        }
    }

    private fun formatTimestamp(entry: Breadcrumbs.Entry): String {
        val dt = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val ms = entry.timestamp.toEpochMilliseconds() % 1000
        return "%02d:%02d:%02d.%03d".format(dt.hour, dt.minute, dt.second, ms)
    }

    private fun longestBacktickRun(text: String): Int {
        var max = 0
        var run = 0
        for (c in text) {
            if (c == '`') {
                run++
                if (run > max) max = run
            } else {
                run = 0
            }
        }
        return max
    }

    private fun sanitize(value: String): String = value.map { c ->
        if (c.isLetterOrDigit() || c == ' ' || c == '.' || c == '_' || c == '-') c else '?'
    }.joinToString("")
}
```

- [ ] **Step 5: `FeedbackViewProvider.kt`**

Compose layout. Read existing `ColorPickerViewProvider.kt` for code-style baseline (Material 2 imports, theme tokens from `com.hluhovskyi.zero.ui.theme`). Strings via `R.string` (added in Task 7).

```kotlin
package com.hluhovskyi.zero.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant

internal class FeedbackViewProvider(
    private val viewModel: FeedbackViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = FeedbackViewModel.State())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.feedback_title),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = OnSurface),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.perform(FeedbackViewModel.Action.UpdateDescription(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.feedback_description_hint)) },
                minLines = 4,
                maxLines = 8,
                enabled = !state.isSubmitting,
            )
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    style = TextStyle(fontSize = 12.sp, color = Error),
                )
            }
            Text(
                text = state.deviceInfoPreview,
                style = TextStyle(fontSize = 12.sp, color = OnSurfaceVariant),
            )
            Button(
                onClick = { viewModel.perform(FeedbackViewModel.Action.Submit) },
                enabled = state.description.isNotBlank() && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(stringResource(R.string.feedback_submit))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

If `Error` theme token doesn't exist under `com.hluhovskyi.zero.ui.theme`, fall back to `MaterialTheme.colors.error`.

- [ ] **Step 6: `FeedbackComponent.kt`**

Mirror `ColorPickerComponent.kt`. Builder receives: `feedbackService`, `breadcrumbs`, `deviceInfo`, `clock` as Dependencies, plus `@BindsInstance isDebugBuild: Boolean`, `@BindsInstance deviceInfoPreview: String`, `@BindsInstance errorMessageProvider: () -> String`, `@BindsInstance onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler`.

Implement without Dagger annotations for now — direct factory like `ColorPickerComponent`'s `Factory`/`create`. The factory takes a `Dependencies` interface that the activity component will implement. Keep `internal class DefaultFeedbackViewModel` lazy-initialised, identical structure to `ColorPickerComponent`.

- [ ] **Step 7: Compile + commit**

```bash
./gradlew :zero-core:compileDebugKotlin
git add zero-core/src/main/java/com/hluhovskyi/zero/feedback/
git commit -m "feat(zero-core): feedback feature (component, vm, view provider, formatter)"
```

---

## Task 5: `FeedbackReportFormatterTest`

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/feedback/FeedbackReportFormatterTest.kt`

- [ ] **Step 1: Test cases (one `@Test` per case, all spec §7 verification items)**

1. `format` with non-blank description → title = first 80 chars of first line.
2. Blank description → title = `"Feedback from <manufacturer> <model>"` (sanitised).
3. Description containing ` ``` ` produces 4-backtick fence; description with `` ```` `` produces 5-backtick.
4. Sanitiser: `manufacturer = "[evil](http://x)"` → body contains `?evil??http???x?`.
5. `isDebugBuild = true` → labels = `["feedback", "debug"]`; `false` → `["feedback"]`.
6. Empty snapshot (both rings empty) → body has no `<details>` block.
7. Only navigation populated → body has `<details>` with `### Navigation` and no `### Breadcrumbs`.

Use a `Clock` fake returning a fixed `Instant.fromEpochSeconds(1_700_000_000L, 0)` so timestamps are deterministic. Use `DeviceInfo` with simple values.

- [ ] **Step 2: Run, expect PASS**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*FeedbackReportFormatterTest"
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/test/java/com/hluhovskyi/zero/feedback/FeedbackReportFormatterTest.kt
git commit -m "test(zero-core): FeedbackReportFormatter (injection guard, sanitiser, labels, details block)"
```

---

## Task 6: `DefaultFeedbackViewModelTest`

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModelTest.kt`

- [ ] **Step 1: Test cases**

1. `UpdateDescription` flows into state; if state had an `errorMessage`, clear it.
2. `Submit` while blank — `feedbackService.submit` not called.
3. Success path: `feedbackService.submit` returns `Success("url")` → `isSubmitting` toggles `true → false`; `onFeedbackSubmittedHandler.onFeedbackSubmitted()` invoked once; no error message.
4. Failure path: `Failure` returned → `errorMessage` set; description preserved; handler not invoked; `isSubmitting` cleared.

Use a fake `FeedbackService` (returns scripted result), fake `Breadcrumbs` (empty snapshot is fine), `runTest` for coroutines. Follow `DefaultColorPickerViewModelTest.kt` style.

- [ ] **Step 2: Run, expect PASS** — `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultFeedbackViewModelTest"`

- [ ] **Step 3: Commit** — `git add ... && git commit -m "test(zero-core): DefaultFeedbackViewModel success/failure paths"`

---

## Task 7: App wiring (Destinations, ApplicationComponent, MainActivityScreenComponent)

**Files modified:**
- `app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt`
- `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`
- `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`
- `app/src/main/res/values/strings.xml`

**Files created:**
- `app/src/main/java/com/hluhovskyi/zero/feedback/ShakeFeedbackEntry.kt`

Follow [Navigation](../../agents/navigation.md) for nav-entry registration, [Dependency Injection](../../agents/dependency-injection.md) for wiring style.

- [ ] **Step 1: `Destinations.kt`** — add at the bottom, alongside `Settings` / `Import`:

```kotlin
object Feedback : Destination by destinationOf("feedback")
```

- [ ] **Step 2: `strings.xml`** — add:

```xml
<string name="feedback_title">Send feedback</string>
<string name="feedback_description_hint">Describe what happened…</string>
<string name="feedback_submit">Send</string>
<string name="feedback_error_generic">Couldn\'t send. Try again.</string>
```

- [ ] **Step 3: `ShakeFeedbackEntry.kt`** — wires shake → navigate.

```kotlin
package com.hluhovskyi.zero.feedback

import android.hardware.SensorManager
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.common.Closeables
import java.io.Closeable

internal class ShakeFeedbackEntry(
    private val sensorManager: SensorManager,
    private val navigator: Navigator,
) {
    fun start(): Closeable {
        val detector = ShakeDetector(sensorManager) {
            navigator.navigateTo(Destinations.Feedback)
        }
        detector.start()
        return Closeables.of { detector.stop() }
    }
}
```

- [ ] **Step 4: `ApplicationComponent.kt`** — provide `DeviceInfo` as `@ApplicationScope` (uses `Build.*` + `BuildConfig.VERSION_NAME`/`VERSION_CODE`). Expose `val deviceInfo: DeviceInfo` and add it to `ActivityComponent.Dependencies`.

```kotlin
@Provides
@ApplicationScope
fun deviceInfo(): DeviceInfo = DeviceInfo(
    manufacturer = android.os.Build.MANUFACTURER ?: "",
    model = android.os.Build.MODEL ?: "",
    osVersion = android.os.Build.VERSION.RELEASE ?: "",
    sdkInt = android.os.Build.VERSION.SDK_INT,
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE.toLong(),
)
```

Add `abstract val deviceInfo: DeviceInfo` near `abstract val feedbackService: FeedbackService`.

- [ ] **Step 5: `MainActivityScreenComponent.kt`** — three changes:

(a) `Dependencies` interface: add `val feedbackService: FeedbackService`, `val deviceInfo: DeviceInfo`, `val clock: Clock`, `val context: Context`.

(b) `Module`: provide `InMemoryBreadcrumbs` (also bound as `Breadcrumbs`), `ShakeFeedbackEntry`, and a `@IntoSet feedbackNavigationEntry` that calls `navigatorScope.buildable(Destinations.Feedback, displayOption = BottomSheet) { FeedbackComponent.create(...) }`.

(c) Override `attach()`:

```kotlin
override fun attach(): Closeable = Closeables.combine(
    breadcrumbs.attach(),
    shakeFeedbackEntry.start(),
)
```

Read `MainActivityScreenComponent.kt` lines 540–700 for an exact example of `@IntoSet` nav entries; copy the colour-picker entry's shape.

- [ ] **Step 6: Wire `ActivityComponent.Dependencies`** — `ActivityComponent` (in `app/`) needs to expose `feedbackService`, `deviceInfo`, `clock`, `context` to `MainActivityScreenComponent`. Add the four fields to its `Dependencies` interface; `ApplicationComponent` already provides all of them.

- [ ] **Step 7: Build app**

```bash
./gradlew :app:assembleDebug
```

If Dagger reports missing bindings, follow [DI](../../agents/dependency-injection.md) — most likely an `ActivityComponent` `Dependencies` field is missing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/ app/src/main/res/values/strings.xml
git commit -m "feat(app): wire feedback nav entry, shake listener, breadcrumbs"
```

---

## Task 8: `BreadcrumbsLiteralOnly` lint rule

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/BreadcrumbsLiteralOnlyDetector.kt`
- Create: `lint-rules/src/test/kotlin/com/hluhovskyi/zero/lint/BreadcrumbsLiteralOnlyDetectorTest.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`

Mirror the structure of `HardcodedComposableStringDetector.kt`.

- [ ] **Step 1: Detector** — see spec §6 for the body. Use `getApplicableMethodNames() = listOf("log")`, resolve `method.containingClass?.qualifiedName == "com.hluhovskyi.zero.feedback.Breadcrumbs"`. Accept `ULiteralExpression` with `String` value; accept `UPolyadicExpression` whose operands are all string literals. Reject everything else with the message: `"Breadcrumbs.log() requires a string literal — concatenation, templates, or variables risk leaking PII."`

- [ ] **Step 2: Test** — use `lint().files(kotlin(\"\"\"...\"\"\")).run().expectClean()` for accept cases and `.expect(...)` for reject cases. Cover: literal accept, polyadic-of-literals accept, template reject (`\"$name\"`), variable reject, method-call reject, concat-with-variable reject.

- [ ] **Step 3: Register** — add `BreadcrumbsLiteralOnlyDetector.ISSUE` to `ZeroIssueRegistry.issues`.

- [ ] **Step 4: Run, expect PASS**

```bash
./gradlew :lint-rules:test
```

- [ ] **Step 5: Commit**

```bash
git add lint-rules/
git commit -m "feat(lint): BreadcrumbsLiteralOnly detector"
```

---

## Task 9: Verification

- [ ] **Step 1: Unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: all green. Fix any failure root-cause-first.

- [ ] **Step 2: Lint**

```bash
./gradlew :app:lintDebug
```

Expected: clean. The new `BreadcrumbsLiteralOnlyDetector` must not flag anything in the codebase (no current `Breadcrumbs.log()` calls).

- [ ] **Step 3: UI inspector verification** — invoke `zero-project:android-ui-inspector` to open the bottom sheet via `adb shell am start` of the navigation deep-link, or trigger a synthetic shake via `adb emu sensor set acceleration 0:0:20`. Verify: sheet visible, headline / hint / button text rendered, no overlap with system bars. Capture a screenshot for the PR.

- [ ] **Step 4: Push to PR #190**

```bash
git push
```

PR auto-updates.

---

## Self-Review Notes

- **Spec coverage:** §1 → Task 1 ✓; §2 → Tasks 2, 3, 7 ✓; §3 → Task 4 ✓; §4 → Task 4 + Task 5 ✓; §5 (PII) → enforced via Task 8 lint ✓; §6 → Task 8 ✓; §7 (verification) → Tasks 5, 6, 9 ✓.
- **Type consistency:** `Breadcrumbs.Entry` field is `message` (not `route`), `timestamp` is `Instant` everywhere. `FeedbackReport` shape from Phase 1 unchanged. `OnFeedbackSubmittedHandler.onFeedbackSubmitted()` matches usage in `DefaultFeedbackViewModel`.
- **Manual fallbacks called out:** ShakeDetector untested by unit (hardware), inspector covers actual rendering, `Error` theme token may need `MaterialTheme.colors.error` fallback.
