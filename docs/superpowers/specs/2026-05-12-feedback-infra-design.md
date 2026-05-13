# Feedback Infra (Phase 1 of shake-to-feedback)

**Date:** 2026-05-12
**Issue:** #81 — `feat: shake-to-feedback screen with log collection and GitHub issue reporting`
**Scope:** Phase 1 (infrastructure only — no UI, no shake detection, no log buffer). Phase 2 (UI + on-device capture) ships in a follow-up PR.

## Goal

Build the abuse-protected pipe that future shake-to-feedback UI will call. The pipe must:

1. Authenticate the caller as a genuine, Play-distributed copy of the app (no static secret in the AAB).
2. Forward a sanitized payload to a GitHub Issues API call made server-side.
3. Encapsulate all HTTP / Play Integrity / JSON details inside a dedicated module so the broader app sees only `FeedbackSubmitter`.

## Non-Goals

- Any user-facing UI.
- Shake detection.
- In-memory log capture or navigation-history ring buffer.
- Promoting the app from Play Internal Testing to Production.
- Server-side rate limiting (deferred — Closed Testing distribution + Play Integrity is sufficient gate at this scale).

## Distribution Gate

Operational only. Existing CD (`.github/workflows/cd.yml`) uploads release builds to the Play **Internal Testing** track; this PR does not change that. The pipe is reachable only by builds Google Play has distributed to invited testers, plus debug builds running on devices registered as Play Integrity test devices (see §7).

## Why Play Integrity Instead of a Shared Secret

A shared secret embedded in `BuildConfig` is plain text in the AAB; anyone with the binary can extract it via `apktool` / `jadx`. Play Integrity replaces that static secret with a short-lived, server-verified attestation that proves the request came from a genuine Play-distributed install on a non-tampered device. A leaked or sideloaded AAB cannot mint valid tokens, which makes "limited to closed testing" enforceable at the code level rather than only operationally.

---

## 1. New Module: `zero-remote`

Mirrors `zero-database`'s structure exactly. Depends only on `zero-api`. All HTTP, OkHttp, Play Integrity, and JSON details live here as `internal` types.

```
zero-remote/
  build.gradle                       # deps: zero-api, dagger, okhttp, playIntegrity, kotlinx.serialization
  AGENTS.md                          # rules + encapsulation guarantee
  src/main/java/com/hluhovskyi/zero/
    RemoteComponent.kt               # public Dagger component
    feedback/
      OkHttpFeedbackSubmitter.kt     # internal
      FeedbackDto.kt                 # internal @Serializable types
    integrity/
      IntegrityTokenProvider.kt      # internal interface
      PlayIntegrityTokenProvider.kt  # internal impl wrapping com.google.android.play:integrity
  src/test/java/com/hluhovskyi/zero/
    feedback/OkHttpFeedbackSubmitterTest.kt
    integrity/FakeIntegrityTokenProvider.kt   # test util
```

Add to `settings.gradle`: `include ':zero-remote'`.

## 2. `RemoteComponent`

```kotlin
@RemoteScope
@dagger.Component(
    modules = [RemoteComponent.Module::class],
    dependencies = [RemoteComponent.Dependencies::class],
)
interface RemoteComponent {

    val feedbackSubmitter: FeedbackSubmitter   // only zero-api types exposed

    interface Dependencies {
        val context: Context                   // for Play Integrity SDK init
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerRemoteComponent.builder()
            .dependencies(dependencies)
            .feedbackEndpoint("")              // blank => MissingConfig at call time
    }

    @dagger.Component.Builder
    interface Builder {
        fun dependencies(d: Dependencies): Builder
        @BindsInstance fun feedbackEndpoint(@FeedbackEndpoint endpoint: String): Builder
        fun build(): RemoteComponent
    }

    @dagger.Module
    object Module {
        @Provides @RemoteScope internal fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @Provides @RemoteScope internal fun json(): Json = Json { ignoreUnknownKeys = true }

        @Provides @RemoteScope internal fun integrityTokenProvider(context: Context): IntegrityTokenProvider =
            PlayIntegrityTokenProvider(context)

        @Provides @RemoteScope internal fun feedbackSubmitter(
            @FeedbackEndpoint endpoint: String,
            client: OkHttpClient,
            tokenProvider: IntegrityTokenProvider,
            json: Json,
        ): FeedbackSubmitter = OkHttpFeedbackSubmitter(endpoint, client, tokenProvider, json)
    }
}
```

OkHttp, Play Integrity, Json, DTOs — none appear in any signature visible outside the module.

## 3. `zero-api` Additions

Pure Kotlin; no Android, no networking deps — same rules as today.

