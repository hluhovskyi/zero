# Feedback Infra Phase 1 — Implementation Plan

**Spec:** [docs/superpowers/specs/2026-05-12-feedback-infra-design.md](../specs/2026-05-12-feedback-infra-design.md)
**Issue:** #81 (Phase 1 of 2)
**Branch:** `worktree-feedback-infra-phase1` (based on `worktree-limit-shake-feedback-testers`)

Read the spec first. This plan only enumerates concrete file changes; design rationale and trade-offs live in the spec.

## Order of Work

Bottom-up so each step compiles. Each numbered step is one logical commit.

### 1. zero-api types

`zero-api/src/main/java/com/hluhovskyi/zero/feedback/`

- `FeedbackReport.kt` — data class (`title: String`, `body: String`, `labels: List<String> = emptyList()`).
- `FeedbackSubmitResult.kt` — sealed interface with `Success(issueUrl: String)` and `object Failure`.
- `FeedbackService.kt` — interface with `suspend fun submit(report: FeedbackReport): FeedbackSubmitResult`.

No tests in `zero-api` (it has none today).

### 2. Root `build.gradle` deps

Add to the `deps` map:

```
okhttp           : "com.squareup.okhttp3:okhttp:4.12.0",
mockwebserver    : "com.squareup.okhttp3:mockwebserver:4.12.0",
playIntegrity    : "com.google.android.play:integrity:1.4.0",
```

### 3. New module `zero-remote`

`settings.gradle` — add `include ':zero-remote'`.

`zero-remote/build.gradle` — Android library; `implementation deps.okhttp`, `deps.playIntegrity`, `deps.kotlin.serialization`, `deps.dagger.runtime`, `ksp deps.dagger.compiler`, `implementation deps.timber`, `testImplementation deps.test.junit / mockito / coroutines / mockwebserver`. Depends on `:zero-api`. Compose absent. Match `zero-database/build.gradle` boilerplate (compileOptions, kotlin opt-ins, namespace `com.hluhovskyi.zero`).

`zero-remote/AGENTS.md` — short rules:
1. Depends only on `zero-api`.
2. Hard Encapsulation: `RemoteComponent` MUST NOT expose any `okhttp3.*`, `com.google.android.play.*integrity.*`, `kotlinx.serialization.json.*`, or `internal` types — only `zero-api` interfaces. Lint rule `RemoteComponentEncapsulation` enforces this.
3. All HTTP, JSON, Integrity types are `internal`.

### 4. `RemoteComponent` and integrity providers

`zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt` — Mirror `DatabaseComponent`'s structure. Public surface = exactly `feedbackService: FeedbackService`. Builder takes `Dependencies` (just `context`) plus two `@BindsInstance` fields: `@FeedbackEndpoint endpoint: String`, `@IntegrityCloudProject cloudProjectNumber: Long`. Module provides OkHttpClient (10s connect, 30s read), Json (`ignoreUnknownKeys = true`), `IntegrityTokenProvider` (delegates to `PlayIntegrityTokenProvider`), and `FeedbackService` (delegates to `OkHttpFeedbackService`).

`zero-remote/src/main/java/com/hluhovskyi/zero/integrity/IntegrityTokenProvider.kt` — internal interface: `suspend fun getToken(nonce: String): String?`.

`zero-remote/src/main/java/com/hluhovskyi/zero/integrity/PlayIntegrityTokenProvider.kt` — internal class wrapping Google's `StandardIntegrityManager`. On construction caches `prepareIntegrityToken(cloudProjectNumber)`. `getToken(nonce)` calls `provider.request(StandardIntegrityTokenRequest.builder().setRequestHash(nonce).build())`. Wraps the entire body in try/catch returning `null` on any exception (Play Services missing, network, IntegrityServiceException). Logs failures via `Timber.w`.

### 5. `OkHttpFeedbackService` + payloads

`zero-remote/src/main/java/com/hluhovskyi/zero/feedback/FeedbackRequest.kt` — internal `@Serializable data class FeedbackRequest(val title: String, val body: String, val labels: List<String>)`.

`zero-remote/src/main/java/com/hluhovskyi/zero/feedback/FeedbackResponse.kt` — internal `@Serializable data class FeedbackResponse(val issueUrl: String)`.

