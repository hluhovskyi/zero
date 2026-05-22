# Crash Analytics — Sentry Integration

## Problem

The app currently has no automatic crash reporting. Crashes in production are invisible unless a user manually files an issue via the existing feedback pipe (which only fires when the user notices something is wrong and chooses to report it). Result: silent crashes go untracked; regressions are discovered late, if at all.

## Goals

- Capture uncaught exceptions from release builds and report them to a hosted backend.
- Tag reports with `versionName` so regressions can be tied to a specific release.
- Zero impact on debug builds (no telemetry during development).
- Zero new permissions; zero new user-facing UI.
- Encapsulate the Sentry SDK behind a Dagger component **and** drive init through the project's existing `Attachable` lifecycle, not through `MainApplication.onCreate` poking the DI graph.
- Keep the DSN inside the crash module — `app` shouldn't know it exists.

## Non-Goals

- Performance / transaction tracing.
- ANR detection (deferred — Sentry supports it but it adds complexity; not part of v1).
- ProGuard mapping upload (the app currently ships with `minifyEnabled false`; stack traces are already symbolicated).
- Timber → breadcrumbs bridge (`sentry-android-timber`) — deferred. Possible follow-up once we have a release crash to debug.
- User-facing opt-in/out toggle in Settings. The app is a personal-finance tool with a small audience; "always on for release builds" is the chosen posture.
- GitHub auto-issue integration. Configured in the Sentry web UI post-merge; no code change required.
- A public `CrashReporter` interface in v1. No caller needs `captureException` / `addBreadcrumb` yet; the component exposes only `attachable: Attachable`. Add a `CrashReporter` interface when v2 has a real consumer for it.

## Choice — Why Sentry

Picked over Firebase Crashlytics and Bugsnag:

- **No Google ecosystem footprint added.** Crashlytics would force `google-services` plugin + `google-services.json` + Firebase BoM (~method-count and APK-size hit). Sentry is a single SDK jar and a DSN string.
- **Privacy posture matches a finance app.** Sentry has explicit opt-out for PII collection; data goes to a single vendor, not the broader Google telemetry surface.
- **Single dashboard if iOS ever lands.** Sentry unifies platforms under one project; Crashlytics splits per Firebase app.
- **First-class GitHub integration.** Auto-create + bidirectional resolve in `hluhovskyi/zero` once enabled in the Sentry UI.
- **Cost.** Free Developer tier (5k errors/mo) is well above expected volume for this app.

## Architecture

**New Gradle module: `zero-crash`.** Structural shape borrows from three places:

