# Crash Analytics — Sentry Integration

## Problem

The app currently has no automatic crash reporting. Crashes in production are invisible unless a user manually files an issue via the existing feedback pipe (which only fires when the user notices something is wrong and chooses to report it). Result: silent crashes go untracked; regressions are discovered late, if at all.

## Goals

- Capture uncaught exceptions from release builds and report them to a hosted backend.
- Tag reports with `versionName` so regressions can be tied to a specific release.
- Zero impact on debug builds (no telemetry during development).
- Zero new permissions; zero new user-facing UI.
- Encapsulate the Sentry SDK behind a Dagger component so the rest of the codebase has no compile-time knowledge of the vendor, and so init is driven by the DI graph, not by an ad-hoc call in `MainApplication.onCreate`.

## Non-Goals

- Performance / transaction tracing.
- ANR detection (deferred — Sentry supports it but it adds complexity; not part of v1).
- ProGuard mapping upload (the app currently ships with `minifyEnabled false`; stack traces are already symbolicated).
- Timber → breadcrumbs bridge (`sentry-android-timber`) — deferred. Possible follow-up once we have a release crash to debug.
- User-facing opt-in/out toggle in Settings. The app is a personal-finance tool with a small audience; "always on for release builds" is the chosen posture.
- GitHub auto-issue integration. Configured in the Sentry web UI post-merge; no code change required.
- Capture/breadcrumb call sites in feature code. v1 only handles uncaught exceptions; `captureException`/`addBreadcrumb` can be added to the `CrashReporter` interface later when a caller needs them.

## Choice — Why Sentry

Picked over Firebase Crashlytics and Bugsnag:

- **No Google ecosystem footprint added.** Crashlytics would force `google-services` plugin + `google-services.json` + Firebase BoM (~method-count and APK-size hit). Sentry is a single SDK jar and a DSN string.
- **Privacy posture matches a finance app.** Sentry has explicit opt-out for PII collection; data goes to a single vendor, not the broader Google telemetry surface.
- **Single dashboard if iOS ever lands.** Sentry unifies platforms under one project; Crashlytics splits per Firebase app.
- **First-class GitHub integration.** Auto-create + bidirectional resolve in `hluhovskyi/zero` once enabled in the Sentry UI.
- **Cost.** Free Developer tier (5k errors/mo) is well above expected volume for this app.

## Architecture

**New Gradle module: `zero-crash`.** Structural shape borrows from two places:
- Module skeleton (`build.gradle`, manifest, proguard files, AGENTS.md) → `zero-image-loading`.
- Dagger surface (file-private `@Scope` and `@Qualifier` annotations, `Dependencies` interface, `@Component.Builder` with `@BindsInstance` setters, companion `builder(deps)` factory pre-setting defaults) → [`zero-remote/.../RemoteComponent.kt`](../../../zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt). RemoteComponent is the closest analog because both components are built once at the application layer, take a parent `Dependencies`, and receive runtime config (URLs, flags) via `@BindsInstance`.

Module surface (all in package `com.hluhovskyi.zero`, namespace `com.hluhovskyi.zero.crash`):

```
zero-crash/
  src/main/AndroidManifest.xml          // disables Sentry's auto-init ContentProvider
  src/main/java/com/hluhovskyi/zero/
    CrashReporter.kt                    // marker interface
    SentryCrashReporter.kt              // internal object; Sentry impl
    NoopCrashReporter.kt                // internal object; debug-build impl
    CrashComponent.kt                   // Dagger component + Module + Builder
```

**Component shape (mirrors `RemoteComponent`):**

```kotlin
@CrashScope
@dagger.Component(
    modules = [CrashComponent.Module::class],
    dependencies = [CrashComponent.Dependencies::class],
)
interface CrashComponent {

    val crashReporter: CrashReporter

    interface Dependencies {
        val application: Application
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCrashComponent.builder()
            .dependencies(dependencies)
            .dsn("")
            .versionName("")
            .debug(true)
    }

    @dagger.Component.Builder
    interface Builder {
        fun dependencies(dependencies: Dependencies): Builder
        @BindsInstance fun dsn(@Dsn dsn: String): Builder
        @BindsInstance fun versionName(@VersionName versionName: String): Builder
        @BindsInstance fun debug(debug: Boolean): Builder
        fun build(): CrashComponent
    }

    @dagger.Module
    object Module {
        @Provides
        @CrashScope
        internal fun crashReporter(
            application: Application,
            @Dsn dsn: String,
            @VersionName versionName: String,
            debug: Boolean,
        ): CrashReporter {
            if (debug || dsn.isBlank()) {
                return NoopCrashReporter
            }
            SentryAndroid.init(application) { options ->
                options.dsn = dsn
                options.release = versionName
                options.environment = "production"
            }
            return SentryCrashReporter
        }
    }
}
```

