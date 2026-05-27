# Frankfurter Currency Conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve live FX rates from Frankfurter (`api.frankfurter.dev/v1`) while keeping bundled rates as the fallback and the sole source for currencies Frankfurter omits (e.g. `BTC`).

**Architecture:** New `ExchangeRateService` interface (zero-api), an OkHttp implementation in zero-remote behind `RemoteComponent` (mirrors `FeedbackService`/`OkHttpFeedbackService`), and a `CompositeCurrencyLoader` in app that merges `bundled ∪ live` (live wins) over the existing `PredefinedCurrencyLoader`. Network failure or unsupported base → empty live map → bundled-only (today's behavior).

**Tech Stack:** Kotlin, OkHttp, kotlinx.serialization, Dagger, coroutines; JUnit + MockWebServer + mockito-kotlin for tests.

**Spec:** `docs/superpowers/specs/2026-05-26-frankfurter-currency-conversion-design.md`

> **Revision (post-review):** the tasks below describe the initial OkHttp + per-base + in-memory
> build. Following review the design was upgraded — see the spec for the current shape. Net changes:
> - `ExchangeRateService` now returns `ExchangeRateSnapshot?` from a single `latest()` (full EUR
>   table); pairs are cross-rated locally instead of one network call per base.
> - The zero-remote impl uses **Retrofit kept internal** (`FrankfurterApi` + `RetrofitExchangeRateService`);
>   `retrofit2.*` added to the `RemoteComponentEncapsulation` lint prefixes.
> - `CompositeCurrencyLoader` fetches **≤ once per calendar day** (via `ZonedClock`) and persists the
>   snapshot through a new `RateSnapshotStore` (JSON file), falling back stale-persisted → bundled.
> - `app` now applies the `kotlin-serialization` plugin.

---

### Task 1: `ExchangeRateService` contract in zero-api

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/currencies/ExchangeRateService.kt`

- [ ] **Step 1: Create the interface + Noop** (mirrors `FeedbackService` + the "every contract has a Noop" rule in `zero-api/AGENTS.md`)

```kotlin
package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate

/**
 * Source of live exchange rates for a base currency, keyed by target currency.
 * Returns an empty map when rates are unavailable (network failure, unsupported base);
 * callers treat empty as "no live rates" and fall back to their own source.
 */
interface ExchangeRateService {

    suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate>

    object Noop : ExchangeRateService {
        override suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate> = emptyMap()
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :zero-api:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/currencies/ExchangeRateService.kt
git commit -m "feat(currencies): add ExchangeRateService contract"
```

---

### Task 2: Frankfurter OkHttp implementation (TDD) in zero-remote

**Files:**
- Create: `zero-remote/src/main/java/com/hluhovskyi/zero/currencies/FrankfurterLatestResponse.kt`
- Create: `zero-remote/src/main/java/com/hluhovskyi/zero/currencies/OkHttpFrankfurterExchangeRateService.kt`
- Test: `zero-remote/src/test/java/com/hluhovskyi/zero/currencies/OkHttpFrankfurterExchangeRateServiceTest.kt`

zero-remote already has okhttp, kotlinx.serialization.json, coroutines, timber, and mockwebserver — no build.gradle change. Implementation classes are `internal` (zero-remote rule 3).

- [ ] **Step 1: Write the failing test** (mirrors `OkHttpFeedbackServiceTest`)

```kotlin
package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpFrankfurterExchangeRateServiceTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `happy path parses rates and queries base`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"base":"USD","date":"2026-05-26","rates":{"EUR":0.86,"GBP":0.74}}""",
            ),
        )
        val service = service(endpoint = server.url("/v1").toString())

        val rates = service.ratesFor(Id("USD"))

        assertEquals(Rate(0.86).value, rates.getValue(Id("EUR")).value)
        assertEquals(Rate(0.74).value, rates.getValue(Id("GBP")).value)
        val recorded = server.takeRequest()
        assertEquals("/v1/latest?base=USD", recorded.path)
    }

    @Test
    fun `blank endpoint returns empty without request`() = runTest {
        val rates = service(endpoint = "").ratesFor(Id("USD"))

        assertTrue(rates.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `500 returns empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody(""))

        val rates = service(endpoint = server.url("/v1").toString()).ratesFor(Id("USD"))

        assertTrue(rates.isEmpty())
    }

    @Test
    fun `socket reset returns empty`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val rates = service(endpoint = server.url("/v1").toString()).ratesFor(Id("USD"))

        assertTrue(rates.isEmpty())
    }

    private fun service(endpoint: String): OkHttpFrankfurterExchangeRateService =
        OkHttpFrankfurterExchangeRateService(endpoint = endpoint, client = client, json = json)
}
```

- [ ] **Step 2: Run test, verify it fails to compile / fails**

Run: `./gradlew :zero-remote:testDebugUnitTest --tests "*OkHttpFrankfurterExchangeRateServiceTest*" 2>&1 | tail -15`
Expected: FAIL (unresolved `OkHttpFrankfurterExchangeRateService` / `FrankfurterLatestResponse`)

- [ ] **Step 3: Create the response DTO**

```kotlin
package com.hluhovskyi.zero.currencies

import kotlinx.serialization.Serializable

@Serializable
internal data class FrankfurterLatestResponse(
    val base: String = "",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap(),
)
```

- [ ] **Step 4: Implement the service** (mirrors `OkHttpFeedbackService`: `withContext(IO)`, blank-endpoint guard, `.use {}`, IOException → failure value, Timber.w diagnostics)

```kotlin
package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

internal class OkHttpFrankfurterExchangeRateService(
    private val endpoint: String,
    private val client: OkHttpClient,
    private val json: Json,
) : ExchangeRateService {

    override suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate> = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) {
            Timber.w("OkHttpFrankfurterExchangeRateService: endpoint not configured")
            return@withContext emptyMap()
        }

        val url = endpoint.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("latest")
            ?.addQueryParameter("base", baseId.value)
            ?.build()
        if (url == null) {
            Timber.w("OkHttpFrankfurterExchangeRateService: invalid endpoint '$endpoint'")
            return@withContext emptyMap()
        }

        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 200) {
                    json.decodeFromString<FrankfurterLatestResponse>(body).rates
                        .entries
                        .associate { (code, rate) -> Id(code.uppercase()) to Rate(rate) }
                } else {
                    Timber.w("OkHttpFrankfurterExchangeRateService: server returned ${response.code}")
                    emptyMap()
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "OkHttpFrankfurterExchangeRateService: network error")
            emptyMap()
        }
    }
}
```

- [ ] **Step 5: Run tests, verify pass**

Run: `./gradlew :zero-remote:testDebugUnitTest --tests "*OkHttpFrankfurterExchangeRateServiceTest*" 2>&1 | tail -15`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add zero-remote/src/main/java/com/hluhovskyi/zero/currencies/ zero-remote/src/test/java/com/hluhovskyi/zero/currencies/
git commit -m "feat(remote): add Frankfurter exchange-rate service"
```

---

### Task 3: Expose `exchangeRateService` from `RemoteComponent`

**Files:**
- Modify: `zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt`

Mirror the `feedbackEndpoint` / `feedbackService` wiring. The endpoint is a public non-secret default that ships in the binary — hardcode the constant, no BuildConfig plumbing.

- [ ] **Step 1: Add the qualifier, exposed property, builder binding, default, and provider**

In `RemoteComponent.kt`:

1. Add a qualifier next to the existing ones:
```kotlin
@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class ExchangeRateEndpoint
```

2. Add to the component interface (next to `val feedbackService: FeedbackService`):
```kotlin
    val exchangeRateService: ExchangeRateService
```
and import `com.hluhovskyi.zero.currencies.ExchangeRateService`.

3. In `companion object`, add the constant and the default binding in `builder()`:
```kotlin
    companion object {

        private const val FRANKFURTER_ENDPOINT = "https://api.frankfurter.dev/v1"

        fun builder(dependencies: Dependencies): Builder = DaggerRemoteComponent.builder()
            .dependencies(dependencies)
            .feedbackEndpoint("")
            .integrityCloudProject(0L)
            .exchangeRateEndpoint(FRANKFURTER_ENDPOINT)
    }
```

4. In `Builder`, add:
```kotlin
        @BindsInstance
        fun exchangeRateEndpoint(@ExchangeRateEndpoint endpoint: String): Builder
```

5. In `Module`, add the provider:
```kotlin
        @Provides
        @RemoteScope
        internal fun exchangeRateService(
            @ExchangeRateEndpoint endpoint: String,
            client: OkHttpClient,
            json: Json,
        ): ExchangeRateService = OkHttpFrankfurterExchangeRateService(
            endpoint = endpoint,
            client = client,
            json = json,
        )
```

- [ ] **Step 2: Compile (Dagger codegen + lint encapsulation check)**

Run: `./gradlew :zero-remote:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (exposing the zero-api `ExchangeRateService` is allowed; `RemoteComponentEncapsulation` only forbids okhttp3/serialization/internal types)

- [ ] **Step 3: Commit**

```bash
git add zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt
git commit -m "feat(remote): expose exchangeRateService from RemoteComponent"
```

---

### Task 4: `CompositeCurrencyLoader` (TDD) in app

**Files:**
- Create: `app/src/main/java/com/hluhovskyi/zero/currencies/CompositeCurrencyLoader.kt`
- Test: `app/src/test/java/com/hluhovskyi/zero/currencies/CompositeCurrencyLoaderTest.kt`

`CurrencyLoader` is `internal` to app; the test lives in the same module so it can see it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeCurrencyLoaderTest {

    private val usd = Id("USD")
    private val eur = Id("EUR")
    private val btc = Id("BTC")

    private class FakeLoader(
        private val rates: Map<Id.Known, Rate>,
        private val available: Set<Id.Known> = emptySet(),
    ) : CurrencyLoader {
        var ratesCalls = 0
        override suspend fun availableCurrencies() = available
        override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
            ratesCalls++
            return rates
        }
    }

    private class FakeService(private val rates: Map<Id.Known, Rate>) : ExchangeRateService {
        var calls = 0
        override suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate> {
            calls++
            return rates
        }
    }

    @Test
    fun `live rates override bundled and bundled-only keys survive`() = runTest {
        val delegate = FakeLoader(mapOf(eur to Rate(0.80), btc to Rate(0.00002)))
        val service = FakeService(mapOf(eur to Rate(0.86)))
        val loader = CompositeCurrencyLoader(delegate, service)

        val rates = loader.ratesFor(usd)

        assertEquals(Rate(0.86).value, rates.getValue(eur).value) // live wins
        assertEquals(Rate(0.00002).value, rates.getValue(btc).value) // bundled-only survives
    }

    @Test
    fun `empty live map falls back to bundled only`() = runTest {
        val delegate = FakeLoader(mapOf(eur to Rate(0.80), btc to Rate(0.00002)))
        val loader = CompositeCurrencyLoader(delegate, FakeService(emptyMap()))

        val rates = loader.ratesFor(usd)

        assertEquals(mapOf(eur to Rate(0.80).value, btc to Rate(0.00002).value), rates.mapValues { it.value.value })
    }

    @Test
    fun `second call for same base is served from cache`() = runTest {
        val delegate = FakeLoader(mapOf(eur to Rate(0.80)))
        val service = FakeService(mapOf(eur to Rate(0.86)))
        val loader = CompositeCurrencyLoader(delegate, service)

        loader.ratesFor(usd)
        loader.ratesFor(usd)

        assertEquals(1, delegate.ratesCalls)
        assertEquals(1, service.calls)
    }

    @Test
    fun `availableCurrencies delegates`() = runTest {
        val delegate = FakeLoader(rates = emptyMap(), available = setOf(usd, eur))
        val loader = CompositeCurrencyLoader(delegate, FakeService(emptyMap()))

        assertEquals(setOf(usd, eur), loader.availableCurrencies())
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CompositeCurrencyLoaderTest*" 2>&1 | tail -15`
Expected: FAIL (unresolved `CompositeCurrencyLoader`)

- [ ] **Step 3: Implement** (cache pattern mirrors `PredefinedCurrencyLoader`'s `ConcurrentHashMap`)

```kotlin
package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import java.util.concurrent.ConcurrentHashMap

/**
 * Merges live rates from [exchangeRateService] over a bundled [delegate] loader.
 * Live rates win on key overlap; keys the live source omits (e.g. crypto) keep their
 * bundled values. An empty live map (network failure / unsupported base) yields the
 * bundled rates unchanged. Merged results are cached per base for the session.
 */
internal class CompositeCurrencyLoader(
    private val delegate: CurrencyLoader,
    private val exchangeRateService: ExchangeRateService,
) : CurrencyLoader {

    private val mergedRates = ConcurrentHashMap<Id.Known, Map<Id.Known, Rate>>()

    override suspend fun availableCurrencies(): Set<Id.Known> = delegate.availableCurrencies()

    override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
        mergedRates[currencyId]?.let { return it }

        val bundled = delegate.ratesFor(currencyId)
        val live = exchangeRateService.ratesFor(currencyId)
        val merged = if (live.isEmpty()) bundled else bundled + live
        return merged.also { mergedRates[currencyId] = it }
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CompositeCurrencyLoaderTest*" 2>&1 | tail -15`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/currencies/CompositeCurrencyLoader.kt app/src/test/java/com/hluhovskyi/zero/currencies/CompositeCurrencyLoaderTest.kt
git commit -m "feat(currencies): add CompositeCurrencyLoader merging live + bundled rates"
```

---

### Task 5: Wire the composite loader into `ApplicationComponent`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`

- [ ] **Step 1: Add imports** near the other currency imports (line ~43-50):

```kotlin
import com.hluhovskyi.zero.currencies.CompositeCurrencyLoader
import com.hluhovskyi.zero.currencies.ExchangeRateService
```

- [ ] **Step 2: Replace the `currencyLoader` provider** (currently `ApplicationComponent.kt:221-232`) so it wraps the predefined loader in the composite:

```kotlin
        @Provides
        @ApplicationScope
        internal fun currencyLoader(
            resourceResolver: ResourceResolver,
            androidUriResourceFactory: AndroidUriResourceFactory,
            localeProvider: LocaleProvider,
            exchangeRateService: ExchangeRateService,
            logger: Logger,
        ): CurrencyLoader = CompositeCurrencyLoader(
            delegate = PredefinedCurrencyLoader(
                resourceResolver = resourceResolver,
                androidUriResourceFactory = androidUriResourceFactory,
                localeProvider = localeProvider,
                logger = logger,
            ),
            exchangeRateService = exchangeRateService,
        )
```

- [ ] **Step 3: Add an `exchangeRateService` provider to `RemoteModule`** (currently `ApplicationComponent.kt:499-516`), next to the `feedbackService` provider:

```kotlin
    @Provides
    @ApplicationScope
    fun exchangeRateService(
        remoteComponent: RemoteComponent,
    ): ExchangeRateService = remoteComponent.exchangeRateService
```

- [ ] **Step 4: Compile (Dagger graph resolves the new binding)**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "feat(currencies): wire CompositeCurrencyLoader + exchange-rate service"
```

---

### Task 6: Document the new service in `zero-remote/AGENTS.md`

**Files:**
- Modify: `zero-remote/AGENTS.md`

- [ ] **Step 1: Extend the "What Lives Here" list** with the new package (keep it terse, traps-only style):

Add under the bullet list:
```markdown
- `currencies/` — `OkHttpFrankfurterExchangeRateService` (impl of `ExchangeRateService`) + `FrankfurterLatestResponse` (HTTP body shape). Frankfurter covers ~31 fiat currencies only — no crypto; unsupported bases return empty, the app's `CompositeCurrencyLoader` falls back to bundled rates.
```

And update the module summary line at the top from "(currently: feedback submission ...)" to also mention live exchange rates.

- [ ] **Step 2: Commit**

```bash
git add zero-remote/AGENTS.md
git commit -m "docs(remote): document Frankfurter exchange-rate service"
```

---

## Verification (after all tasks)

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

UI inspection is **not required** — no UI surface changes; conversion already renders via existing screens. (lets-do Step 5 exempts purely infrastructural changes.) A device smoke check that the app launches and a converted amount displays is a nice-to-have, not a gate.

## Self-Review Notes

- **Spec coverage:** ExchangeRateService (T1), Frankfurter impl + failure handling (T2), RemoteComponent exposure + endpoint default (T3), composite merge/cache/fallback (T4), DI wiring (T5), docs (T6). All spec sections mapped.
- **Type consistency:** `ratesFor(baseId/currencyId): Map<Id.Known, Rate>` used identically across T1/T2/T4; `Id(String)→Id.Known`, `Rate(Double)` factories confirmed against `Id.kt`/`Rate.kt`.
- **No new deps:** zero-remote already bundles okhttp/serialization/mockwebserver; app test already has junit/coroutines-test.
