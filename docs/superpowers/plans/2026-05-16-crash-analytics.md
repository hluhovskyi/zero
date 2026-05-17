# Crash Analytics — Implementation Plan

Spec: [docs/superpowers/specs/2026-05-16-crash-analytics-design.md](../specs/2026-05-16-crash-analytics-design.md)

Implements Sentry crash reporting for release builds only. No new modules, no DI changes, no UI changes. Structural analog for "BuildConfig field fed by env+localProps": the existing `FEEDBACK_ENDPOINT` block in `app/build.gradle` lines 51-54. Structural analog for "env-injected secret in CD": the existing `FEEDBACK_ENDPOINT: ${{ vars.FEEDBACK_ENDPOINT }}` line in `.github/workflows/cd.yml`.

---

## Task 1 — Add Sentry dependency

**Files:** `build.gradle` (root), `app/build.gradle`.

In root `build.gradle`, add to the `deps` map (alphabetic position near `playIntegrity`):

```groovy
sentry: "io.sentry:sentry-android:7.18.1",
```

In `app/build.gradle`, add to the `dependencies` block alongside `deps.timber`:

```groovy
implementation deps.sentry
```

Verify version `7.18.1` is the latest stable on Maven Central before committing; bump if there's a newer 7.x. Do **not** add the Sentry Gradle plugin (out of scope per spec — no mapping upload needed while `minifyEnabled false`).

---

## Task 2 — Add SENTRY_DSN BuildConfig field

**File:** `app/build.gradle`.

Following the analog at lines 51-54 (`FEEDBACK_ENDPOINT`), inside `defaultConfig`, add:

```groovy
buildConfigField "String", "SENTRY_DSN",
    "\"${System.getenv("SENTRY_DSN") ?: localProps['sentryDsn'] ?: ""}\""
```

Place it directly after the `FEEDBACK_INTEGRITY_PROJECT` block. `buildConfig = true` is already enabled.

---

## Task 3 — Disable Sentry auto-init in manifest

**File:** `app/src/main/AndroidManifest.xml`.

Inside the existing `<application>` element, add a single `<meta-data>` child:

```xml
<meta-data android:name="io.sentry.auto-init" android:value="false" />
```

This stops Sentry's `SentryInitProvider` from initializing on app start so we can gate init on build type in code.

---

## Task 4 — Create CrashReporting init function

**File (new):** `app/src/main/java/com/hluhovskyi/zero/crash/CrashReporting.kt`.

Single top-level function. No class, no DI binding, no companion. Package `com.hluhovskyi.zero.crash`.

```kotlin
package com.hluhovskyi.zero.crash

import android.app.Application
import io.sentry.android.core.SentryAndroid

internal fun initCrashReporting(
    application: Application,
    dsn: String,
    versionName: String,
) {
    if (dsn.isBlank()) return
    SentryAndroid.init(application) { options ->
        options.dsn = dsn
        options.release = versionName
        options.environment = "production"
    }
}
```

`internal` visibility — only `MainApplication` calls it. No analog file in the codebase for "single-function package"; this is small enough that introducing a class would be ceremony. If a reviewer wants it wrapped, we can refactor in a follow-up.

---

## Task 5 — Wire init from MainApplication

**File:** `app/src/main/java/com/hluhovskyi/zero/MainApplication.kt`.

Edit `onCreate()` to call the new function in the **non-DEBUG** branch. Current structure (lines 37-43):

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
    } else {
        initCrashReporting(
            application = this,
            dsn = BuildConfig.SENTRY_DSN,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}
```

Add the `import com.hluhovskyi.zero.crash.initCrashReporting` at the top.

The empty-DSN guard inside `initCrashReporting` means a release build accidentally shipped without `SENTRY_DSN` set will run silently (no SDK init, no crash). This is the chosen behavior per spec.

---

## Task 6 — Wire SENTRY_DSN into CD

**File:** `.github/workflows/cd.yml`.

In the `Build release AAB` step's `env:` block (lines 29-35), add one line at the end:

```yaml
SENTRY_DSN: ${{ vars.SENTRY_DSN }}
```

Order matches existing convention (secrets first, vars after). `vars.SENTRY_DSN` must be configured in the repo's GitHub Variables page before the first CD run after merge — note this in the PR description as a manual prereq.

CI workflow (`ci.yml`) is unchanged — debug builds don't need the DSN.

---

## Task 7 — Document the new config key

**File:** `docs/agents/feedback-infra.md`.

This file already documents the `FEEDBACK_ENDPOINT` / `FEEDBACK_INTEGRITY_PROJECT` config pattern in its "Secrets matrix" section. The Sentry DSN follows the exact same pattern, so it belongs in the same table.

Add a third column to the existing secrets matrix (or, if cleaner, a new "Crash reporting" subsection below it) capturing:

- Local dev: `local.gradle.properties` → `sentryDsn=https://...`
- CD release builds: `${{ vars.SENTRY_DSN }}` (Variable, not Secret — DSN ships in the AAB anyway)
- No Cloud Function involvement.

Cross-reference the spec at `docs/superpowers/specs/2026-05-16-crash-analytics-design.md`.

---

## Verification

Run in order; fix any failure before moving on.

### 1. Spotless

```bash
./gradlew spotlessCheck
```

Expected: pass. (If a Gradle file changed formatting, run `./gradlew spotlessApply` and re-check.)

### 2. Unit tests

```bash
./gradlew testDebugUnitTest
```

Expected: pass. No new test files added — there's nothing meaningful to unit-test on an SDK init wrapper. Existing tests must not regress.

### 3. Lint

```bash
./gradlew lintDebug
```

Expected: no new errors. Sentry SDK should not trip any project lint rules; if `RemoteComponentEncapsulation` or similar fires, stop and investigate.

### 4. Manual smoke (release build)

Build a release AAB locally with a real DSN in `local.gradle.properties`:

```bash
./gradlew assembleRelease
```

Then install on the acquired emulator (`./scripts/install-app.sh` if present, else `./scripts/ui/adb.sh install -r app/build/outputs/apk/release/*.apk`), launch the app, and from `adb shell` send `am crash com.hluhovskyi.zero` to force an uncaught exception.

Verify within ~1 minute:
- The crash appears in the Sentry project dashboard.
- The event is tagged `release=1.0.<versionCode>` and `environment=production`.

### 5. Debug-build negative test

Build and install a debug build (`./gradlew installDebug`), trigger the same crash, and confirm the dashboard receives **nothing** new. The debug gate must hold.

### UI inspection

Not applicable — this PR touches no UI.

---

## Out-of-scope follow-ups (do not implement here)

- `sentry-android-timber` integration for breadcrumbs.
- ANR detection.
- ProGuard mapping upload (only relevant once `minifyEnabled true`).
- GitHub auto-issue integration — configured in the Sentry web UI post-merge.
- User-facing opt-in/out toggle.
