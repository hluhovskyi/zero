# Crash Analytics — Implementation Plan

Spec: [docs/superpowers/specs/2026-05-16-crash-analytics-design.md](../specs/2026-05-16-crash-analytics-design.md)

Implements Sentry crash reporting in a new `zero-crash` module, used by `app` only in release builds. **Structural analog for the whole module: [`zero-image-loading`](../../../zero-image-loading)** — match its file layout, `build.gradle` shape, `AGENTS.md` framing, and `consumer-rules.pro` / `proguard-rules.pro` boilerplate verbatim where they don't differ in intent. The Sentry SDK dependency is the only deviation.

Other analogs:
- BuildConfig field fed by env+localProps → existing `FEEDBACK_ENDPOINT` block in `app/build.gradle:51-54`.
- Env-injected variable in CD → existing `FEEDBACK_ENDPOINT: ${{ vars.FEEDBACK_ENDPOINT }}` line in `.github/workflows/cd.yml`.

---

## Task 1 — Scaffold `zero-crash` module

**New files:**

- `zero-crash/.gitignore` — single line `/build` (required by [Module Boundaries](../../agents/module-boundaries.md#new-module-scaffolding); copy from `zero-image-loading/.gitignore`).
- `zero-crash/consumer-rules.pro` — empty (copy from `zero-image-loading/consumer-rules.pro`).
- `zero-crash/proguard-rules.pro` — copy verbatim from `zero-image-loading/proguard-rules.pro`.
- `zero-crash/src/main/AndroidManifest.xml` — empty `<manifest>` shell **plus** a single child `<application>` element containing the auto-init disable meta-data (see Task 4).

**Modified files:**

- `settings.gradle` — add `include ':zero-crash'` in alphabetical position (after `':zero-core'`, before `':zero-database'`, matching existing ordering style; current ordering is roughly insertion-order so just append cleanly).

---

## Task 2 — `zero-crash/build.gradle`

**New file.** Copy `zero-image-loading/build.gradle` as the structural template and adjust:

- Remove the `org.jetbrains.kotlin.plugin.compose` plugin (no Compose in this module).
- Remove `buildFeatures { compose true }`.
- Remove `lint { targetSdk 32 }` and `testOptions { targetSdk 32 }` blocks (no UI tests, no Compose lint surface — copy from a module without them if simpler, but matching image-loading minus the Compose bits is fine; verify `./gradlew :zero-crash:lintDebug` still works).
- Dependencies block: keep `lintChecks project(":lint-rules")`, drop the Compose / Coil / `zero-api` lines, add `implementation deps.sentry`.
- Namespace stays `com.hluhovskyi.zero` (project convention; see image-loading).

Result should be ~20 lines.

---

## Task 3 — `CrashReporter` interface

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/CrashReporter.kt`.

```kotlin
package com.hluhovskyi.zero

import android.app.Application

interface CrashReporter {

    fun init(application: Application, dsn: String, versionName: String)

    companion object {

        fun sentry(): CrashReporter = SentryCrashReporter

        fun noop(): CrashReporter = NoopCrashReporter
    }
}
```

Public visibility on the interface; companion factories return the interface, never the impls.

---

## Task 4 — `SentryCrashReporter` impl + manifest gate

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/SentryCrashReporter.kt`.

```kotlin
package com.hluhovskyi.zero

import android.app.Application
import io.sentry.android.core.SentryAndroid

internal object SentryCrashReporter : CrashReporter {

    override fun init(application: Application, dsn: String, versionName: String) {
        if (dsn.isBlank()) return
        SentryAndroid.init(application) { options ->
            options.dsn = dsn
            options.release = versionName
            options.environment = "production"
        }
    }
}
```

**Modify:** `zero-crash/src/main/AndroidManifest.xml` (created in Task 1) — inside `<application>`, add:

```xml
<meta-data android:name="io.sentry.auto-init" android:value="false" />
```

This stops Sentry's `SentryInitProvider` from initializing on app start so we can gate init on build type in code. Putting the meta-data here (not in the app manifest) keeps the module self-contained.

---

## Task 5 — `NoopCrashReporter` impl

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/NoopCrashReporter.kt`.

```kotlin
package com.hluhovskyi.zero

import android.app.Application

internal object NoopCrashReporter : CrashReporter {

    override fun init(application: Application, dsn: String, versionName: String) = Unit
}
```

---

## Task 6 — `zero-crash/AGENTS.md`

**New file.** Copy `zero-image-loading/AGENTS.md` as the template; rewrite the content to match this module's surface:

- Standalone module rule (matches image-loading's rule 1).
- Surface: `CrashReporter` interface + companion factories `sentry()` and `noop()`.
- Why an interface for one real impl: vendor isolation, mirrors `ImageLoader`.
- Note that `init` is called once from `MainApplication.onCreate`; no other callers exist in v1.
- Note that the SDK auto-init is disabled in the module manifest — don't move it.

Aim for ~20–25 lines, same density as `zero-image-loading/AGENTS.md`.

---

## Task 7 — Add Sentry dependency to root `deps` map

**File:** `build.gradle` (root).

In the `deps` map, add (alphabetical position near `playIntegrity`):

```groovy
sentry: "io.sentry:sentry-android:7.18.1",
```

Verify `7.18.1` is the latest stable on Maven Central before committing; bump if there's a newer 7.x. The dep is consumed by `zero-crash`, not `app`.

---

## Task 8 — Add `SENTRY_DSN` BuildConfig field

**File:** `app/build.gradle`.

Following the analog at lines 51-54 (`FEEDBACK_ENDPOINT`), inside `defaultConfig`, add after the `FEEDBACK_INTEGRITY_PROJECT` block:

```groovy
buildConfigField "String", "SENTRY_DSN",
    "\"${System.getenv("SENTRY_DSN") ?: localProps['sentryDsn'] ?: ""}\""
```

`buildConfig = true` is already enabled.

Also add to the `dependencies` block:

```groovy
implementation(project(":zero-crash"))
```

Alphabetical position relative to the other `project(":zero-*")` lines.

---

## Task 9 — Wire init from `MainApplication`

**File:** `app/src/main/java/com/hluhovskyi/zero/MainApplication.kt`.

Edit `onCreate()`. Current (lines 37-43):

```kotlin
override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    }
}
```

Becomes:

```kotlin
override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    }

    val crashReporter = if (BuildConfig.DEBUG) CrashReporter.noop() else CrashReporter.sentry()
    crashReporter.init(
        application = this,
        dsn = BuildConfig.SENTRY_DSN,
        versionName = BuildConfig.VERSION_NAME,
    )
}
```

`CrashReporter` already lives in `com.hluhovskyi.zero` (same package as `MainApplication`) so no import is needed.

---

## Task 10 — Wire `SENTRY_DSN` into CD

**File:** `.github/workflows/cd.yml`.

In the `Build release AAB` step's `env:` block (lines 29-35), add one line at the end:

```yaml
SENTRY_DSN: ${{ vars.SENTRY_DSN }}
```

Order matches existing convention. `vars.SENTRY_DSN` must be configured in the repo's GitHub Variables page before the first CD run after merge — note this in the PR description as a manual prereq.

CI workflow (`ci.yml`) is unchanged — debug builds don't need the DSN.

---

## Task 11 — Update docs

**File:** `docs/agents/feedback-infra.md` — extend the "Secrets matrix" section (or add a sibling "Crash reporting config" subsection) capturing:

- Local dev: `local.gradle.properties` → `sentryDsn=https://...`
- CD release builds: `${{ vars.SENTRY_DSN }}` (Variable, not Secret — DSN ships in the AAB anyway)
- No Cloud Function involvement.

