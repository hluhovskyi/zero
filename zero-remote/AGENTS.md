# zero-remote — Agent Guide

Android library module. Server-side calls (feedback submission to a GCP Cloud Function gated by Play Integrity; live exchange rates from Frankfurter).

## Rules

1. **Depends only on `zero-api`** — implements interfaces defined there. Cannot import from `zero-core`, `app`, or any other module.
2. **Hard Encapsulation**: `RemoteComponent` MUST NOT expose any of the following in its public methods or fields:
   - Types from `okhttp3.*` or `retrofit2.*`
   - Types from `com.google.android.play.core.integrity.*` or `com.google.android.play.integrity.*`
   - Types from `kotlinx.serialization.json.*`
   - Any class declared `internal` to this module
   Lint rule `RemoteComponentEncapsulation` enforces this.
3. **All HTTP, Retrofit, JSON, and Play Integrity types are `internal`.** Only `zero-api` interfaces leak out.
4. **Failures collapse to a single `FeedbackSubmitResult.Failure`.** Transport-level distinctions (network error, 401, 500, missing config, Integrity unavailable) are diagnosed via `Timber.w` logs inside the implementation, never via the public API.

## What Lives Here

- `RemoteComponent` — public Dagger component, the only entry point
- `feedback/` — `OkHttpFeedbackService` (impl) + `FeedbackRequest` / `FeedbackResponse` (HTTP body shapes)
- `integrity/` — `IntegrityTokenProvider` interface + `PlayIntegrityTokenProvider` impl (wraps Google's `StandardIntegrityManager`)
- `currencies/` — `RetrofitExchangeRateService` (impl of `ExchangeRateService`) + `FrankfurterApi` (Retrofit `@GET` interface) + `FrankfurterLatestResponse` (HTTP body shape). `latest()` returns the whole EUR-based table in one call; the app cross-rates locally. Frankfurter covers ~31 fiat currencies only — no crypto; failures return `null`, and the app's `CompositeCurrencyLoader` falls back to its persisted snapshot, then bundled rates.
