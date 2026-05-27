# Live Currency Conversion via Frankfurter (with bundled overrides)

## Problem

All FX rates currently come from bundled JSON assets read by `PredefinedCurrencyLoader`
(`app/.../currencies/`), a snapshot dated `2022-04-04` with 208 rates per base. These are
stale. We want **live** rates from [Frankfurter](https://frankfurter.dev/)
(`https://api.frankfurter.dev/v1`, free, no API key, ECB-based, refreshed on banking days)
while **keeping the bundled rates** so currencies Frankfurter does not cover — notably crypto
like `BTC`, plus exotic fiat — still convert. The user calls these the "custom overrides".

Frankfurter `/v1/latest?base=EUR` returns `{ "base": "EUR", "date": "...", "rates": { "USD": 1.16, ... } }`
covering ~31 fiat currencies. It has **no crypto** and updates once per banking day (weekends/holidays
return the last working day's table).

## Approach

A **layered loader** with smart, minimal fetching:

- **One fetch per calendar day.** `/v1/latest` (base = EUR, ECB's native base) returns the entire
  fiat table in a single call. Any pair is derived locally by cross-rate:
  `rate(A→B) = table[B] / table[A]`. So we never fetch per-currency — one request covers everything.
- **Persisted.** The snapshot is serialized and stored via `ConfigurationRepository` (Room/SQLite),
  so restarts within a day reuse it and offline sessions fall back to the last persisted (stale)
  snapshot. SQLite writes are ACID — no torn-write window to guard against. (A dedicated rates table
  is a possible later step if we go historical/multi-date.)
- **Bundled fallback.** Bundled rates are merged underneath the live table; **live fiat wins on
  overlap, while keys Frankfurter omits (crypto/exotic) keep their bundled values**. No snapshot at
  all (first run offline) → bundled-only, i.e. today's behavior. No crash, no UI change.
- **Latest-rate semantics (for now).** A transaction's converted value uses the latest daily rate,
  matching today's single-snapshot model. Transaction-date/historical rates are explicitly out of
  scope.

The network call lives in `zero-remote` behind a `zero-api` interface, implemented with **Retrofit
kept fully internal** to the module — `RemoteComponent` exposes only the `zero-api` interface, never
Retrofit/OkHttp/serialization types (enforced by the `RemoteComponentEncapsulation` lint rule, which
now also forbids `retrofit2.*`). The daily-cache + persistence + cross-rate orchestration lives in
`app`.

## Components

| Type | Module | Template analog | Responsibility |
|------|--------|-----------------|----------------|
| `ExchangeRateService` + `ExchangeRateSnapshot` (+ `Noop`) | zero-api `currencies/` | `FeedbackService` | `suspend fun latest(): ExchangeRateSnapshot?` — the full base-relative table, or `null` when unavailable. |
| `FrankfurterApi` (`internal`) | zero-remote `currencies/` | — | Retrofit `@GET("v1/latest")` with `base=EUR` default. |
| `RetrofitExchangeRateService` (`internal`) | zero-remote `currencies/` | `OkHttpFeedbackService` | Calls the API, maps to `ExchangeRateSnapshot`; `IOException`/`HttpException` → `null` + `Timber.w`. |
| `FrankfurterLatestResponse` (`internal`, `@Serializable`) | zero-remote `currencies/` | `FeedbackResponse` | HTTP body shape: `base`, `date`, `rates: Map<String, Double>`. |
| `RateSnapshotStore` (`internal`) | app `currencies/` | — | Persists `{ fetchedOn, base, rates }` (serialized) via `ConfigurationRepository` under `CurrencyConfigurationKey.RateSnapshot`; blank/corrupt → `null`. |
| `CompositeCurrencyLoader` (`internal`) | app `currencies/` | `PredefinedCurrencyLoader` | Refresh ≤ once/day (via `ZonedClock`) + persist + cross-rate + merge over bundled `delegate`. |

**DI changes (mirror `feedbackService` wiring):**
- `RemoteComponent` exposes `val exchangeRateService: ExchangeRateService`; provides an internal
  `FrankfurterApi` (Retrofit built from the OkHttp client + kotlinx-serialization converter) and wraps
  it in `RetrofitExchangeRateService`. The base URL is a public non-secret default bound via
  `@BindsInstance exchangeRateEndpoint`, defaulted to `https://api.frankfurter.dev/`.
- `app` applies the `kotlin-serialization` plugin (for `RateSnapshotStore.Stored`).
- `ApplicationComponent.currencyLoader` builds `CompositeCurrencyLoader(PredefinedCurrencyLoader(...),
  exchangeRateService, RateSnapshotStore(configurationRepository), zonedClock)`.

## Data Flow

`CurrencyConvertUseCase.getRate(from, to)` → `CurrencyLoader.ratesFor(from)` →
`CompositeCurrencyLoader`:
1. `currentSnapshot()` — if already refreshed today, reuse; else (under a mutex) load the persisted
   snapshot if it's today's, otherwise fetch `latest()`, persist, and cache. On fetch failure, reuse
   the stale persisted snapshot if any.
2. Cross-rate the snapshot to base `from` (`out[T] = table[T] / table[from]`, plus `from→base` and
   `from→from = 1`). If `from` isn't in the table and isn't the base (e.g. crypto base) → empty live.
3. Merge `bundled + live` (live wins), cache per `from` for the day.

`USD → BTC`: live cross-rates have no `BTC`, so the bundled `BTC` rate survives. ✅
`USD → EUR`: live `EUR` (= 1 / table[USD]) overrides the stale bundled `EUR`. ✅
Day rollover: `currentSnapshot()` clears the per-currency cache and refetches. ✅
Offline: stale persisted snapshot, then bundled. ✅

`availableCurrencies()` delegates unchanged to the predefined loader (the 219-currency superset).

## Error Handling

Network error / non-200 → `null` from the service (`Timber.w` in zero-remote; transport distinctions
never leak through the public API, per zero-remote rule 4). Corrupt/missing cache file → `null` from
the store. The composite loader degrades gracefully: live → stale-persisted → bundled.

## Caching

- **In-memory:** current snapshot + the day it was refreshed; per-currency cross-rate map.
- **Persistent:** the latest snapshot + its fetch day, serialized into `ConfigurationRepository`
  (Room/SQLite — ACID, so no partial-write corruption). The config `observe`/`get` queries key on
  scope+name only (not user), so the snapshot behaves as a single global cached value; only the
  write needs a current user (to fill `userId`), which always exists by the time conversions run.
- **Policy:** at most one network attempt per local calendar day; offline serves the last persisted
  snapshot. **Out of scope / follow-up:** intra-day refresh, retry-on-reconnect, eviction.

## Testing

- `RetrofitExchangeRateServiceTest` (MockWebServer): happy path maps the snapshot and requests
  `/v1/latest?base=EUR`; 500 → null; socket reset → null.
- `RateSnapshotStoreTest` (fake `ConfigurationRepository`): save→load round-trip; unset → null;
  corrupt value → null; save overwrites previous.
- `CompositeCurrencyLoaderTest`: live cross-rates override bundled while crypto survives; null
  snapshot → bundled only; fetch ≤ once/day across currencies; new day → refetch with fresh rates;
  today's persisted snapshot reused without fetching; stale persisted snapshot used when today's fetch
  fails; `availableCurrencies` delegates.

## Out of Scope

Transaction-date/historical rates, intra-day refresh policy, retry-on-reconnect, user-editable custom
rates, changing the `availableCurrencies` set, and a dedicated `exchange_rates` Room table (a possible
later step over the current `ConfigurationRepository` storage).