Cross-reference the spec at `docs/superpowers/specs/2026-05-16-crash-analytics-design.md`.

**File:** `AGENTS.md` — add a line to the Module Map for `zero-crash`. Suggested text:

```
zero-crash           → CrashReporter interface + Sentry impl
```

**File:** `docs/agents/module-boundaries.md` — add `zero-crash` to the ASCII module map (sibling of `zero-image-loading`).

---

## Verification

Run in order; fix any failure before moving on.

### 1. Spotless

```bash
./gradlew spotlessCheck
```

### 2. Unit tests

```bash
./gradlew testDebugUnitTest
```

No new test files added — there's nothing meaningful to unit-test on an SDK init wrapper. Existing tests must not regress.

### 3. Lint

```bash
./gradlew lintDebug
```

No new errors. If `zero-crash`'s lint surface fires on Sentry classes, stop and investigate.

### 4. Manual smoke (release build)

Set `sentryDsn=...` in `local.gradle.properties`, then:

```bash
./gradlew assembleRelease
./scripts/ui/adb.sh install -r app/build/outputs/apk/release/*.apk
./scripts/ui/adb.sh shell am crash com.hluhovskyi.zero
```

Verify within ~1 minute:
- The crash appears in the Sentry project dashboard.
- The event is tagged `release=1.0.<versionCode>` and `environment=production`.

### 5. Debug-build negative test

```bash
./gradlew installDebug
./scripts/ui/adb.sh shell am crash com.hluhovskyi.zero
```

Confirm the dashboard receives **nothing** new. The debug gate (NoopCrashReporter) must hold.

### UI inspection

Not applicable — this PR touches no UI.

---

## Out-of-scope follow-ups (do not implement here)

- `sentry-android-timber` integration for breadcrumbs.
- ANR detection.
- ProGuard mapping upload (only relevant once `minifyEnabled true`).
- `captureException` / `addBreadcrumb` methods on the `CrashReporter` interface — add when a caller actually needs them.
- GitHub auto-issue integration — configured in the Sentry web UI post-merge.
- User-facing opt-in/out toggle.