`zero-remote/src/main/java/com/hluhovskyi/zero/feedback/OkHttpFeedbackService.kt` — internal class. Behaviour per spec §4. Uses `withContext(Dispatchers.IO)`. Logs all failure paths via `Timber.w`. Public method returns only `Success` or `Failure`.

### 6. zero-remote tests

`zero-remote/src/test/java/com/hluhovskyi/zero/integrity/FakeIntegrityTokenProvider.kt` — test-internal class taking a `(nonce) -> String?` lambda.

`zero-remote/src/test/java/com/hluhovskyi/zero/feedback/OkHttpFeedbackServiceTest.kt` — `MockWebServer` based; six cases per spec §11. Each Failure case asserts the result type AND the relevant transport invariant (e.g. `mockWebServer.requestCount == 0` when no token).

### 7. Lint rule

`lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/RemoteComponentEncapsulationDetector.kt` — Kotlin port of `DatabaseComponentEncapsulationDetector`. Triggers when a class named `RemoteComponent` and `isInterface` exposes a method/field whose return type's FQN starts with `okhttp3.`, `com.google.android.play.core.integrity.`, `com.google.android.play.integrity.`, or `kotlinx.serialization.json.`, or whose visibility modifier is internal.

`lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt` — append `RemoteComponentEncapsulationDetector.ISSUE` to the list.

`lint-rules/src/test/java/com/hluhovskyi/zero/lint/RemoteComponentEncapsulationDetectorTest.java` — mirror the existing `DatabaseComponentEncapsulationDetectorTest` shape. Positive case: a `RemoteComponent` returning `OkHttpClient` triggers the rule. Negative case: returning `FeedbackService` is clean.

### 8. `app` wiring

`app/build.gradle`:
- Add `implementation project(":zero-remote")`.
- In `defaultConfig`, add two `buildConfigField` lines per spec §6 (`FEEDBACK_ENDPOINT`, `FEEDBACK_INTEGRITY_PROJECT`). Use the `System.getenv ?: localProps[...] ?: ''` pattern that already exists for the keystore.

`app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt` — Build a `RemoteComponent` from `BuildConfig.FEEDBACK_ENDPOINT`, `BuildConfig.FEEDBACK_INTEGRITY_PROJECT.toLongOrNull() ?: 0L`, and `Context`. Expose `val feedbackService: FeedbackService` as a field (no consumer in this PR — Phase 2 will inject downstream).

### 9. Cloud function

`functions/feedback/index.js` — Node 20, ~50 LOC. Per spec §8: validates `X-Integrity-Token` via `googleapis` `playintegrity.v1.decodeIntegrityToken`; checks the three verdicts; calls `@octokit/rest` to create the issue. Returns `201 { issueUrl }` or sanitized error.

`functions/feedback/package.json` — name `feedback`, type module, deps `@octokit/rest` `^21`, `googleapis` `^144`. Entry point `index.js`.

`functions/feedback/README.md` — Deploy command (`gcloud functions deploy feedback --gen2 --runtime=nodejs20 --trigger-http --no-allow-unauthenticated --set-env-vars ...`), IAM step (`roles/playintegrity.tokenDecoder`), env var matrix, manual smoke test (`curl -H "X-Integrity-Token: <token-from-debug-logcat>" ...`). All values are placeholders.

### 10. `docs/agents/feedback-infra.md`

Per spec §6 secrets matrix and §7 debug-build setup. Plus a "Phase 2 hook" paragraph noting that the Phase 2 callers append `"debug"` to `labels` when `BuildConfig.DEBUG`. Reference the spec via relative link.

## Verification

```bash
./gradlew testDebugUnitTest 2>&1 | tail -30
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
./gradlew assembleDebug 2>&1 | tail -10
```

No UI changes — skip android-ui-inspector.

If `assembleDebug` fails on missing Play Integrity classes, ensure the dep version resolves and that `compileSdk 35` is sufficient. Play Integrity 1.4.0 requires no special config beyond the dep.

## Out of Scope (Phase 2+)

Captured in spec §13. Notably: shake detection, log buffer, feedback UI, ViewModel wiring of `FeedbackService`, server-side rate limiting, Production track promotion.

## PII Discipline

No real cloud function URL, GCP project number, repo name, GitHub PAT, device ID, or tester email in any committed file. Use placeholders. The cloud function README explicitly warns against logging request bodies.
