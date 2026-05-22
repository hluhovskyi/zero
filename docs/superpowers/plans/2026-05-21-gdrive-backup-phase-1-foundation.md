# Phase 1 — Foundation: `zero-backup` Module + Interfaces

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the `zero-backup` module skeleton, `zero-api` backup interfaces, `BackupEnvelope` format, `DefaultBackupUseCase` skeleton driven by a fake `BackupClient`, and a lint rule that keeps the module KMP-pure. No Drive code, no UI, no user-visible change. Foundation only.

**Architecture:** Mirrors `zero-sync`. Interfaces in `zero-api`. Pure-Kotlin implementation in a new `zero-backup` module. Lint rule enforces no Android / OkHttp / coroutines-android imports. `DefaultBackupUseCase` runs the state machine against a fake client; Phase 2 swaps in `DriveBackupClient`.

**Tech Stack:** kotlinx-serialization, kotlinx-coroutines (pure), Dagger (interface only — no inject sites in `zero-backup`).

**Spec:** [Spec §Architecture](../specs/2026-05-21-gdrive-backup-design.md#architecture) and [§Backup Envelope Format](../specs/2026-05-21-gdrive-backup-design.md#backup-envelope-format)

**Structural analogs:** `zero-sync` for module shape; `SyncEngine.kt` + `SyncComponent.kt` for interface shape; `RemoteComponentEncapsulationDetector.kt` for the lint rule.

---

### Task 1: Create `zero-backup` module

**Files:**
- Create: `zero-backup/build.gradle`
- Create: `zero-backup/AGENTS.md`
- Create: `zero-backup/.gitignore`
- Modify: `settings.gradle`

- [ ] **Step 1: Add module to settings.gradle**

Append `include ':zero-backup'`.

- [ ] **Step 2: Create `zero-backup/build.gradle`** modeled on `zero-sync/build.gradle`. Pure Kotlin JVM library, no Android. Depends on `:zero-api`, `deps.kotlin.serialization`, `deps.kotlin.coroutines.core`, `deps.kotlin.datetime`. Test deps mirror `zero-sync`. Read `zero-sync/build.gradle` first and copy its shape exactly; do not invent new options.

- [ ] **Step 3: Create `zero-backup/.gitignore`** with `/build`.

- [ ] **Step 4: Create `zero-backup/AGENTS.md`** with these rules:

```markdown
# zero-backup — Module Guide

Pure-Kotlin orchestration of Google Drive backup. Mirrors `zero-sync`.

## Responsibility

- Define the `BackupEnvelope` wire format and serializer.
- Implement `BackupUseCase` — the backup state machine over `SyncEngine` + a `BackupClient`.
- Implement `DriveBackupClient` (added in Phase 2) — the Drive REST contract over `HttpExecutor` + `OAuthTokenProvider`.
- **No Android, no OkHttp, no kotlinx-coroutines-android.** Pure Kotlin JVM module. Enforced by `BackupModuleEncapsulation` lint rule.

## Key Invariants

- **`SyncSnapshot` is untouched.** The envelope wraps it; never mutates fields on it.
- **Unknown `format` values must be rejected with a versioned error.** No silent acceptance — older builds must surface a "please update Zero" error when they see `format > 1`.
- **State is owned by `DefaultBackupUseCase`.** Don't introduce parallel "is backup running" flags anywhere else.

## Backward Compatibility

- The envelope `format` field is the version handle. Adding `format: 2` (encrypted variant) does not break v1 builds — they fail-loudly on read.
- New optional fields on the envelope itself: nullable + default null, no version bump needed.

## Testing

- Pure unit tests, in-memory fakes. No Android, no real Drive, no Room.
- Backward-compat fixtures live in `zero-backup/src/test/resources/fixtures/backup/`. Add `v1-envelope.json` in Phase 1; never modify existing fixtures.
```

- [ ] **Step 5: Build to verify Gradle setup**

Run: `./gradlew :zero-backup:assemble 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL with no sources yet.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle zero-backup/build.gradle zero-backup/AGENTS.md zero-backup/.gitignore
git commit -m "backup: add zero-backup module skeleton"
```

---

### Task 2: Add `zero-api` interfaces

Interfaces are subpackaged by responsibility, not by feature, so they're reusable beyond backup:

- `backup/` — backup-specific (BackupClient, BackupEnvelope, BackupMetadata, BackupUseCase, BackupError)
- `auth/` — generic OAuth (OAuthTokenProvider) — reusable by future Google integrations (Calendar, Sheets, ...)
- `http/` — generic HTTP transport (HttpExecutor)
- `security/` — generic secure local storage (SecureKeyValueStore)

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/backup/BackupEnvelope.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/backup/BackupMetadata.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/backup/BackupClient.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/backup/BackupUseCase.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/backup/BackupError.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/auth/OAuthTokenProvider.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/security/SecureKeyValueStore.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/http/HttpExecutor.kt`

Model after `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncEngine.kt` for interface naming, and `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncSnapshot.kt` for `@Serializable` style. **Every field on `@Serializable` types MUST carry `@SerialName`** (existing lint rule enforces this).

- [ ] **Step 1: `BackupEnvelope.kt`**

```kotlin
package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    @SerialName("format") val format: Int,
    @SerialName("snapshot") val snapshot: SyncSnapshot,
)
```

- [ ] **Step 2: `BackupMetadata.kt`**

```kotlin
package com.hluhovskyi.zero.backup

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    @SerialName("backupId") val backupId: String,
    @SerialName("createdAt") val createdAt: LocalDateTime,
    @SerialName("byteSize") val byteSize: Long,
    @SerialName("deviceLabel") val deviceLabel: String,
)
```

- [ ] **Step 3: `BackupError.kt`** — sealed taxonomy of failure modes. AuthExpired, NetworkUnavailable, QuotaExceeded, ParseFailure, Unknown(message). Mirror `FeedbackSubmitResult.Failure` shape but more granular because Phase 4 routes errors to notification logic.

```kotlin
package com.hluhovskyi.zero.backup