```kotlin
data class FeedbackReport(
    val title: String,
    val body: String,
    val labels: List<String> = emptyList(),
)

sealed interface FeedbackSubmitResult {
    data class Success(val issueUrl: String) : FeedbackSubmitResult
    sealed interface Failure : FeedbackSubmitResult {
        object MissingConfig : Failure         // FEEDBACK_ENDPOINT blank
        object IntegrityUnavailable : Failure  // Play Services missing / token request failed
        object IntegrityRejected : Failure     // server returned 401/403
        object Network : Failure
        data class Http(val code: Int) : Failure
    }
}

interface FeedbackSubmitter {
    suspend fun submit(report: FeedbackReport): FeedbackSubmitResult
}
```

This is the **entire** public surface from this PR. Phase 2's UI imports only these types.

## 4. `OkHttpFeedbackSubmitter` Behaviour

Internal to `zero-remote`. Sequence per `submit(report)`:

1. If `endpoint.isBlank()` → return `Failure.MissingConfig` (no network, no Integrity call).
2. Generate a nonce (random UUID hashed to base64).
3. `tokenProvider.getToken(nonce)` → `null` ⇒ return `Failure.IntegrityUnavailable`.
4. Build OkHttp `Request`: `POST endpoint`, header `X-Integrity-Token: <token>`, JSON body from `FeedbackDto.Request(title, body, labels)`.
5. Execute on `Dispatchers.IO`. Map response:
   - `201` → parse `FeedbackDto.Response(issueUrl)` → `Success(issueUrl)`
   - `401` / `403` → `Failure.IntegrityRejected`
   - Other 4xx / 5xx → `Failure.Http(code)`
   - `IOException` → `Failure.Network`

DTOs (`FeedbackDto.Request`, `FeedbackDto.Response`) are `internal @Serializable` data classes inside `feedback/`. They never appear in the public API.

## 5. `PlayIntegrityTokenProvider`

Internal. Wraps `com.google.android.play.integrity.StandardIntegrityManager`.