- Module skeleton (`.gitignore`, manifest, proguard files, `AGENTS.md`) → `zero-image-loading`.
- `build.gradle` plugins/deps (Dagger + KSP, plus `localProps` boilerplate for the DSN field) → `zero-remote` + the `localProps` block at the top of `app/build.gradle`.
- Dagger component shape (file-private `@Scope`, `Dependencies` interface, `@Component.Builder` with `@BindsInstance`, companion `builder(deps)` pre-setting defaults, **`abstract val attachable: Attachable` as the only surface**, private `*Attachable` class that does the side-effect work) → [`zero-core/.../PresetsComponent.kt`](../../../zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsComponent.kt). PresetsComponent is the canonical attach-only-component analog called out in [docs/agents/dependency-injection.md](../../agents/dependency-injection.md).
- App-level composition (`AttachApplicationComponent : Attachable` that merges children's `attachable.attach()` Closeables) → [`app/.../activity/AttachActivityComponent.kt`](../../../app/src/main/java/com/hluhovskyi/zero/activity/AttachActivityComponent.kt). One-for-one analog, just at the application layer instead of the activity layer.

**DSN flows entirely inside `zero-crash`.** `zero-crash/build.gradle` gets its own `buildConfigField "String", "SENTRY_DSN", ...` block reading from `System.getenv("SENTRY_DSN") ?: localProps['sentryDsn']`. The crash module reads `BuildConfig.SENTRY_DSN` from its own generated `BuildConfig` class inside the `@Provides`. The `app` module does **not** get a `SENTRY_DSN` field, does not call `.dsn(...)` on the Builder, and has no compile-time reference to the DSN string.

**Debug gate also reads from `zero-crash`'s own `BuildConfig.DEBUG`.** AGP builds library variants matching the consuming app's variant, so `zero-crash.BuildConfig.DEBUG` is `true` when the app is built debug and `false` when release. No need to thread `BuildConfig.DEBUG` through the Builder either.

**The only Builder param is `versionName`** — that's genuinely the app's identity (set from `version.properties` → AGP → `app/.../BuildConfig.VERSION_NAME`), not a crash-module concern, so it remains a `@BindsInstance` set by the caller.

**Component surface:**

```kotlin
@CrashScope
@dagger.Component(
    modules = [CrashComponent.Module::class],
    dependencies = [CrashComponent.Dependencies::class],
)
abstract class CrashComponent {

    abstract val attachable: Attachable

    interface Dependencies {
        val application: Application
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCrashComponent.builder()
            .dependencies(dependencies)
            .versionName("")
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CrashComponent> {
        fun dependencies(dependencies: Dependencies): Builder
        @BindsInstance fun versionName(versionName: String): Builder
    }

    @dagger.Module
    object Module {
        @Provides
        @CrashScope
        internal fun attachable(
            application: Application,
            versionName: String,
        ): Attachable {
            if (BuildConfig.DEBUG || BuildConfig.SENTRY_DSN.isBlank()) {
                return Attachable.Noop
            }
            return CrashAttachable(application, BuildConfig.SENTRY_DSN, versionName)
        }
    }
}

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

`Buildable<CrashComponent>` reused for consistency with PresetsComponent (which uses `Buildable<PresetsComponent>`). `Buildable` lives in `zero-api`, so `zero-crash` declares `implementation(project(":zero-api"))` — accepted for the `Attachable`/`Closeables`/`Buildable` types from `com.hluhovskyi.zero.common`.

**App-side wiring:**

Two changes to `ApplicationComponent`:

1. Add `val application: Application` to `ApplicationComponent.Dependencies` (sibling of the existing `val context: Context`). The Application instance is the same object — `context` stays because other modules (`imageLoader`, `androidUriResourceFactory`, `exportWriter`) already wire against `Context` and changing them is out of scope.
2. Add `CrashComponent.Dependencies` to `ApplicationComponent`'s superinterfaces and `abstract val attachable: Attachable` to its abstract surface.

A new `AttachApplicationComponent` class (analog of `AttachActivityComponent`) composes children, currently just `CrashComponent`:

```kotlin
class AttachApplicationComponent(
    private val crashComponent: CrashComponent,
) : Attachable {

    override fun attach(): Closeable = Closeables.merge(
        crashComponent.attachable.attach(),
    )
}
```

`ApplicationComponent.Module` gets two new `@Provides` (in a new `CrashModule` block at the bottom of the file, mirroring `RemoteModule`):

```kotlin
@Provides @ApplicationScope
fun crashComponent(component: ApplicationComponent): CrashComponent =
    CrashComponent.builder(component)
        .versionName(BuildConfig.VERSION_NAME)
        .build()

@Provides @ApplicationScope
fun attachable(crashComponent: CrashComponent): Attachable =
    AttachApplicationComponent(crashComponent)
```

**Bootstrapping in `MainApplication.onCreate`:**

```kotlin
applicationComponent.attachable.attach()
```

The returned `Closeable` is intentionally discarded — the process lives until killed, and `SentryAndroid.init` mutates global SDK state that we don't unwind. This is the same shape as `MainActivity.AttachWithView()` at the activity level (which DOES dispose on view destruction) — at the application level there's no destruction signal to honor.

**SDK auto-init disabled** via `<meta-data android:name="io.sentry.auto-init" android:value="false"/>` in `zero-crash`'s own `AndroidManifest.xml` (merged into the app manifest at build time). The module is self-contained — any consumer that adds `zero-crash` inherits the gate.

## Configuration & Secrets

DSN flows like `FEEDBACK_ENDPOINT` (see `docs/agents/feedback-infra.md`) **but the BuildConfig field lives in `zero-crash`, not `app`**:

| Where | Source |
|---|---|
| Local dev | `local.gradle.properties` → `sentryDsn=https://...` (gitignored, read by `zero-crash/build.gradle`) |
| CD release builds | `${{ vars.SENTRY_DSN }}` in `.github/workflows/cd.yml` (GitHub Variable, not Secret — DSNs ship in the AAB) |

`zero-crash/build.gradle` carries the `localProps` boilerplate (a copy of the block at `app/build.gradle:10-12`) and the matching `buildConfigField` line.

No new secret is needed (no Sentry Gradle plugin → no `SENTRY_AUTH_TOKEN`). If mapping upload is ever required (i.e. when minification is enabled), that adds the plugin + the auth token; out of scope here.

## CI / CD Impact

Only `cd.yml` changes: one new line in the `env:` block of `Build release AAB`. No new step, no new tooling. CI (`ci.yml`) is unaffected — debug builds skip Sentry, so unit tests don't need the DSN.

## Risk / Mitigation

- **Crash loop burning quota.** Sentry's free tier caps at 5k errors/month. A release with a startup crash could exhaust this in hours. Mitigation: the SDK includes a default session-rate-limit; we accept the residual risk for v1 and add a quota alert in the Sentry UI post-merge.
- **Vendor lock-in.** All Sentry knowledge is inside `zero-crash`. Swapping vendors means a new `*Attachable` impl in the same module; nothing else changes.
- **PII in stack traces.** Stack traces don't carry transaction data unless an exception is thrown from inside a code path operating on user money. Defaults are sufficient; we won't enable `attachStackTrace` for non-exception events.

## Verification (test plan)

- Unit tests / lint: green.
- Local release build with a deliberately thrown exception → confirm it appears in the Sentry project dashboard within ~1 minute.
- Local debug build with the same exception → confirm **nothing** appears in Sentry (debug gate works).
- Release AAB built via CD → confirm a `Sentry.captureMessage("CI smoke test")` invocation (run manually one time post-merge) lands in the dashboard with `release=1.0.<runNumber>`.

UI inspector is not applicable (no UI changes).