sealed interface BackupError {
    object AuthExpired : BackupError
    object NetworkUnavailable : BackupError
    object QuotaExceeded : BackupError
    object ParseFailure : BackupError
    data class Unknown(val message: String) : BackupError
}
```

- [ ] **Step 4: `BackupClient.kt`** — interface for the remote backend. Implementations: `FakeBackupClient` (Phase 1, in tests), `DriveBackupClient` (Phase 2).

```kotlin
package com.hluhovskyi.zero.backup

interface BackupClient {
    suspend fun upload(envelope: BackupEnvelope): Result
    suspend fun latest(): Result
    suspend fun download(backupId: String): DownloadResult
    suspend fun delete(backupId: String): Result

    sealed interface Result {
        data class Success(val metadata: BackupMetadata) : Result
        data class Failure(val error: BackupError) : Result
        object NotFound : Result
    }

    sealed interface DownloadResult {
        data class Success(val envelope: BackupEnvelope) : DownloadResult
        data class Failure(val error: BackupError) : DownloadResult
        object NotFound : DownloadResult
    }
}
```

- [ ] **Step 5: `BackupUseCase.kt`** — the orchestrator interface. Exposes `state: Flow<State>` and `perform(Action)`. **No `attach()`** — the use case is scoped to the `BackupComponent` factory (see Task 5 below), which is itself constructed once at app start. Its background work runs in its constructor `CoroutineScope` and lives for as long as the BackupComponent does (effectively process lifetime). Consumers (the `BackupDetailViewModel` UI screen, `WorkManager` worker, notification presenter) observe the flow; none own the use case's lifecycle. The UI screen has its own separate feature scope (`@BackupDetailScope`), independent of the use case's scope.

```kotlin
package com.hluhovskyi.zero.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface BackupUseCase {
    val state: Flow<State>
    fun perform(action: Action)

    data class State(
        val phase: Phase = Phase.Idle,
        val lastSuccessAt: LocalDateTime? = null,
        val lastError: BackupError? = null,
        val consecutiveFailures: Int = 0,
    )

    sealed interface Phase {
        object Idle : Phase
        object Uploading : Phase
        data class Failed(val error: BackupError) : Phase
        object Restoring : Phase
    }

    sealed interface Action {
        object BackupNow : Action
        data class RestoreLatest(val onSnapshot: (com.hluhovskyi.zero.sync.SyncSnapshot) -> Unit) : Action
    }
}
```

- [ ] **Step 6: `OAuthTokenProvider.kt`** — generic OAuth interface. Implementations: `FakeOAuthTokenProvider` (Phase 1 tests), `GoogleOAuthTokenProvider` (Phase 2, in `zero-auth`).

```kotlin
package com.hluhovskyi.zero.auth