- On construction, calls `prepareIntegrityToken(cloudProjectNumber)` lazily and caches the warm provider (Google's recommended pattern for low-latency requests).
- `getToken(nonce)` calls `provider.request(StandardIntegrityTokenRequest(nonce))`. On any exception (Play Services missing, network, IntegrityServiceException) returns `null` — translated upstream to `Failure.IntegrityUnavailable`.

The `cloudProjectNumber` is supplied via the same wiring path as `feedbackEndpoint` (a separate `@BindsInstance` on the builder, sourced from `BuildConfig`).

## 6. Build / Secrets Wiring

### Root `build.gradle` deps additions

```
okhttp           : "com.squareup.okhttp3:okhttp:4.12.0",
mockwebserver    : "com.squareup.okhttp3:mockwebserver:4.12.0",
playIntegrity    : "com.google.android.play:integrity:1.4.0",
```

### `app/build.gradle` defaultConfig

```groovy
buildConfigField "String", "FEEDBACK_ENDPOINT",
    "\"${System.getenv('FEEDBACK_ENDPOINT') ?: localProps['feedbackEndpoint'] ?: ''}\""
buildConfigField "String", "FEEDBACK_INTEGRITY_PROJECT",
    "\"${System.getenv('FEEDBACK_INTEGRITY_PROJECT') ?: localProps['feedbackIntegrityProject'] ?: ''}\""
```

Pattern matches the existing keystore wiring in §1. Empty defaults keep local builds and CI runs without secrets green; the submitter returns `MissingConfig` rather than crashing.

### Secrets matrix

| Where | `FEEDBACK_ENDPOINT` | `FEEDBACK_INTEGRITY_PROJECT` | Other |
|---|---|---|---|
| Local dev | `local.gradle.properties` | `local.gradle.properties` | Dev device registered as Play Integrity test device |
| CD release builds | GitHub Actions secret | GitHub Actions secret | n/a |
| Cloud Function env | n/a | n/a | `GITHUB_TOKEN`, `REPO_OWNER`, `REPO_NAME`, `PACKAGE_NAME`, `GCP_PROJECT_NUMBER` |

**No `FEEDBACK_SECRET` exists, anywhere.**

## 7. Debug Builds (Play Integrity test devices)

No special code path — `OkHttpFeedbackSubmitter` is the only binding in all build types. The setup that makes debug builds work is operational, documented in `docs/agents/feedback-infra.md`:

1. In Play Console → App integrity → Integrity API → "Test devices", register your dev device's Google Account.
2. Configure the test device response: `MEETS_DEVICE_INTEGRITY`, `PLAY_RECOGNIZED`.
3. Install your debug build on that device. The same code path mints valid tokens.

**Convention:** Phase 2 callers append `"debug"` to `labels` when `BuildConfig.DEBUG`, so the issue tracker is filterable. Phase 1 only documents the convention; no debug-detection in the submitter itself.

## 8. Cloud Function (`functions/feedback/`)

Node.js 20 GCP Cloud Function (HTTP trigger). ~40 LOC.

- **Endpoint:** `POST /` — single handler.
- **Auth:** reads `X-Integrity-Token` header; decodes via `playintegrity.v1.decodeIntegrityToken`. Rejects unless:
  - `appIntegrity.appRecognitionVerdict == "PLAY_RECOGNIZED"`
  - `deviceIntegrity.deviceRecognitionVerdict` includes `"MEETS_DEVICE_INTEGRITY"`
  - `appIntegrity.packageName == PACKAGE_NAME`
  - On failure → 401 / 403 with sanitized error.
- **Payload schema:** `{ "title": string, "body": string, "labels"?: string[] }`. Title and body required. Validation failure → 400.
- **GitHub call:** `@octokit/rest` with PAT from `GITHUB_TOKEN`, repo from `REPO_OWNER` / `REPO_NAME`. On 201, response = `{ "issueUrl": "<html_url>" }`.
- **Server env vars:** `GITHUB_TOKEN`, `REPO_OWNER`, `REPO_NAME`, `PACKAGE_NAME`, `GCP_PROJECT_NUMBER`.
- **IAM:** function's runtime service account needs `roles/playintegrity.tokenDecoder` on the GCP project that owns Integrity API.
- **Files:** `index.js`, `package.json`, `README.md` (deploy command, IAM steps, manual smoke test note — see §11).
- **Rate limiting:** deferred. Function returns whatever Octokit returns (including 403 for secondary rate limits) — client already maps that to `Failure.Http(code)`.

## 9. Lint Rule: `RemoteComponentEncapsulationDetector`

Mirrors `DatabaseComponentEncapsulationDetector`. File: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/RemoteComponentEncapsulationDetector.kt`.

Reports an error when `RemoteComponent`'s public methods or fields return any class whose FQN starts with one of:

- `okhttp3.`
- `com.google.android.play.core.integrity.`
- `com.google.android.play.integrity.`
- `kotlinx.serialization.json.`

Or any class declared `internal` with package prefix `com.hluhovskyi.zero.` from `zero-remote`.

Registered in `ZeroIssueRegistry`. Test file `RemoteComponentEncapsulationDetectorTest` mirrors the existing `DatabaseComponentEncapsulationDetectorTest` (positive + negative samples).

## 10. `app` Wiring

- `app/build.gradle`: add `implementation project(":zero-remote")`. No direct OkHttp / Play Integrity / Json deps in `app` — they remain transitive through `zero-remote` and are only used internally there.
- `ApplicationComponent.kt`: build a `RemoteComponent` with `Context` + `BuildConfig.FEEDBACK_ENDPOINT` + `BuildConfig.FEEDBACK_INTEGRITY_PROJECT`. Expose `feedbackSubmitter: FeedbackSubmitter` for downstream components (no consumer in this PR).

## 11. Verification

- **Unit tests** (`zero-remote/src/test`): `OkHttpFeedbackSubmitterTest` with `MockWebServer` + `FakeIntegrityTokenProvider`. Six cases:
  1. Happy path → `Success(issueUrl)` with body parsed from server response.
  2. Blank endpoint → `Failure.MissingConfig`; assert `mockWebServer.requestCount == 0`.
  3. Token provider returns `null` → `Failure.IntegrityUnavailable`; assert `mockWebServer.requestCount == 0`.
  4. Server returns 401 → `Failure.IntegrityRejected`. Same for 403.
  5. Server returns 5xx → `Failure.Http(code)`.
  6. `MockWebServer.shutdown()` mid-request (or socket reset policy) → `Failure.Network`.
- **Lint test** (`lint-rules/src/test`): `RemoteComponentEncapsulationDetectorTest` with positive + negative samples.
- `./gradlew testDebugUnitTest` and `./gradlew lintDebug` clean.
- **Cloud function:** manual smoke test only — `curl` against the deployed endpoint with a real Integrity token captured from a debug build's logcat. Documented in the function README. No automated server-side test in this PR.
- **No UI inspector** — no UI changes.

## 12. PII Discipline

Issue #81, this spec, the implementation plan, all commits, the PR body, and `feedback-infra.md`:

- Use placeholders only: `<your-cloud-function-url>`, `<gcp-project-number>`, `<repo-owner>`, `<repo-name>`. No real URLs, GitHub PATs, device IDs, emails, or tester names.
- Cloud function README warns against logging request bodies (user descriptions could contain PII).
- Cloud function code logs only decision booleans for the Integrity verdict, never the raw verdict (which exposes device-tier info).
- `@octokit` errors echoed to clients are sanitized to status code + generic message.

## 13. Out of Scope (Phase 2+)

Captured here so reviewers know what is intentionally absent:

- Shake detection (`SensorManager` + accelerometer threshold).
- In-memory log sink (custom `Timber` tree) and navigation-history ring buffer.
- Feedback bottom sheet / screen UI.
- Device info + app version attached automatically.
- Wiring `FeedbackSubmitter` into a ViewModel.
- Server-side rate limiting.
- Promotion to Play Production track.
