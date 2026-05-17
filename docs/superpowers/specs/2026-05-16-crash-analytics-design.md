# Crash Analytics — Sentry Integration

## Problem

The app currently has no automatic crash reporting. Crashes in production are invisible unless a user manually files an issue via the existing feedback pipe (which only fires when the user notices something is wrong and chooses to report it). Result: silent crashes go untracked; regressions are discovered late, if at all.

## Goals

- Capture uncaught exceptions from release builds and report them to a hosted backend.
- Tag reports with `versionName` so regressions can be tied to a specific release.
- Zero impact on debug builds (no telemetry during development).
- Zero new permissions; zero new user-facing UI.
- Encapsulate the Sentry SDK behind an interface so the rest of the codebase has no compile-time knowledge of the vendor.

## Non-Goals

- Performance / transaction tracing.
- ANR detection (deferred — Sentry supports it but it adds complexity; not part of v1).
- ProGuard mapping upload (the app currently ships with `minifyEnabled false`; stack traces are already symbolicated).
- Timber → breadcrumbs bridge (`sentry-android-timber`) — deferred. Possible follow-up once we have a release crash to debug.
- User-facing opt-in/out toggle in Settings. The app is a personal-finance tool with a small audience; "always on for release builds" is the chosen posture.
- GitHub auto-issue integration. Configured in the Sentry web UI post-merge; no code change required.
- Capture/breadcrumb call sites in feature code. v1 only handles uncaught exceptions; `captureException`/`addBreadcrumb` can be added to the interface later when a caller needs them.

## Choice — Why Sentry

Picked over Firebase Crashlytics and Bugsnag:

- **No Google ecosystem footprint added.** Crashlytics would force `google-services` plugin + `google-services.json` + Firebase BoM (~method-count and APK-size hit). Sentry is a single SDK jar and a DSN string.
- **Privacy posture matches a finance app.** Sentry has explicit opt-out for PII collection; data goes to a single vendor, not the broader Google telemetry surface.
- **Single dashboard if iOS ever lands.** Sentry unifies platforms under one project; Crashlytics splits per Firebase app.
- **First-class GitHub integration.** Auto-create + bidirectional resolve in `hluhovskyi/zero` once enabled in the Sentry UI.
- **Cost.** Free Developer tier (5k errors/mo) is well above expected volume for this app.

## Architecture

**New Gradle module: `zero-crash`.** Structural analog: [`zero-image-loading`](../../../zero-image-loading) — same shape (one interface, one real impl, one no-op impl, no dependencies on other `zero-*` modules, Android library plugin). The app module gains a single `implementation(project(":zero-crash"))` line and stops importing Sentry directly.

Module surface (all in package `com.hluhovskyi.zero`, matching `zero-image-loading`'s flat-namespace convention):

```
zero-crash/
  src/main/java/com/hluhovskyi/zero/
    CrashReporter.kt        // interface + companion factories (.sentry(), .noop())
    SentryCrashReporter.kt  // internal object — calls SentryAndroid.init
    NoopCrashReporter.kt    // internal object — does nothing
  src/main/AndroidManifest.xml  // disables Sentry's auto-init ContentProvider
```

```kotlin
interface CrashReporter {
    fun init(application: Application, dsn: String, versionName: String)

    companion object {
        fun sentry(): CrashReporter = SentryCrashReporter
        fun noop(): CrashReporter = NoopCrashReporter
    }
}
```

**Why an interface for one real implementation:** matches `ImageLoader`'s pattern, gives `MainApplication` a single call-site whose meaning is unchanged when the vendor changes, and makes the debug/release split a one-line ternary instead of an `if/else` on `BuildConfig.DEBUG` wrapping the entire init block. No tests are planned against the interface — this is encapsulation, not testability.

**App-side wiring (in `MainApplication.onCreate`):**

```kotlin
val crashReporter = if (BuildConfig.DEBUG) CrashReporter.noop() else CrashReporter.sentry()
crashReporter.init(
    application = this,
    dsn = BuildConfig.SENTRY_DSN,
    versionName = BuildConfig.VERSION_NAME,
)
```

**Init guard inside `SentryCrashReporter`:** skip the SDK init if `dsn.isBlank()` so a release build that accidentally ships without `SENTRY_DSN` fails silent instead of crashing on a misconfigured DSN.

**SDK auto-init disabled** via `<meta-data android:name="io.sentry.auto-init" android:value="false"/>` in `zero-crash`'s own `AndroidManifest.xml` (merged into the app manifest at build time). Keeping it in the module manifest is what makes the module self-contained: any consumer that adds `zero-crash` inherits the gate.

**Tags applied:**
- `release` = `BuildConfig.VERSION_NAME` (e.g. `1.0.123`) — required for Sentry's release-grouping.
- `environment` = `"production"` (only release builds reach `Sentry.init`; "production" is the truthful label).

Nothing else is tagged. No user IDs, no device IDs beyond Sentry's defaults.

**No DI binding.** `CrashReporter` is constructed in `MainApplication.onCreate()` and never referenced elsewhere in v1. Adding a Dagger binding for an object only used in one place is ceremony.

## Configuration & Secrets

DSN flows exactly like `FEEDBACK_ENDPOINT` (see `docs/agents/feedback-infra.md`):

| Where | Source |
|---|---|
| Local dev | `local.gradle.properties` → `sentryDsn=https://...` (gitignored) |
| CD release builds | `${{ vars.SENTRY_DSN }}` in `.github/workflows/cd.yml` (GitHub Variable, not Secret — DSNs ship in the AAB) |

Exposed to code via a new `BuildConfig.SENTRY_DSN` field in `app/build.gradle`, fed by `System.getenv("SENTRY_DSN") ?: localProps['sentryDsn']`. The field stays in the **app** module (not `zero-crash`) because the env/localProps plumbing is already there for `FEEDBACK_ENDPOINT`; `zero-crash` takes the DSN as a parameter and has no opinion on where it came from.

No new secret is needed (no Sentry Gradle plugin → no `SENTRY_AUTH_TOKEN`). If mapping upload is ever required (i.e. when minification is enabled), that adds the plugin + the auth token; out of scope here.

## CI / CD Impact

Only `cd.yml` changes: one new line in the `env:` block of `Build release AAB`. No new step, no new tooling. CI (`ci.yml`) is unaffected — debug builds skip Sentry, so unit tests don't need the DSN.

## Risk / Mitigation

- **Crash loop burning quota.** Sentry's free tier caps at 5k errors/month. A release with a startup crash could exhaust this in hours. Mitigation: the SDK includes a default session-rate-limit; we accept the residual risk for v1 and add a quota alert in the Sentry UI post-merge.
- **Vendor lock-in.** The `CrashReporter` interface is the abstraction boundary. Swapping Sentry for another vendor means a new `*CrashReporter` impl in the same module; no changes elsewhere in the codebase.
- **PII in stack traces.** Stack traces don't carry transaction data unless an exception is thrown from inside a code path operating on user money. Defaults are sufficient; we won't enable `attachStackTrace` for non-exception events.

## Verification (test plan)

- Unit tests / lint: green.
- Local release build with a deliberately thrown exception → confirm it appears in the Sentry project dashboard within ~1 minute.
- Local debug build with the same exception → confirm **nothing** appears in Sentry (debug gate works).
- Release AAB built via CD → confirm a `Sentry.captureMessage("CI smoke test")` invocation (run manually one time post-merge) lands in the dashboard with `release=1.0.<runNumber>`.

UI inspector is not applicable (no UI changes).