import com.hluhovskyi.zero.backup.BackupError

interface OAuthTokenProvider {
    suspend fun getAccessToken(): String?
    suspend fun signIn(): Result
    suspend fun revoke()
    val isSignedIn: kotlinx.coroutines.flow.Flow<Boolean>

    sealed interface Result {
        data class Success(val accountLabel: String) : Result
        data class Failure(val error: BackupError) : Result
        object Cancelled : Result
    }
}
```

Note: `Result.Failure` carries a `BackupError` for v1 to keep the error taxonomy unified. When/if a non-backup consumer (Calendar etc.) needs distinct error semantics, generalize to a dedicated `AuthError` type — out of scope for v1.

- [ ] **Step 7: `SecureKeyValueStore.kt`**

```kotlin
package com.hluhovskyi.zero.security

interface SecureKeyValueStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}
```

- [ ] **Step 8: `HttpExecutor.kt`** — minimal HTTP abstraction. Read `OkHttpFeedbackService` first to see the shape of requests/responses it makes, then define an interface that covers those without leaking OkHttp.

```kotlin
package com.hluhovskyi.zero.http

interface HttpExecutor {
    suspend fun execute(request: HttpRequest): HttpResponse

    data class HttpRequest(
        val method: Method,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: Body? = null,
    ) {
        enum class Method { GET, POST, DELETE }

        sealed interface Body {
            data class Json(val payload: String) : Body
            data class Multipart(
                val metadataJson: String,
                val contentType: String,
                val content: ByteArray,
            ) : Body
        }
    }

    data class HttpResponse(
        val status: Int,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        fun bodyAsString(): String = body.decodeToString()
    }
}
```

- [ ] **Step 9: Build to confirm zero-api compiles**

Run: `./gradlew :zero-api:assemble 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/backup/ \
        zero-api/src/main/java/com/hluhovskyi/zero/auth/ \
        zero-api/src/main/java/com/hluhovskyi/zero/security/ \
        zero-api/src/main/java/com/hluhovskyi/zero/http/
git commit -m "backup(api): add backup/auth/http/security interfaces"
```

---

### Task 3: `BackupEnvelopeSerializer` in `zero-backup` + round-trip test

**Files:**
- Create: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/BackupEnvelopeSerializer.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/BackupEnvelopeSerializerTest.kt`
- Create: `zero-backup/src/test/resources/fixtures/backup/v1-envelope.json`

Model after `zero-sync/src/main/java/com/hluhovskyi/zero/sync/SyncSerializer.kt` and `SyncSerializerRoundTripTest.kt`.

- [ ] **Step 1: Write the failing test first** (TDD)

```kotlin
package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupEnvelopeSerializerTest {

    private val serializer = BackupEnvelopeSerializer()

    private val emptySnapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-05-21T10:00:00"),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
        budgets = emptyList(),
    )

    @Test
    fun `round trips empty snapshot`() {
        val envelope = BackupEnvelope(format = 1, snapshot = emptySnapshot)
        val json = serializer.serialize(envelope)
        assertEquals(envelope, serializer.deserialize(json))
    }

    @Test
    fun `rejects unknown format with descriptive error`() {
        val futureJson = """{"format":99,"snapshot":${"$"}{}}"""
        val ex = assertThrows(IllegalStateException::class.java) {
            serializer.deserialize(futureJson)
        }
        assertEquals(true, ex.message!!.contains("99"))
        assertEquals(true, ex.message!!.contains("update"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :zero-backup:test --tests BackupEnvelopeSerializerTest 2>&1 | tail -10`
