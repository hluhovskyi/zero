# zero-crash — Agent Guide

Android library module. Sentry-backed crash reporting, exposed to the rest of the app as a Dagger component with an `Attachable` lifecycle hook.

## Rules

1. **One zero-* dep only** — `zero-api` for `Attachable`, `Closeables`, `Buildable`. No other zero-* modules.
2. **DSN lives here, not in app** — `SENTRY_DSN` is a `buildConfigField` of this module, read by env var or `local.gradle.properties → sentryDsn=...`. The app module has no knowledge of the DSN.
3. **Init runs inside `attach()`, not in a `@Provides`** — the `@Provides` builds an `Attachable`; the `Attachable.attach()` call (made by `AttachApplicationComponent`) is what actually invokes `SentryAndroid.init`. Don't move init into the `@Provides` body.
4. **Don't touch the manifest meta-data** — `<meta-data android:name="io.sentry.auto-init" .../>` disables Sentry's auto-init `ContentProvider` so init is driven explicitly by the `Attachable`. Removing it causes double-init.

## What Lives Here

- `CrashComponent` — Dagger component, surface is `abstract val attachable: Attachable`. Built once at the application layer (`ApplicationComponent.Module → crashComponent(...)`).
- `CrashAttachable` (file-private to `CrashComponent.kt`) — calls `SentryAndroid.init(application)` with `release = versionName`, `environment = "production"`.
- Debug gate: `BuildConfig.DEBUG || BuildConfig.SENTRY_DSN.isBlank()` returns `Attachable.Noop`. AGP builds this module's variant to match the consuming app's variant, so `BuildConfig.DEBUG` here is the truthful "is the app being built debug" signal.