`@CrashScope`, `@Dsn`, `@VersionName` declared `private` at file scope (matches RemoteComponent). No `@Qualifier` is needed for `Boolean` (only one Boolean binding in the graph). The Builder pre-sets defaults so that any consumer who forgets a setter still builds — the `debug = true` default falls through to `NoopCrashReporter`, which is the safe choice.

`CrashReporter` itself is a **marker interface** in v1 — no methods. The init side effect happens inside the `@Provides` function, not via a method on the interface. Once v2 needs `captureException(t: Throwable)` or `addBreadcrumb(...)`, those methods land on `CrashReporter` and both impls implement them.

**App-side wiring (in `ApplicationComponent`):**

Two changes to the existing `ApplicationComponent`:

1. Add `val application: Application` to `ApplicationComponent.Dependencies` (sibling of the existing `val context: Context`). The Application instance is the same object — `context` stays because other modules (`imageLoader`, `androidUriResourceFactory`, `exportWriter`) already wire against `Context` and changing them is out of scope.
2. Add `CrashComponent.Dependencies` to `ApplicationComponent`'s superinterfaces and `abstract val crashReporter: CrashReporter` to its abstract surface.

Then in `ApplicationComponent.Module`, two new `@Provides` functions mirroring the existing `remoteComponent` / `feedbackService` pair:

```kotlin
@Provides @ApplicationScope
fun crashComponent(component: ApplicationComponent): CrashComponent =
    CrashComponent.builder(component)
        .dsn(BuildConfig.SENTRY_DSN)
        .versionName(BuildConfig.VERSION_NAME)
        .debug(BuildConfig.DEBUG)
        .build()

@Provides @ApplicationScope
fun crashReporter(crashComponent: CrashComponent): CrashReporter =
    crashComponent.crashReporter
```

**Bootstrapping in `MainApplication.onCreate`:** Dagger lazy-initializes scoped bindings on first access, so the side effect inside the `@Provides` doesn't fire until something touches `crashReporter`. To force init at app startup, `MainApplication.onCreate` accesses `applicationComponent.crashReporter` once — the value is discarded, but the access materializes the binding and runs `SentryAndroid.init`. Same one-line cost as the previous design, but the call site no longer knows about Sentry, debug flags, or DSN strings.

**SDK auto-init disabled** via `<meta-data android:name="io.sentry.auto-init" android:value="false"/>` in `zero-crash`'s own `AndroidManifest.xml` (merged into the app manifest at build time). The module is self-contained — any consumer that adds `zero-crash` inherits the gate.

## Configuration & Secrets

DSN flows exactly like `FEEDBACK_ENDPOINT` (see `docs/agents/feedback-infra.md`):

| Where | Source |
|---|---|
| Local dev | `local.gradle.properties` → `sentryDsn=https://...` (gitignored) |
| CD release builds | `${{ vars.SENTRY_DSN }}` in `.github/workflows/cd.yml` (GitHub Variable, not Secret — DSNs ship in the AAB) |

Exposed to code via a new `BuildConfig.SENTRY_DSN` field in `app/build.gradle`, fed by `System.getenv("SENTRY_DSN") ?: localProps['sentryDsn']`. The field stays in the **app** module (not `zero-crash`) because the env/localProps plumbing is already there for `FEEDBACK_ENDPOINT`; `zero-crash` takes the DSN as a `@BindsInstance` parameter and has no opinion on where it came from.

No new secret is needed (no Sentry Gradle plugin → no `SENTRY_AUTH_TOKEN`). If mapping upload is ever required (i.e. when minification is enabled), that adds the plugin + the auth token; out of scope here.

## CI / CD Impact

Only `cd.yml` changes: one new line in the `env:` block of `Build release AAB`. No new step, no new tooling. CI (`ci.yml`) is unaffected — debug builds skip Sentry, so unit tests don't need the DSN.

## Risk / Mitigation

- **Crash loop burning quota.** Sentry's free tier caps at 5k errors/month. A release with a startup crash could exhaust this in hours. Mitigation: the SDK includes a default session-rate-limit; we accept the residual risk for v1 and add a quota alert in the Sentry UI post-merge.
- **Vendor lock-in.** The `CrashReporter` interface plus the `CrashComponent` boundary localize all Sentry knowledge to one module. Swapping vendors means a new impl + a swap inside the `@Provides`; no changes elsewhere.
- **PII in stack traces.** Stack traces don't carry transaction data unless an exception is thrown from inside a code path operating on user money. Defaults are sufficient; we won't enable `attachStackTrace` for non-exception events.

## Verification (test plan)

- Unit tests / lint: green.
- Local release build with a deliberately thrown exception → confirm it appears in the Sentry project dashboard within ~1 minute.
- Local debug build with the same exception → confirm **nothing** appears in Sentry (debug gate works).
- Release AAB built via CD → confirm a `Sentry.captureMessage("CI smoke test")` invocation (run manually one time post-merge) lands in the dashboard with `release=1.0.<runNumber>`.

UI inspector is not applicable (no UI changes).
