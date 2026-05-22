# Crash Analytics — Implementation Plan

Spec: [docs/superpowers/specs/2026-05-16-crash-analytics-design.md](../specs/2026-05-16-crash-analytics-design.md)

Implements Sentry crash reporting in a new `zero-crash` Gradle module. The crash module owns its DSN (in its own `BuildConfig`), exposes a Dagger `CrashComponent` whose only surface is `attachable: Attachable`, and the app drives init through the project's `Attachable` lifecycle — `MainApplication.onCreate` calls `applicationComponent.attachable.attach()` which composes `CrashComponent.attachable.attach()` which calls `SentryAndroid.init`.

**Structural analogs (open side-by-side while implementing — every new file copies its analog's structure, naming, and surface conventions):**

- Module skeleton (`.gitignore`, manifest, proguard files, `AGENTS.md`) → [`zero-image-loading/`](../../../zero-image-loading).
- Module `build.gradle` with Dagger + KSP → [`zero-remote/build.gradle`](../../../zero-remote/build.gradle).
- `localProps` boilerplate for the `SENTRY_DSN` BuildConfig field → top of [`app/build.gradle`](../../../app/build.gradle) (lines 10-12).
- Component shape — `Attachable`-only surface, file-private `@Scope`, `Dependencies`, `Buildable<T>` Builder, companion `builder(deps)` pre-setting defaults, `@Provides` returning `Attachable.Noop` for the disabled path, private `*Attachable` class doing the side-effect work → [`zero-core/.../PresetsComponent.kt`](../../../zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsComponent.kt).
- App-level composer class that merges child `Attachable`s → [`app/.../activity/AttachActivityComponent.kt`](../../../app/src/main/java/com/hluhovskyi/zero/activity/AttachActivityComponent.kt).
- App-module `@Provides` wiring for the new child component + its exposed `Attachable` → the `RemoteModule` block at the bottom of [`app/.../ApplicationComponent.kt`](../../../app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt) (lines 466-482).
- Env-injected variable in CD → existing `FEEDBACK_ENDPOINT: ${{ vars.FEEDBACK_ENDPOINT }}` line in `.github/workflows/cd.yml`.

---

## Task 1 — Scaffold `zero-crash` module skeleton

**New files:**

- `zero-crash/.gitignore` — single line `/build` (required by [Module Boundaries](../../agents/module-boundaries.md#new-module-scaffolding); copy from `zero-image-loading/.gitignore`).
- `zero-crash/consumer-rules.pro` — empty (copy from `zero-image-loading/consumer-rules.pro`).
- `zero-crash/proguard-rules.pro` — copy verbatim from `zero-image-loading/proguard-rules.pro`.
- `zero-crash/src/main/AndroidManifest.xml` — `<manifest>` shell containing an `<application>` element with one `<meta-data>` child to disable Sentry's auto-init (exact element in Task 4).

**Modified files:**

- `settings.gradle` — add `include ':zero-crash'` after the existing module includes.

---

## Task 2 — `zero-crash/build.gradle`

**New file.** Use `zero-remote/build.gradle` as the structural template.

- Plugins: `com.android.library`, `com.google.devtools.ksp`. No Compose, no serialization, no desugaring.
- Namespace: `com.hluhovskyi.zero.crash`.
- **At the top of the file (above the `android` block)**, copy the `localProps` boilerplate from `app/build.gradle:10-12`:

   ```groovy
   def localProps = new Properties()
   def localPropsFile = rootProject.file("local.gradle.properties")
   if (localPropsFile.exists()) localPropsFile.withInputStream { localProps.load(it) }
   ```

- Inside `defaultConfig`, add the SENTRY_DSN BuildConfig field (analog: `app/build.gradle:51-54`):

   ```groovy
   buildConfigField "String", "SENTRY_DSN",
       "\"${System.getenv("SENTRY_DSN") ?: localProps['sentryDsn'] ?: ""}\""
   ```

- Inside `android`, add `buildFeatures { buildConfig = true }` (otherwise `buildConfigField` produces nothing).
- Dependencies:
  - `lintChecks project(":lint-rules")`
  - `implementation(project(":zero-api"))` — needed for `Attachable`, `Closeables`, `Buildable` from `com.hluhovskyi.zero.common`.
  - `implementation deps.dagger.runtime` + `ksp deps.dagger.compiler`
  - `implementation deps.javax.inject`
  - `implementation deps.sentry` (added in Task 6)

Result should be ~30 lines.

---

## Task 3 — `zero-crash/AGENTS.md`

**New file.** Copy `zero-image-loading/AGENTS.md`'s structure and density. Content:

- Only one zero-* dep — `zero-api` — solely for `Attachable`/`Closeables`/`Buildable`.
- Surface: `CrashComponent` with `abstract val attachable: Attachable`. No other public types in v1.
- Why a Dagger component for one SDK init: matches the project's component-per-feature convention; the `Attachable` returned by the component is composed into `AttachApplicationComponent` so init is driven by the same lifecycle hook as `PresetsComponent` / `BiometricLockComponent`.
- DSN lives in **this module's** `BuildConfig` — not `app`'s — read inside the `@Provides`. Do not pass DSN through the Builder.
- The SDK auto-init is disabled in the module manifest — don't move it.

Aim for ~25 lines.

---

## Task 4 — `CrashComponent` + `CrashAttachable`

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/CrashComponent.kt`.

Mirror `PresetsComponent.kt` one-for-one (file structure, scope declaration, companion factories, Builder shape):

- File-private scope at file top: `@Scope @Retention(AnnotationRetention.SOURCE) private annotation class CrashScope`.
- `abstract class CrashComponent` with `@CrashScope @dagger.Component(modules = [Module::class], dependencies = [Dependencies::class])`.
- Surface: `abstract val attachable: Attachable`. **No `CrashReporter`, no `crashReporter` getter** — `attachable` is the only thing the component exposes.
- `interface Dependencies { val application: Application }`.
- `companion object { fun builder(dependencies: Dependencies): Builder = DaggerCrashComponent.builder().dependencies(dependencies).versionName("") }`.
- `@dagger.Component.Builder interface Builder : Buildable<CrashComponent> { fun dependencies(...): Builder; @BindsInstance fun versionName(versionName: String): Builder }`. **No `.dsn(...)` setter, no `.debug(...)` setter.**
- `@dagger.Module object Module` with one `@Provides @CrashScope internal fun attachable(application: Application, versionName: String): Attachable` that:
  - Returns `Attachable.Noop` if `BuildConfig.DEBUG || BuildConfig.SENTRY_DSN.isBlank()`.
  - Otherwise returns `CrashAttachable(application, BuildConfig.SENTRY_DSN, versionName)`.

Then a private class in the same file (mirrors `PresetsAttachable` at the bottom of `PresetsComponent.kt`):

```kotlin
private class CrashAttachable(
    private val application: Application,
    private val dsn: String,
    private val versionName: String,
) : Attachable {

    override fun attach(): Closeable {
        SentryAndroid.init(application) { options ->
            options.dsn = dsn
            options.release = versionName
            options.environment = "production"
        }
        return Closeables.empty()
    }
}
```

`Closeables.empty()` because `SentryAndroid.init` mutates global SDK state; there's no per-attach handle to tear down.

**Manifest gate** (`zero-crash/src/main/AndroidManifest.xml` created in Task 1): inside `<application>`, add:

```xml
<meta-data android:name="io.sentry.auto-init" android:value="false" />
```

---

## Task 5 — `AttachApplicationComponent`

**New file:** `app/src/main/java/com/hluhovskyi/zero/AttachApplicationComponent.kt`.

One-for-one copy of `app/.../activity/AttachActivityComponent.kt`'s shape, just at the application layer:

```kotlin
package com.hluhovskyi.zero

import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import java.io.Closeable

class AttachApplicationComponent(
    private val crashComponent: CrashComponent,
) : Attachable {

    override fun attach(): Closeable = Closeables.merge(
        crashComponent.attachable.attach(),
    )
}
```

`Closeables.merge(...)` with one arg is intentional — it leaves room for future application-level `Attachable`s to be added as siblings without restructuring.

---

## Task 6 — Add Sentry dependency to root `deps` map

**File:** `build.gradle` (root).

In the `deps` map, add (alphabetical position near `playIntegrity`):

```groovy
sentry: "io.sentry:sentry-android:7.18.1",
```

Verify `7.18.1` is the latest stable on Maven Central before committing; bump if there's a newer 7.x. The dep is consumed by `zero-crash` only.

---

## Task 7 — Add module dep to `app/build.gradle`

**File:** `app/build.gradle`.

In the `dependencies` block, add (alphabetical position among `project(":zero-*")` lines):

```groovy
implementation(project(":zero-crash"))
```

**Do not add a `SENTRY_DSN` `buildConfigField`** to `app/build.gradle`. The DSN belongs to `zero-crash` per the spec.

---

## Task 8 — Wire `ApplicationComponent` to build `CrashComponent` and expose its `Attachable`

**File:** `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`.

Four edits:

1. **Extend `Dependencies`** (current declaration around line 95):

   ```kotlin
   interface Dependencies {
       val context: Context
       val application: Application
   }
   ```

   Add `import android.app.Application` at the top.

2. **Add `CrashComponent.Dependencies` to superinterfaces** and **expose `attachable`**:

   ```kotlin
   abstract class ApplicationComponent :
       ActivityComponent.Dependencies,
       CrashComponent.Dependencies,
       DatabaseComponent.Dependencies,
       RemoteComponent.Dependencies,
       ResourceResolverComponent.Dependencies,
       SettingsComponent.Dependencies,
       ImportComponent.Dependencies {

       abstract val activityComponentBuilder: ActivityComponent.Builder
       abstract val attachable: Attachable
       abstract val logger: Logger
       abstract val databaseComponent: DatabaseComponent
       abstract override val feedbackService: FeedbackService
       abstract override val deviceInfo: DeviceInfo
       // ...
   }
   ```

   Order alphabetical within the abstract-val block (already the convention). Add imports for `com.hluhovskyi.zero.CrashComponent`, `com.hluhovskyi.zero.common.Attachable`.

3. **Add a new `CrashModule`** at the bottom of the file, mirroring `RemoteModule` (lines 466-482):

   ```kotlin
   @dagger.Module
   internal object CrashModule {

       @Provides
       @ApplicationScope
       fun crashComponent(
           component: ApplicationComponent,
       ): CrashComponent = CrashComponent.builder(component)
           .versionName(BuildConfig.VERSION_NAME)
           .build()

       @Provides
       @ApplicationScope
       fun attachable(
           crashComponent: CrashComponent,
       ): Attachable = AttachApplicationComponent(crashComponent)
   }
   ```

4. **Include `CrashModule::class`** in the `includes` list of `ApplicationComponent.Module`'s `@dagger.Module` annotation (currently includes `DatabaseModule::class` and `RemoteModule::class`).

---

## Task 9 — Wire `MainApplication`

**File:** `app/src/main/java/com/hluhovskyi/zero/MainApplication.kt`.

Two edits:

1. **Provide `application` in the Dependencies object** (current block at lines 16-18):

   ```kotlin
   override val applicationComponent: ApplicationComponent by lazy {
       val dependencies = object : ApplicationComponent.Dependencies {
           override val context: Context = this@MainApplication
           override val application: Application = this@MainApplication
       }
       ApplicationComponent.builder(dependencies).build()
   }
   ```

   `Application` import already present (the class extends it).

2. **Trigger attach in `onCreate`** (after the existing Timber.plant block):

   ```kotlin
   override fun onCreate() {
       super.onCreate()

       if (BuildConfig.DEBUG) {
           Timber.plant(Timber.DebugTree())
       }

       applicationComponent.attachable.attach()
   }
   ```

   The returned `Closeable` is intentionally discarded — the process lives until killed, and `SentryAndroid.init` is a one-way global mutation. Matches how `AttachActivityComponent` is *not* discarded at the activity level either — there the `AttachWithView` Compose effect closes on view destruction; at the app level there's nothing analogous to honor.

---

## Task 10 — Wire `SENTRY_DSN` into CD

**File:** `.github/workflows/cd.yml`.

In the `Build release AAB` step's `env:` block (lines 29-35), add one line at the end:

```yaml
SENTRY_DSN: ${{ vars.SENTRY_DSN }}
```

Order matches existing convention (secrets first, vars after). `vars.SENTRY_DSN` must be configured in the repo's GitHub Variables page before the first CD run after merge — note this in the PR description as a manual prereq.

CI workflow (`ci.yml`) is unchanged — debug builds skip Sentry, so unit tests don't need the DSN.

---

## Task 11 — Update docs

**File:** `docs/agents/feedback-infra.md` — extend the "Secrets matrix" section (or add a sibling "Crash reporting config" subsection) capturing:

- Local dev: `local.gradle.properties` → `sentryDsn=https://...` (read by **`zero-crash/build.gradle`**, not `app/build.gradle`).
- CD release builds: `${{ vars.SENTRY_DSN }}` (Variable, not Secret — DSN ships in the AAB anyway).
- No Cloud Function involvement.

Cross-reference the spec at `docs/superpowers/specs/2026-05-16-crash-analytics-design.md`.

**File:** `AGENTS.md` — add a line to the Module Map for `zero-crash`. Suggested text:

```
zero-crash           → CrashComponent (Sentry crash reporting, attach-only)
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

No new test files added — there's nothing meaningful to unit-test on a Dagger-driven SDK init. Existing tests must not regress.

### 3. Lint

```bash
./gradlew lintDebug
```

No new errors. The `:lint-rules` module enforces invariants like `ScopedComponentBuilder` — `@CrashScope` is on the `Module.attachable` `@Provides`, not on a Builder provider, so it must satisfy `ScopedComponentBuilder`.

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

Confirm the dashboard receives **nothing** new. The debug gate (`BuildConfig.DEBUG = true` inside `zero-crash` → `Attachable.Noop`) must hold.

### UI inspection

Not applicable — this PR touches no UI.

---

## Out-of-scope follow-ups (do not implement here)

- `sentry-android-timber` integration for breadcrumbs.
- ANR detection.
- ProGuard mapping upload (only relevant once `minifyEnabled true`).
- A public `CrashReporter` interface with `captureException` / `addBreadcrumb` — add when a caller actually needs them; design will mirror `ImageLoader` (interface in zero-crash, exposed via `CrashComponent.crashReporter`).
- GitHub auto-issue integration — configured in the Sentry web UI post-merge.
- User-facing opt-in/out toggle.
