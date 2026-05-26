# Live Currency Conversion via Frankfurter (with bundled overrides)

## Problem

All FX rates currently come from bundled JSON assets read by `PredefinedCurrencyLoader`
(`app/.../currencies/`), a snapshot dated `2022-04-04` with 208 rates per base. These are
stale. We want **live** rates from [Frankfurter](https://frankfurter.dev/)
(`https://api.frankfurter.dev/v1`, free, no API key, ECB-based, refreshed on banking days)
while **keeping the bundled rates** so currencies Frankfurter does not cover — notably crypto
like `BTC`, plus exotic fiat — still convert. The user calls these the "custom overrides".

Frankfurter `/v1/latest?base=USD` returns `{ "base": "USD", "date": "...", "rates": { "EUR": 0.86, ... } }`
covering ~31 fiat currencies. It has **no crypto** and 404s/empties for unsupported bases.

## Approach

A **layered loader**. Frankfurter supplies live fiat rates; the bundled assets remain the
fallback and the *sole* source for everything Frankfurter lacks (crypto + exotic fiat).

Merge rule: `bundled ∪ live`, **live wins on key overlap**. So fiat rates become fresh while
`BTC`/`ADA`/etc. keep flowing from the bundle. Any network failure or unsupported base →
empty live map → merged result is bundled-only, i.e. exactly today's behavior. No crash, no UI
change.

The network call lives in `zero-remote` behind a new `zero-api` interface, mirroring the
existing `FeedbackService` / `OkHttpFeedbackService` / `RemoteComponent` precedent. Orchestration
(merge + cache + fallback) lives in `app` as a new `CurrencyLoader` implementation.

## Components

| Type | Module | Template analog | Responsibility |
|------|--------|-----------------|----------------|
| `ExchangeRateService` (interface + `Noop`) | zero-api `currencies/` | `FeedbackService` | `suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate>`. `Noop` returns `emptyMap()`. |
| `OkHttpFrankfurterExchangeRateService` (`internal`) | zero-remote `currencies/` | `OkHttpFeedbackService` | GETs `<endpoint>/latest?base=<CODE>`, parses `rates`, maps to `Map<Id.Known, Rate>`. All failures (IO, non-200, blank endpoint) → `emptyMap` + `Timber.w`. |
| `FrankfurterLatestResponse` (`internal`, `@Serializable`) | zero-remote `currencies/` | `FeedbackResponse` | HTTP body shape: `base`, `date`, `rates: Map<String, Double>`. |
| `CompositeCurrencyLoader` (`internal`) | app `currencies/` | `PredefinedCurrencyLoader` | Wraps a delegate `CurrencyLoader` (predefined) + `ExchangeRateService`. Merges, caches per base, falls back. |

**DI changes (mirror `feedbackService` wiring exactly):**
- `RemoteComponent` exposes `val exchangeRateService: ExchangeRateService`; add `@BindsInstance exchangeRateEndpoint(@ExchangeRateEndpoint endpoint: String)` to its Builder, defaulted in `builder()` to the Frankfurter v1 base URL constant. The endpoint is a public, non-secret default (ships in the binary) — hardcode the constant; no `BuildConfig`/`local.properties` plumbing needed.
- `RemoteModule` (in `ApplicationComponent.kt`) adds `exchangeRateService(remoteComponent)` provider.
- `ApplicationComponent.currencyLoader` returns `CompositeCurrencyLoader(delegate = PredefinedCurrencyLoader(...), exchangeRateService = ...)` instead of the bare `PredefinedCurrencyLoader`.

The `RemoteComponentEncapsulation` lint rule forbids exposing `okhttp3.*` / serialization types
from `RemoteComponent` — exposing `ExchangeRateService` (a zero-api interface) is compliant.

## Data Flow

`CurrencyConvertUseCase.getRate(from, to)` → `CurrencyLoader.ratesFor(from)` →
`CompositeCurrencyLoader`:
1. `delegate.ratesFor(from)` → bundled map (e.g. USD→{EUR, BTC, ...}).
2. `exchangeRateService.ratesFor(from)` → live fiat map (e.g. USD→{EUR, ...}), or `emptyMap`.
3. Merge `bundled + live` (live overrides), cache per `from`, return.

`USD → BTC`: live map has no `BTC`, so the bundled `BTC` rate survives the merge. ✅
`USD → EUR`: live `EUR` overrides the stale bundled `EUR`. ✅
`base = BTC` (Frankfurter 404): live map empty → bundled-only. ✅

`availableCurrencies()` delegates unchanged to the predefined loader (the 219-currency superset;
Frankfurter's ~31 are a strict subset, so no union needed).

## Error Handling

Frankfurter unreachable / unsupported base / parse error → `emptyMap()` inside the service,
logged via `Timber.w` in zero-remote (transport distinctions never leak through the public API,
per zero-remote rule 4). Composite loader then yields bundled-only rates. No exceptions surface.

## Caching ("for now")

In-memory per-session cache keyed by base (`ConcurrentHashMap`, matching the existing
`PredefinedCurrencyLoader`). Rates do not refresh within a session — acceptable for now and
consistent with current behavior. **Out of scope / follow-up:** TTL, disk persistence, daily
refresh policy.

## Testing

- `OkHttpFrankfurterExchangeRateServiceTest` (MockWebServer, mirrors `OkHttpFeedbackServiceTest`):
  happy path parses `rates` into `Map<Id.Known, Rate>`; 500 → empty; socket reset → empty;
  blank endpoint → empty + zero requests; request URL carries `base` query param.
- `CompositeCurrencyLoaderTest`: live overlays bundled (fresh EUR wins); bundled-only keys (BTC)
  survive merge; empty live map → bundled only; second call for same base served from cache
  (delegate + service invoked once).

## Out of Scope

Persisting rates, TTL / refresh policy, historical rates, user-editable custom rates, and
changing the `availableCurrencies` set.