Expected: COMPILATION FAILURE (class doesn't exist) or test failure.

- [ ] **Step 3: Implement `BackupEnvelopeSerializer`**

```kotlin
package com.hluhovskyi.zero.backup

import kotlinx.serialization.json.Json

private const val SUPPORTED_FORMAT = 1

class BackupEnvelopeSerializer {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun serialize(envelope: BackupEnvelope): String =
        json.encodeToString(BackupEnvelope.serializer(), envelope)

    fun deserialize(input: String): BackupEnvelope {
        val envelope = json.decodeFromString(BackupEnvelope.serializer(), input)
        check(envelope.format <= SUPPORTED_FORMAT) {
            "Unsupported backup format ${envelope.format}. " +
                "Max supported: $SUPPORTED_FORMAT. Please update Zero."
        }
        return envelope
    }
}
```

- [ ] **Step 4: Re-run tests**

Run: `./gradlew :zero-backup:test --tests BackupEnvelopeSerializerTest 2>&1 | tail -10`
Expected: PASS.

- [ ] **Step 5: Create the v1 fixture** at `zero-backup/src/test/resources/fixtures/backup/v1-envelope.json`. Hand-write a small but realistic envelope — one category, one account, one expense transaction. Mirror the fixture style from `zero-sync/src/test/resources/fixtures/sync/v1-full-snapshot.json`.

- [ ] **Step 6: Add a fixture-driven backwards-compat test case**

Add to `BackupEnvelopeSerializerTest`:

```kotlin
@Test
fun `deserializes v1 fixture without error`() {
    val json = javaClass.classLoader!!
        .getResource("fixtures/backup/v1-envelope.json")!!
        .readText()
    val envelope = serializer.deserialize(json)
    assertEquals(1, envelope.format)
    assertEquals(1, envelope.snapshot.version)
}
```

- [ ] **Step 7: Run all `zero-backup` tests**

Run: `./gradlew :zero-backup:test 2>&1 | tail -15`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add zero-backup/src/main/java/com/hluhovskyi/zero/backup/BackupEnvelopeSerializer.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/BackupEnvelopeSerializerTest.kt \
        zero-backup/src/test/resources/fixtures/backup/v1-envelope.json
git commit -m "backup: add BackupEnvelopeSerializer with v1 round-trip + fixture test"
```

---

### Task 4: `DefaultBackupUseCase` skeleton + state machine tests

**Files:**
- Create: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/DefaultBackupUseCase.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/DefaultBackupUseCaseTest.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeBackupClient.kt`

Model after `DefaultSyncEngine.kt` for the constructor + dependency wiring shape, and `DefaultImportUseCase.kt` for the `MutableStateFlow`-based state-machine pattern. Per `feedback_viewmodel_no_derivation`: all derivation lives here, not in a ViewModel.

- [ ] **Step 1: Implement `FakeBackupClient`** — in-memory test double. Configurable to return success / failure on each method, recording calls. ~50 LOC. Mirror the test-double style of `InMemoryEntitySyncSource` from `zero-sync` tests.

- [ ] **Step 2: Write failing tests** covering:

  1. `BackupNow` happy path: `Idle → Uploading → Idle` with `lastSuccessAt` set, `consecutiveFailures = 0`.
  2. `BackupNow` failure: `Idle → Uploading → Failed`, `consecutiveFailures = 1`.
  3. Three consecutive failures: `consecutiveFailures = 3`.
  4. Success after failure resets counter: 2 failures then 1 success → `consecutiveFailures = 0`.
  5. Concurrent `BackupNow` while `Uploading` is a no-op (does not start a second upload, observes same in-flight job).
  6. `RestoreLatest` happy path: invokes callback with deserialized snapshot.
  7. `RestoreLatest` with `NotFound`: error state, callback not invoked.

  Use `runTest` and `Turbine` or a manual `flow.first()` pattern per existing test style.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :zero-backup:test --tests DefaultBackupUseCaseTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 4: Implement `DefaultBackupUseCase`**

Required behavior (no full code in plan — let the implementing engineer compose this against the failing tests):

- Constructor takes `SyncEngine`, `BackupClient`, `CurrentUserRepository`, `BackupEnvelopeSerializer`, and a `coroutineScope: CoroutineScope`.
- Holds a `MutableStateFlow<State>`. `state: Flow<State>` is `mutableState.asStateFlow()`.
- `perform(BackupNow)` launches a coroutine that takes a `Mutex.withLock`:
  - If `phase is Uploading`, return immediately (coalesce).
  - Update phase to `Uploading`.
  - Call `SyncEngine.export(userId)`.
  - Wrap in `BackupEnvelope(format = 1, snapshot)`.
  - Call `client.upload(envelope)`.
  - On `Success`: update `phase = Idle`, `lastSuccessAt = now`, `consecutiveFailures = 0`, `lastError = null`.
  - On `Failure`: update `phase = Failed(error)`, `consecutiveFailures += 1`, `lastError = error`.
- `perform(RestoreLatest(callback))` similarly: phase → `Restoring`, call `client.latest()` then `download`, deserialize via `BackupEnvelopeSerializer`, invoke callback, return to `Idle`.

- [ ] **Step 5: Re-run tests**

Run: `./gradlew :zero-backup:test 2>&1 | tail -15`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add zero-backup/src/main/java/com/hluhovskyi/zero/backup/DefaultBackupUseCase.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/DefaultBackupUseCaseTest.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeBackupClient.kt
git commit -m "backup: add DefaultBackupUseCase state machine with coalescing + tests"
```

---

### Task 5: `BackupComponent` (manual DI factory)

**Files:**
- Create: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/BackupComponent.kt`

Model after `zero-sync/src/main/java/com/hluhovskyi/zero/sync/SyncComponent.kt`. Manual DI — no Dagger in this module. Wires `DefaultBackupUseCase` from a `Dependencies` interface that `ApplicationComponent` will satisfy.

- [ ] **Step 1: Implement**

```kotlin
package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope

interface BackupComponent {

    interface Dependencies {
        val syncEngine: SyncEngine
        val backupClient: BackupClient
        val currentUserRepository: CurrentUserRepository
        val backupCoroutineScope: CoroutineScope
    }

    val backupUseCase: BackupUseCase
    val envelopeSerializer: BackupEnvelopeSerializer

    class Factory(private val dependencies: Dependencies) {
        fun create(): BackupComponent = DefaultBackupComponent(dependencies)
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}

internal class DefaultBackupComponent(dependencies: BackupComponent.Dependencies) : BackupComponent {

    override val envelopeSerializer: BackupEnvelopeSerializer = BackupEnvelopeSerializer()

    override val backupUseCase: BackupUseCase by lazy {
        DefaultBackupUseCase(
            syncEngine = dependencies.syncEngine,
            backupClient = dependencies.backupClient,
            currentUserRepository = dependencies.currentUserRepository,
            envelopeSerializer = envelopeSerializer,
            coroutineScope = dependencies.backupCoroutineScope,
        )
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :zero-backup:assemble 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-backup/src/main/java/com/hluhovskyi/zero/backup/BackupComponent.kt
git commit -m "backup: add BackupComponent manual DI factory"
```

---

### Task 6: `BackupModuleEncapsulation` lint rule

**Files:**
- Create: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/BackupModuleEncapsulationDetector.kt`
- Modify: `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt`
- Create: `lint-rules/src/test/java/com/hluhovskyi/zero/lint/BackupModuleEncapsulationDetectorTest.java`

Model after `RemoteComponentEncapsulationDetector.kt` and its test. Triggers on any class/function inside the `com.hluhovskyi.zero.backup` package whose body references `okhttp3.*`, `android.*`, `androidx.*`, or `kotlinx.coroutines.android.*`. Read those forbidden-import substrings from a constant.

- [ ] **Step 1: Implement detector** modeled exactly after `RemoteComponentEncapsulationDetector`, but matching imports/types whose FQN starts with one of `okhttp3.`, `android.`, `androidx.`, `kotlinx.coroutines.android.`. Scope: only classes whose file path contains `/zero-backup/`.

- [ ] **Step 2: Register in `ZeroIssueRegistry`** by appending `BackupModuleEncapsulationDetector.ISSUE` to the list.

- [ ] **Step 3: Write positive + negative test cases** modeled after `RemoteComponentEncapsulationDetectorTest`. Positive: a `zero-backup` file importing `okhttp3.OkHttpClient` triggers. Negative: importing only `kotlinx.serialization.*` is clean.

- [ ] **Step 4: Run lint tests**

Run: `./gradlew :lint-rules:test 2>&1 | tail -15`
Expected: PASS.

- [ ] **Step 5: Run lint against `zero-backup`**

Run: `./gradlew :zero-backup:lint 2>&1 | grep -E "error:|Error" | head -10`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/BackupModuleEncapsulationDetector.kt \
        lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/ZeroIssueRegistry.kt \
        lint-rules/src/test/java/com/hluhovskyi/zero/lint/BackupModuleEncapsulationDetectorTest.java
git commit -m "lint: add BackupModuleEncapsulationDetector"
```

---

### Task 7: Wire foundation into `ApplicationComponent` with a temporary fake client

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`
- Create: `app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt`

Goal: prove the foundation compiles into `app` without dragging anything Android-specific into `zero-backup`. The real `DriveBackupClient` lands in Phase 2.

- [ ] **Step 1: Create `NoopBackupClient`** in `app/.../backup/` — implements `BackupClient` by returning `Result.NotFound` for everything. ~30 LOC. Only used until Phase 2 lands.

- [ ] **Step 2: Wire `BackupComponent` in `ApplicationComponent.Module`**

`BackupComponent` is the factory that owns `BackupUseCase`'s lifecycle (lazy val singleton within the factory instance, mirroring how `SyncComponent` owns `SyncEngine`).

`ApplicationComponent.Module` holds a single `BackupComponent` instance at `@ApplicationScope` — same way it holds the `SyncComponent`. The use case's effective scope is the BackupComponent factory's lifetime, not `@ApplicationScope` directly. (This matters when reading the dep graph: `BackupDetailComponent` UI gets `BackupUseCase` via the factory's getter, not as a directly-scoped binding.)

```kotlin
@Provides
@ApplicationScope
fun backupComponent(
    syncEngine: SyncEngine,
    backupClient: BackupClient,
    currentUserRepository: CurrentUserRepository,
): BackupComponent = BackupComponent.factory(object : BackupComponent.Dependencies {
    override val syncEngine = syncEngine
    override val backupClient = backupClient
    override val currentUserRepository = currentUserRepository
    override val backupCoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}).create()

@Provides
fun backupUseCase(backupComponent: BackupComponent): BackupUseCase = backupComponent.backupUseCase

@Provides
@ApplicationScope
fun backupEnvelopeSerializer(backupComponent: BackupComponent): BackupEnvelopeSerializer =
    backupComponent.envelopeSerializer
```

The `BackupClient` is `NoopBackupClient` in Phase 1; Phase 2 swaps it for the real `DriveBackupClient`.

**Debug-flavor override hook:** Phase 5's instrumented round-trip test needs to substitute `FakeBackupClient` for the real client. Since `BackupClient` is an interface and is provided directly via `@Provides`, swap-in for tests is a one-line change in an `androidTest`-flavor module or via a test-only `ApplicationComponent` builder argument. No additional plumbing here — calling out the testability seam.

- [ ] **Step 3: Confirm app build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt \
        app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt
git commit -m "backup: wire BackupComponent in ApplicationComponent with NoopBackupClient"
```

---

## Verification

```bash
./gradlew :zero-backup:test 2>&1 | tail -10
./gradlew :lint-rules:test 2>&1 | tail -10
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

All four MUST pass. No UI changes — skip android-ui-inspector.

## Out of Scope

- Real Drive REST calls — Phase 2.
- OkHttp `HttpExecutor` implementation — Phase 2.
- OAuth provider implementation — Phase 2.
- Settings UI — Phase 3.
- WorkManager — Phase 4.
