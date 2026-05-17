# Crash Analytics — Implementation Plan

Spec: [docs/superpowers/specs/2026-05-16-crash-analytics-design.md](../specs/2026-05-16-crash-analytics-design.md)

Implements Sentry crash reporting in a new `zero-crash` Gradle module, exposed to the rest of the codebase as a Dagger `CrashComponent` that the `ApplicationComponent` builds and accesses.

**Structural analogs (these names must be opened side-by-side while implementing — every new file copies its analog's structure, naming, and surface conventions):**

- Module skeleton (`build.gradle`, `.gitignore`, manifest, proguard files, `AGENTS.md`) → [`zero-image-loading/`](../../../zero-image-loading).
- Module skeleton with Dagger + KSP (for the `build.gradle` plugins block and `dagger.runtime` + `ksp dagger.compiler` lines) → [`zero-remote/build.gradle`](../../../zero-remote/build.gradle).
- Dagger component shape — file-private `@Scope`, file-private `@Qualifier`s, `Dependencies` interface, `@Component.Builder` with `@BindsInstance`, companion `builder(deps)` that pre-sets defaults → [`zero-remote/.../RemoteComponent.kt`](../../../zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt).
- App-side wiring (`@Provides` for the child component + a second `@Provides` extracting the exposed service) → the `RemoteModule` block at the bottom of [`app/.../ApplicationComponent.kt`](../../../app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt) (lines 466-482).
- BuildConfig field fed by env+localProps → existing `FEEDBACK_ENDPOINT` block in `app/build.gradle:51-54`.
- Env-injected variable in CD → existing `FEEDBACK_ENDPOINT: ${{ vars.FEEDBACK_ENDPOINT }}` line in `.github/workflows/cd.yml`.

---

## Task 1 — Scaffold `zero-crash` module skeleton

**New files:**

- `zero-crash/.gitignore` — single line `/build` (required by [Module Boundaries](../../agents/module-boundaries.md#new-module-scaffolding); copy from `zero-image-loading/.gitignore`).
- `zero-crash/consumer-rules.pro` — empty (copy from `zero-image-loading/consumer-rules.pro`).
- `zero-crash/proguard-rules.pro` — copy verbatim from `zero-image-loading/proguard-rules.pro`.
- `zero-crash/src/main/AndroidManifest.xml` — `<manifest>` shell containing an `<application>` element with one `<meta-data>` child to disable Sentry's auto-init (see Task 4 for the exact element).

**Modified files:**

- `settings.gradle` — add `include ':zero-crash'` after the existing module includes.

---

## Task 2 — `zero-crash/build.gradle`

**New file.** Use `zero-remote/build.gradle` as the structural template (because we need Dagger + KSP, which image-loading doesn't have).

- Plugins: `com.android.library`, `com.google.devtools.ksp`. No Compose, no serialization, no desugaring (Sentry SDK is 21+ compatible without desugar — verify during Task 11 verification; if a desugar warning appears, add `coreLibraryDesugaringEnabled true` + the desugar dep).
- Namespace: `com.hluhovskyi.zero.crash`.
- Dependencies: drop everything zero-remote pulls that we don't need. Final list:
  - `lintChecks project(":lint-rules")`
  - `implementation deps.dagger.runtime` + `ksp deps.dagger.compiler`
  - `implementation deps.javax.inject`
  - `implementation deps.sentry` (added in Task 7)

Result should be ~25 lines. **Do not add zero-api** — we are not using `Buildable` (matches RemoteComponent, which also doesn't). The module is truly standalone in zero-* terms.

---

## Task 3 — `CrashReporter` interface + impls

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/CrashReporter.kt`.

```kotlin
package com.hluhovskyi.zero

interface CrashReporter
```

Marker interface in v1. No methods.

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/SentryCrashReporter.kt`.

```kotlin
package com.hluhovskyi.zero

internal object SentryCrashReporter : CrashReporter
```

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/NoopCrashReporter.kt`.

```kotlin
package com.hluhovskyi.zero

internal object NoopCrashReporter : CrashReporter
```

Both `internal`. The init side effect lives in `CrashComponent.Module` — not here — because Sentry doesn't expose a per-instance state we can encapsulate; `SentryAndroid.init` mutates global SDK state, so calling it from a `@Provides` is the truthful representation.

---

## Task 4 — `CrashComponent`

**New file:** `zero-crash/src/main/java/com/hluhovskyi/zero/CrashComponent.kt`.

Mirror `RemoteComponent.kt`'s shape one-for-one. Specifically:

- File-private annotations declared at file top (above the `interface CrashComponent` block):
  - `@Scope @Retention(AnnotationRetention.SOURCE) private annotation class CrashScope`
  - `@Qualifier @Retention(AnnotationRetention.SOURCE) private annotation class Dsn`
  - `@Qualifier @Retention(AnnotationRetention.SOURCE) private annotation class VersionName`
- `interface CrashComponent` (interface, not abstract class — matches RemoteComponent).
- `@CrashScope @dagger.Component(modules = [Module::class], dependencies = [Dependencies::class])`.
- Surface: `val crashReporter: CrashReporter`.
- `interface Dependencies { val application: Application }`.
- `companion object { fun builder(dependencies: Dependencies): Builder = DaggerCrashComponent.builder().dependencies(dependencies).dsn("").versionName("").debug(true) }`.
- `@dagger.Component.Builder interface Builder { fun dependencies(...); @BindsInstance fun dsn(@Dsn ...); @BindsInstance fun versionName(@VersionName ...); @BindsInstance fun debug(...): Builder; fun build(): CrashComponent }`.
- `@dagger.Module object Module` with one `@Provides @CrashScope internal fun crashReporter(application, @Dsn dsn, @VersionName versionName, debug): CrashReporter` that returns `NoopCrashReporter` if `debug || dsn.isBlank()`, otherwise calls `SentryAndroid.init(application) { options -> options.dsn = dsn; options.release = versionName; options.environment = "production" }` and returns `SentryCrashReporter`.

**Manifest gate** (`zero-crash/src/main/AndroidManifest.xml` created in Task 1): inside `<application>`, add `<meta-data android:name="io.sentry.auto-init" android:value="false" />`. This stops Sentry's `SentryInitProvider` from initializing on app start so init is driven by the `@Provides` above.

`debug` Boolean has no qualifier — only one Boolean binding in the graph, no collision.

---

## Task 5 — `zero-crash/AGENTS.md`

**New file.** Copy `zero-image-loading/AGENTS.md`'s structure and density. Content to cover:

- Standalone module (no zero-* deps; depends only on Sentry SDK + Dagger).
- Surface: `CrashReporter` (marker interface in v1) + `CrashComponent` (Dagger component built once at the app layer).
- Why a Dagger component for a single SDK init: matches the project's component-per-feature convention (`RemoteComponent`, `SettingsComponent`, etc.); init runs inside `@Provides` so binding lookup *is* startup.
- Note that the SDK auto-init is disabled in the module manifest — don't move it.
- Note `MainApplication.onCreate` must touch `applicationComponent.crashReporter` to force init (Dagger lazy-resolves scoped bindings).

Aim for ~25–30 lines.

---

## Task 6 — Add Sentry dependency to root `deps` map

**File:** `build.gradle` (root).

In the `deps` map, add (alphabetical position near `playIntegrity`):

```groovy
sentry: "io.sentry:sentry-android:7.18.1",
```

Verify `7.18.1` is the latest stable on Maven Central before committing; bump if there's a newer 7.x. The dep is consumed by `zero-crash`, not `app`.

---

## Task 7 — Add `SENTRY_DSN` BuildConfig field + module dep to `app`

**File:** `app/build.gradle`.

Following the analog at lines 51-54 (`FEEDBACK_ENDPOINT`), inside `defaultConfig`, add after the `FEEDBACK_INTEGRITY_PROJECT` block:

```groovy
buildConfigField "String", "SENTRY_DSN",
    "\"${System.getenv("SENTRY_DSN") ?: localProps['sentryDsn'] ?: ""}\""
```

In the `dependencies` block, add (alphabetical position among `project(":zero-*")` lines):

```groovy
implementation(project(":zero-crash"))
```

---

## Task 8 — Wire `ApplicationComponent` to build `CrashComponent`

**File:** `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`.

Three edits:

1. **Extend `Dependencies`** (current declaration around line 95):

   ```kotlin
   interface Dependencies {
       val context: Context
       val application: Application
   }
   ```

   Add `import android.app.Application` at the top.

2. **Add `CrashComponent.Dependencies` to superinterfaces** (current list at lines 82-87) and **expose `crashReporter`**:

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
       abstract val logger: Logger
       abstract val databaseComponent: DatabaseComponent
       abstract val crashReporter: CrashReporter
       abstract override val feedbackService: FeedbackService
       abstract override val deviceInfo: DeviceInfo
       // ...
   }
   ```

   Order matches the alphabetical convention already in use. Add `import com.hluhovskyi.zero.CrashComponent` and `import com.hluhovskyi.zero.CrashReporter` at the top.

3. **Add a new `CrashModule`** at the bottom of the file, **mirroring `RemoteModule`** (lines 466-482):

   ```kotlin
   @dagger.Module
   internal object CrashModule {

       @Provides
       @ApplicationScope
       fun crashComponent(
           component: ApplicationComponent,
       ): CrashComponent = CrashComponent.builder(component)
           .dsn(BuildConfig.SENTRY_DSN)
           .versionName(BuildConfig.VERSION_NAME)
           .debug(BuildConfig.DEBUG)
           .build()

       @Provides
       @ApplicationScope
       fun crashReporter(
           crashComponent: CrashComponent,
       ): CrashReporter = crashComponent.crashReporter
   }
   ```

   Add `CrashModule::class` to the `includes` list of `ApplicationComponent.Module`'s `@dagger.Module` annotation (currently includes `DatabaseModule::class` and `RemoteModule::class`).

---

## Task 9 — Wire `MainApplication`

**File:** `app/src/main/java/com/hluhovskyi/zero/MainApplication.kt`.

Two edits to existing code:

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

   Add `import android.app.Application` if not already present (it isn't — only `Application` from the class header).

2. **Touch `crashReporter` in `onCreate`** (after the existing Timber.plant block):

   ```kotlin
   override fun onCreate() {
       super.onCreate()

       if (BuildConfig.DEBUG) {
           Timber.plant(Timber.DebugTree())
       }

       applicationComponent.crashReporter
   }
   ```

   The bare expression `applicationComponent.crashReporter` is intentional — it forces Dagger to materialize the binding, which runs `SentryAndroid.init` inside the `@Provides`. Add a `// Forces SentryAndroid.init via the CrashComponent @Provides — see zero-crash/AGENTS.md.` comment on that line; this is the rare case where the *why* is non-obvious from the code.

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

- Local dev: `local.gradle.properties` → `sentryDsn=https://...`
- CD release builds: `${{ vars.SENTRY_DSN }}` (Variable, not Secret — DSN ships in the AAB anyway)
- No Cloud Function involvement.

Cross-reference the spec at `docs/superpowers/specs/2026-05-16-crash-analytics-design.md`.

**File:** `AGENTS.md` — add a line to the Module Map for `zero-crash`. Suggested text:

```
zero-crash           → CrashReporter + CrashComponent (Sentry impl)
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

No new errors. The `:lint-rules` module enforces invariants like `ScopedComponentBuilder` and `Default*` visibility — `CrashScope` annotation on `Module.crashReporter` (not on a Builder provider) must satisfy `ScopedComponentBuilder`; `NoopCrashReporter` / `SentryCrashReporter` are objects with `internal` visibility, satisfying `DefaultImplMustBeInternal` if it applies.

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

Confirm the dashboard receives **nothing** new. The debug gate (`debug = true` → `NoopCrashReporter`) must hold.

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
