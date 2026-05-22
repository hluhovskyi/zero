# Phase 2 — Drive REST Client + OAuth

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Phase 1's `NoopBackupClient` with a real Drive REST implementation backed by Credential Manager OAuth, OkHttp transport, and EncryptedSharedPreferences. End-to-end against a real Google account by the end of the phase. Still no UI — exercised via a temporary developer entry point.

**Architecture:** Four new Android-side implementations of `zero-api` interfaces, split across three modules by responsibility:

- **New `zero-auth` module** — `AuthComponent` + `GoogleOAuthTokenProvider` (generic Google OAuth, scope-parameterized). Owns the Credential Manager flow + token-exchange/refresh/revoke against `https://oauth2.googleapis.com`.
- **`zero-remote`** — `OkHttpHttpExecutor` (generic HTTP transport, internal). Provided via `RemoteComponent`.
- **`app`** — `AndroidSecureKeyValueStore` (EncryptedSharedPreferences). Provided directly from `ApplicationComponent`.
- **`zero-backup`** — `DriveBackupClient` (pure Kotlin Drive REST contract). Composes `HttpExecutor` + `OAuthTokenProvider` + `BackupEnvelopeSerializer`.

`ApplicationComponent` wires it all and replaces the noop. Dep flow: `AuthComponent` takes `HttpExecutor` (from `RemoteComponent`) + `SecureKeyValueStore` (from `app`) + activity provider + OAuth client ID. `DriveBackupClient` constructed inline in `ApplicationComponent` from `HttpExecutor` (`RemoteComponent`) + `OAuthTokenProvider` (`AuthComponent`).

**Tech Stack:**
- `androidx.credentials`, `googleid` → `zero-auth/build.gradle` (new)
- `androidx.security:security-crypto` → `app/build.gradle` (new)
- OkHttp, kotlinx-serialization → already in `zero-remote`

**Spec:** [Spec §Drive Integration](../specs/2026-05-21-gdrive-backup-design.md#drive-integration), [§Auth](../specs/2026-05-21-gdrive-backup-design.md#auth-zero-auth-module), and [§Security Model](../specs/2026-05-21-gdrive-backup-design.md#security-model)

**Structural analogs:**
- `zero-sync/build.gradle` + `zero-sync/AGENTS.md` → `zero-auth/build.gradle` + `zero-auth/AGENTS.md` (module shape; zero-auth IS Android though, not pure Kotlin — so model the `build.gradle` after `zero-remote` instead).
- `PlayIntegrityTokenProvider.kt` → `GoogleOAuthTokenProvider.kt`
- `OkHttpFeedbackService.kt` → `OkHttpHttpExecutor.kt` + `DriveBackupClient.kt`
- `RemoteComponent.kt` (Builder + `@BindsInstance` + qualifiers) → `AuthComponent.kt` (same shape) + small additions to `RemoteComponent.kt` (HttpExecutor only)

---

### Task 1: Create `zero-auth` module

**Files:**
- Modify: `settings.gradle`
- Create: `zero-auth/build.gradle`
- Create: `zero-auth/AGENTS.md`
- Create: `zero-auth/.gitignore`

Model after `zero-remote` (Android library module — not pure Kotlin, since Credential Manager is Android-only).

- [ ] **Step 1: Add to `settings.gradle`:** `include ':zero-auth'`.

- [ ] **Step 2: Create `zero-auth/build.gradle`** modeled on `zero-remote/build.gradle`. Android library; depends on `:zero-api`, kotlinx-coroutines-android, dagger runtime + ksp, kotlinx-serialization (for token-exchange request bodies), timber. Test deps mirror `zero-remote`. Namespace `com.hluhovskyi.zero`.

- [ ] **Step 3: Create `zero-auth/.gitignore` with `/build`** — per the AGENTS.md rule "New module: add a per-module `.gitignore` with `/build`."

- [ ] **Step 4: Create `zero-auth/AGENTS.md`:**

```markdown
# zero-auth — Module Guide

Google sign-in / OAuth flow. Holds the Android-side implementation of `OAuthTokenProvider`
(in `zero-api/auth/`). Generic over Google scopes — Drive backup is one consumer; future
integrations (Calendar, Sheets, ...) can build their own `AuthComponent` with different scopes
without changing this module.

## Responsibility

- Implement `OAuthTokenProvider` against Android Credential Manager + Google ID + the OAuth 2.0
  token endpoint.
- Persist refresh tokens via `SecureKeyValueStore` (interface from `zero-api/security/`).
- Use `HttpExecutor` (interface from `zero-api/http/`) for token exchange and revoke calls —
  do not import OkHttp directly.

## Encapsulation

- **No networking concerns** (those live in `zero-remote`). This module never imports OkHttp.
- **No UI** — token UI (sign-in CTA, status row) lives in `zero-core`.
- All implementation classes are `internal`; the public surface is `AuthComponent`.

## Key Invariants

- Refresh tokens are written through `SecureKeyValueStore`, never to plain SharedPreferences.
- Access tokens live in memory only — never persist them.
- Scope is set at component build time via `@BindsInstance`. Don't accept arbitrary scopes
  at call time; each consumer builds its own component.
```

- [ ] **Step 5: Update root `AGENTS.md`** module map to add `zero-auth`. Locate the existing module list and append:

```
zero-auth            → Google OAuth (Credential Manager + token exchange)
```

- [ ] **Step 6: Build to verify Gradle setup**

Run: `./gradlew :zero-auth:assemble 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL with no sources yet.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle zero-auth/ AGENTS.md
git commit -m "backup(auth): add zero-auth module skeleton"
```

---

### Task 2: Add dependencies

**Files:**
- Modify: `build.gradle` (root, the `deps` map and `versions` map)
- Modify: `zero-auth/build.gradle`
- Modify: `app/build.gradle`

Credential Manager + googleid are sign-in concerns used by `GoogleOAuthTokenProvider` in `zero-auth` → go into `zero-auth`. `security-crypto` is the EncryptedSharedPreferences lib used by `AndroidSecureKeyValueStore` which lives in `app` → goes into `app`. `zero-remote` gains **no new deps** in Phase 2 — its OkHttp is reused for `OkHttpHttpExecutor`.

- [ ] **Step 1: Append to the root `deps` map**

```groovy
credentials                : "androidx.credentials:credentials:1.3.0",
credentialsPlayServicesAuth: "androidx.credentials:credentials-play-services-auth:1.3.0",
googleId                   : "com.google.android.libraries.identity.googleid:googleid:1.1.1",
securityCrypto             : "androidx.security:security-crypto:1.1.0-alpha06",
```

- [ ] **Step 2: Add to `zero-auth/build.gradle` dependencies block**

```
implementation deps.credentials
runtimeOnly    deps.credentialsPlayServicesAuth
implementation deps.googleId
```

`credentialsPlayServicesAuth` is the runtime Play Services adapter for Credential Manager — no code references it at compile time; it registers via manifest merger. `runtimeOnly` makes this explicit.

- [ ] **Step 3: Add to `app/build.gradle` dependencies block**

```
implementation deps.securityCrypto
```

- [ ] **Step 4: Build to confirm resolution**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add build.gradle zero-auth/build.gradle app/build.gradle
git commit -m "backup: add Credential Manager (zero-auth) + EncryptedSharedPreferences (app) deps"
```

---

### Task 3: `OkHttpHttpExecutor` (transport adapter)

**Files:**
- Create: `zero-remote/src/main/java/com/hluhovskyi/zero/http/OkHttpHttpExecutor.kt`
- Create: `zero-remote/src/test/java/com/hluhovskyi/zero/http/OkHttpHttpExecutorTest.kt`

`HttpExecutor` is a generic primitive (not Drive-specific, not OAuth-specific), so the impl lives at `zero-remote/http/` — top-level for the module, no `drive/` subpackage.

Internal class implementing `HttpExecutor` from `zero-api`. Read `OkHttpFeedbackService.kt` first — copy its `withContext(Dispatchers.IO)` pattern, its `Request.Builder` shape, and its error handling.

- [ ] **Step 1: TDD — write failing tests** against `MockWebServer`. Mirror `OkHttpFeedbackServiceTest`. Cases:
  - GET success: status 200, body returned.
  - POST JSON success: request carries `Content-Type: application/json` and the right body.
  - POST Multipart success: request is `multipart/related` with two parts (metadata + content).
  - 401: returned as-is (status 401), not silently swallowed.
  - Network failure: exception bubbles out (the caller decides how to handle).

- [ ] **Step 2: Run tests, expect FAIL**

- [ ] **Step 3: Implement `OkHttpHttpExecutor`** — `internal` class. Constructor takes `OkHttpClient`. Switches on `request.method` and `request.body` to build the OkHttp `RequestBody`. Multipart shape:

```
MultipartBody.Builder("boundary")
    .setType("multipart/related".toMediaType())
    .addPart(metadata-json as application/json)
    .addPart(content as <contentType>)
    .build()
```

Returns `HttpResponse(status, headers, bodyBytes)`. No exception-swallowing.

- [ ] **Step 4: Re-run tests, expect PASS**

- [ ] **Step 5: Provide it via `RemoteComponent`**

Modify `zero-remote/.../RemoteComponent.kt`:
- Add `val httpExecutor: HttpExecutor` to the public interface.
- Add an `@RemoteScope @Provides internal fun httpExecutor(client: OkHttpClient): HttpExecutor = OkHttpHttpExecutor(client)` in the module.

- [ ] **Step 6: Commit**

```bash
git add zero-remote/src/main/java/com/hluhovskyi/zero/http/OkHttpHttpExecutor.kt \
        zero-remote/src/test/java/com/hluhovskyi/zero/http/OkHttpHttpExecutorTest.kt \
        zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt
git commit -m "backup(remote): add OkHttpHttpExecutor + RemoteComponent provide"
```

---

### Task 4: `AndroidSecureKeyValueStore` + Auto Backup exclusion

`AndroidSecureKeyValueStore` lives in `app/` because EncryptedSharedPreferences is not a networked concern and isn't auth-specific either — it's a generic Android secure-storage primitive. It's provided directly from `ApplicationComponent`, like `ImageLoader` or `idGenerator` are today. `GoogleOAuthTokenProvider` (in `zero-auth`) receives it via `AuthComponent.Dependencies` — see Task 5.

**Files:**
- Create: `app/src/main/java/com/hluhovskyi/zero/security/AndroidSecureKeyValueStore.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: Implement** — `class AndroidSecureKeyValueStore(context: Context) : SecureKeyValueStore`. Backs onto `EncryptedSharedPreferences` with `MasterKey` (`KeyScheme.AES256_GCM`). Use file name `"zero_secure_prefs"`. All methods run on `Dispatchers.IO`.

- [ ] **Step 2: Provide via `ApplicationComponent`** — in `ApplicationComponent.Module`, add a `@Provides @ApplicationScope` factory returning `AndroidSecureKeyValueStore(context)` as `SecureKeyValueStore`.

- [ ] **Step 3: Exclude `zero_secure_prefs` from Auto Backup** (Phase 0's rules already cover the database; this adds an explicit exclude for the secure prefs).

  **Rationale:** `EncryptedSharedPreferences` is wrapped by a `MasterKey` stored in Android Keystore. Keystore keys are **device-bound** — they cannot be exported, backed up, or restored on a different device. Including `zero_secure_prefs` in Auto Backup would send an encrypted blob to the cloud that the destination device physically cannot decrypt. Worse, transparently transferring a long-lived OAuth refresh token to a new device without user consent is a privacy regression. The correct flow on phone swap is: re-sign-in on the new device.

  Add to `app/src/main/res/xml/backup_rules.xml` inside `<full-backup-content>`:

```xml
<exclude domain="sharedpref" path="zero_secure_prefs" />
```

  Add to `app/src/main/res/xml/data_extraction_rules.xml` inside **both** `<cloud-backup>` and `<device-transfer>`:

```xml
<exclude domain="sharedpref" path="zero_secure_prefs" />
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Skip instrumented test for v1** — EncryptedSharedPreferences requires an actual device/emulator. Unit-test coverage for this is impractical. Verification is end-to-end in Task 8.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/security/AndroidSecureKeyValueStore.kt \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt \
        app/src/main/res/xml/backup_rules.xml \
        app/src/main/res/xml/data_extraction_rules.xml
git commit -m "backup(app): AndroidSecureKeyValueStore + exclude zero_secure_prefs from Auto Backup"
```

---

### Task 5: `GoogleOAuthTokenProvider` + `AuthComponent` (in `zero-auth`)

**Files:**
- Create: `zero-auth/src/main/java/com/hluhovskyi/zero/auth/GoogleOAuthTokenProvider.kt`
- Create: `zero-auth/src/main/java/com/hluhovskyi/zero/auth/AuthComponent.kt`

`AuthComponent` is the DI root for `zero-auth`. Model after `RemoteComponent.kt` — same Dagger-with-Builder shape, `@BindsInstance` for the OAuth client ID + scopes + activity provider, internal impl class, public interface.

**DI shape:**
- `AuthComponent.Dependencies` takes `httpExecutor: HttpExecutor` (provided by `RemoteComponent`) + `secureKeyValueStore: SecureKeyValueStore` (provided by `ApplicationComponent`).
- `AuthComponent.Builder` takes `@BindsInstance` for: OAuth Web client ID, list of OAuth scopes (`listOf("https://www.googleapis.com/auth/drive.appdata")` for the backup case), and a `currentActivity: () -> Activity?` provider (from `ActivityComponent`).
- `AuthComponent.googleOAuthTokenProvider: OAuthTokenProvider` is the public surface.
- `GoogleOAuthTokenProvider` is `internal` to `zero-auth`. Lint encapsulation rule (existing `DefaultImplMustBeInternal`) enforces this.

Read `PlayIntegrityTokenProvider.kt` for the broad shape — the API surface is different, but the construction pattern and try/catch boundary apply. Behaviour spec:

- Constructor takes `SecureKeyValueStore`, `HttpExecutor`, the OAuth client ID (Web client ID, `@BindsInstance`), the scopes list (`@BindsInstance`), and the activity provider (`@BindsInstance`).
- `signIn()`:
  1. Build `GetGoogleIdOption` with `setServerClientId(clientId)` and `setFilterByAuthorizedAccounts(false)`.
  2. Call `CredentialManager.create(context).getCredential(...)` against the activity context. **Note:** Credential Manager needs an `Activity` context, not the application context. We resolve this by deferring construction — see Task 7 wiring.
  3. Parse the resulting `GoogleIdTokenCredential` to extract the Google account email + an authorization grant.
  4. Use the grant to call Google's OAuth token endpoint via `httpExecutor` with `grant_type=authorization_code`, `code=<grant>`, `client_id=<clientId>`, `redirect_uri=` (PKCE flow for installed apps).
  5. Store the refresh token in `secureKeyValueStore` under key `"drive.refresh_token"`. Store the account label under `"drive.account_label"`.
  6. Cache the access token in memory.
  7. Update `isSignedIn` flow to emit `true`.
  8. Return `OAuthTokenProvider.Result.Success(accountLabel)`.
- `getAccessToken()`:
  - If in-memory token is valid (not expired), return it.
  - Otherwise read refresh token from `secureKeyValueStore`. If null → return null.
  - Call OAuth refresh endpoint via `httpExecutor`. On success cache + return. On failure → return null.
- `revoke()`:
  - Read refresh token. If present, call `https://oauth2.googleapis.com/revoke?token=<>` via `httpExecutor`.
  - Remove both keys from `secureKeyValueStore`.
  - Clear in-memory token.
  - Update `isSignedIn` flow to emit `false`.
- `isSignedIn`: a `Flow<Boolean>` backed by a `MutableStateFlow` initialized from `secureKeyValueStore.get("drive.refresh_token") != null` at construction.

Required scope string (constant in this file): `"https://www.googleapis.com/auth/drive.appdata"`.

**Activity vs Application context:** Credential Manager `getCredential` requires an Activity context. We solve this by:
- `GoogleOAuthTokenProvider` doesn't hold a `Context` directly. Instead it takes a `currentActivity: () -> Activity?` lambda.
- That lambda is provided from `ActivityComponent` and bound via `@BindsInstance`. `MainActivity` populates a static-or-coroutine-state `WeakReference<Activity>` on `onResume` / clears on `onPause` — same pattern as `AndroidBiometricAuthenticator` uses today (read it first).
- If lambda returns null when `signIn()` is called, return `Result.Failure(BackupError.Unknown("Sign-in requires foreground activity"))`.

- [ ] **Step 1: Implement** the class per the spec above. ~200 LOC.

- [ ] **Step 2: Define `AuthComponent`**

```kotlin
@AuthScope
@dagger.Component(
    modules = [AuthComponent.Module::class],
    dependencies = [AuthComponent.Dependencies::class],
)
interface AuthComponent {

    val googleOAuthTokenProvider: com.hluhovskyi.zero.auth.OAuthTokenProvider

    interface Dependencies {
        val httpExecutor: com.hluhovskyi.zero.http.HttpExecutor
        val secureKeyValueStore: com.hluhovskyi.zero.security.SecureKeyValueStore
    }

    @dagger.Component.Builder
    interface Builder {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun googleOauthClientId(@GoogleOAuthClientId clientId: String): Builder

        @BindsInstance
        fun googleOauthScopes(@GoogleOAuthScopes scopes: List<String>): Builder

        @BindsInstance
        fun currentActivityProvider(@CurrentActivity provider: () -> android.app.Activity?): Builder

        fun build(): AuthComponent
    }

    @dagger.Module
    object Module {
        @Provides
        @AuthScope
        internal fun googleOAuthTokenProvider(
            secureKeyValueStore: SecureKeyValueStore,
            httpExecutor: HttpExecutor,
            @GoogleOAuthClientId clientId: String,
            @GoogleOAuthScopes scopes: List<String>,
            @CurrentActivity currentActivity: () -> android.app.Activity?,
        ): OAuthTokenProvider = GoogleOAuthTokenProvider(
            secureKeyValueStore = secureKeyValueStore,
            httpExecutor = httpExecutor,
            clientId = clientId,
            scopes = scopes,
            currentActivity = currentActivity,
        )
    }
}
```

Three new `@Qualifier` annotations (`GoogleOAuthClientId`, `GoogleOAuthScopes`, `CurrentActivity`) and one `@Scope` (`AuthScope`) — all in the same file (mirroring how `RemoteComponent` colocates its qualifiers).

- [ ] **Step 3: Lint encapsulation** — `OAuthTokenProvider` is already a `zero-api` interface; no Android types leak through the `AuthComponent` public surface. Consider adding an `AuthComponentEncapsulation` lint rule analogous to `RemoteComponentEncapsulation` to enforce this going forward (out of scope for v1 but worth a tracking note).

- [ ] **Step 4: Commit**

```bash
git add zero-auth/src/main/java/com/hluhovskyi/zero/auth/
git commit -m "backup(auth): GoogleOAuthTokenProvider + AuthComponent with Credential Manager sign-in"
```

---

### Task 6: `DriveBackupClient`

**Files:**
- Create: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveBackupClient.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/DriveBackupClientTest.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeHttpExecutor.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeOAuthTokenProvider.kt`

Pure-Kotlin implementation of `BackupClient`. Composes `HttpExecutor` + `OAuthTokenProvider` + `BackupEnvelopeSerializer`. Knows Drive REST contract:

- File name: `"zero-backup.json"`
- Folder: `appDataFolder` (Drive special name)
- List query: `q=name='zero-backup.json'&spaces=appDataFolder`
- Multipart upload with metadata `{"name":"zero-backup.json","parents":["appDataFolder"]}` and content type `application/json`.
- Always sends `Authorization: Bearer <accessToken>`.

- [ ] **Step 1: Fakes** — `FakeHttpExecutor` records the last request and returns a programmable response. `FakeOAuthTokenProvider` returns a configured token or null.

- [ ] **Step 2: TDD — failing tests** covering:
  - `upload` happy path: serializes envelope, calls POST multipart, parses response into `BackupMetadata`.
  - `upload` when no token: returns `Failure(AuthExpired)` without HTTP call.
  - `upload` on 401: returns `Failure(AuthExpired)`.
  - `upload` on 403/quota: returns `Failure(QuotaExceeded)`.
  - `upload` on 5xx: returns `Failure(Unknown(...))`.
  - `latest` happy path: list call returns one file, parsed into metadata.
  - `latest` empty: returns `NotFound`.
  - `download` happy path: GET with `alt=media`, deserialize envelope.
  - `download` unknown format: returns `Failure(ParseFailure)`.
  - `delete` happy path: DELETE call, returns Success with empty-ish metadata.

- [ ] **Step 3: Run failing tests**

- [ ] **Step 4: Implement** `DriveBackupClient`. ~250 LOC. Internal helpers: `authHeader()` returns the bearer-or-null, `fileId()` lazily resolves the existing file id via `latest()` (cached). Constants for endpoint URLs.

- [ ] **Step 5: Re-run tests — PASS**

- [ ] **Step 6: Commit**

```bash
git add zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveBackupClient.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/DriveBackupClientTest.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeHttpExecutor.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeOAuthTokenProvider.kt
git commit -m "backup: add DriveBackupClient with multipart upload + REST contract tests"
```

---

### Task 7: Wire AuthComponent + DriveBackupClient into `ApplicationComponent` (replace `NoopBackupClient`)

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/MainActivity.kt`
- Delete: `app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt`

- [ ] **Step 1: Add `buildConfigField` for OAuth client ID**

In `app/build.gradle` `defaultConfig`, add:

```groovy
buildConfigField "String", "DRIVE_OAUTH_CLIENT_ID",
    "\"${System.getenv("DRIVE_OAUTH_CLIENT_ID") ?: localProps['driveOauthClientId'] ?: ""}\""
```

Document in `local.gradle.properties` example (in `feedback-infra.md` and a new note in the design doc) that `driveOauthClientId` is the **Web client ID** from Google Cloud Console (Android-type OAuth clients are auto-derived from package name + SHA-1; the Web client ID is what `setServerClientId(...)` needs).

- [ ] **Step 2: Plumb `currentActivity` provider**

In `ActivityComponent`, add a `WeakReference<Activity>` field updated by `MainActivity.onResume` (set) / `onPause` (clear if pointing to this activity). Expose `fun currentActivity(): Activity?`. Inject this into `ApplicationComponent` via `Dependencies` is awkward — instead, expose it via `ActivityComponent` and pull it from there. **Read `AndroidBiometricAuthenticator.kt` first to see how the existing Activity-aware injection pattern works** — replicate it; do not invent a new approach.

- [ ] **Step 3: Build `AuthComponent` in `ApplicationComponent.Module`**

  Add a new `AuthModule` (mirroring the existing `RemoteModule`) with:

```kotlin
@dagger.Module
internal object AuthModule {

    private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    @Provides
    @ApplicationScope
    fun authComponent(
        component: ApplicationComponent,        // satisfies AuthComponent.Dependencies
        activityComponent: ActivityComponent,    // for currentActivity provider
    ): AuthComponent = AuthComponent.builder()
        .dependencies(component)
        .googleOauthClientId(BuildConfig.DRIVE_OAUTH_CLIENT_ID)
        .googleOauthScopes(listOf(DRIVE_APPDATA_SCOPE))
        .currentActivityProvider { activityComponent.currentActivity() }
        .build()

    @Provides
    fun googleOAuthTokenProvider(
        authComponent: AuthComponent,
    ): OAuthTokenProvider = authComponent.googleOAuthTokenProvider
}
```

`ApplicationComponent` must now implement `AuthComponent.Dependencies` — which requires it to expose `httpExecutor: HttpExecutor` (from `RemoteComponent`) and `secureKeyValueStore: SecureKeyValueStore` (already provided in Task 4). Add `httpExecutor` to `ApplicationComponent`'s abstract overrides similarly to how `feedbackService` is currently exposed.

- [ ] **Step 4: Replace the `BackupClient` provider in `ApplicationComponent.Module`**

  - Remove the `NoopBackupClient` provider.
  - Add a `@Provides BackupClient` that constructs `DriveBackupClient` inline:

```kotlin
@Provides
@ApplicationScope
fun backupClient(
    httpExecutor: HttpExecutor,                    // from RemoteComponent
    oauthTokenProvider: OAuthTokenProvider,         // from AuthComponent
    envelopeSerializer: BackupEnvelopeSerializer,   // from BackupComponent
): BackupClient = DriveBackupClient(
    httpExecutor = httpExecutor,
    oauthTokenProvider = oauthTokenProvider,
    envelopeSerializer = envelopeSerializer,
)
```

This is where the cross-component composition happens. `BackupComponent` is downstream — it receives the resulting `BackupClient` via its existing `Dependencies` interface (from Phase 1 Task 5).

- [ ] **Step 5: Delete `NoopBackupClient.kt`**

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/
git rm app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt
git commit -m "backup(app): wire AuthComponent + DriveBackupClient, drop NoopBackupClient"
```

---

### Task 8: Manual end-to-end smoke test

Read [`docs/agents/feedback-infra.md`](../../agents/feedback-infra.md) §"Debug builds" and §"Verifying changes" — the same dev-device setup applies, with adjustments below.

Prereqs (done by the human running the test, not the executing agent):
1. Create a Google Cloud Project (or reuse the existing one used for Play Integrity).
2. Enable Google Drive API in the project.
3. Create an **OAuth Web client** in Credentials → get the Web client ID.
4. Create an **OAuth Android client** for the debug keystore: package `com.hluhovskyi.zero`, SHA-1 from `./gradlew signingReport`.
5. Put the Web client ID in `local.gradle.properties` as `driveOauthClientId=...`.
6. Configure the OAuth consent screen with `https://www.googleapis.com/auth/drive.appdata` scope and add your dev Google account as a test user.

Then:

- [ ] **Step 1: Add a temporary developer entry point**

Add a `// TODO: remove in Phase 3` button at the bottom of the Settings screen labelled "DEV: Test backup". On tap, it should:
1. Call `oauthTokenProvider.signIn()`.
2. On success, call `backupUseCase.perform(BackupNow)`.
3. Observe `backupUseCase.state` and log transitions via `Timber.d`.

- [ ] **Step 2: Run on device**

Install the debug build, tap the DEV button. Walk through the consent prompt. Confirm logcat shows `Idle → Uploading → Idle` with `lastSuccessAt` set.

- [ ] **Step 3: Verify file is in Drive**

Run a one-off `curl` against Drive REST with a separately-obtained dev token (or use the Drive API explorer): list `spaces=appDataFolder`. Confirm `zero-backup.json` is present and roughly the expected size (~3-30 KB for a typical dataset).

- [ ] **Step 4: Verify content** by manually downloading via API explorer and `jq '.format, .snapshot.userId, .snapshot.transactions | length'`.

- [ ] **Step 5: Remove the DEV button** (commit in Phase 3 when real UI lands; for now, push it as a separate revert-pending commit).

- [ ] **Step 6: Commit the DEV button removal note**

```bash
git commit --allow-empty -m "backup: manual smoke test of Drive upload verified (no code change in this commit)"
```

---

## Verification

```bash
./gradlew :zero-backup:test 2>&1 | tail -10
./gradlew :zero-remote:testDebugUnitTest 2>&1 | tail -10
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

All four MUST pass. The Phase 2 PR also requires manual confirmation of the smoke test above (Task 8) — note this in the PR body, with a screenshot of the logcat transitions if practical.

## Out of Scope

- Real UI (settings detail screen) — Phase 3.
- WorkManager-driven auto-backup — Phase 4.
- Restore flow — Phase 5.
- Account switching — v2.

## Security Notes

- Refresh tokens in `zero_secure_prefs` are Keystore-wrapped via `EncryptedSharedPreferences`. Per `feedback_ships_in_binary_not_secret`, the OAuth Web client ID is hardcoded with env override — it's not a secret.
- Per `feedback_dont_paste_values_in_public_artifacts`: never restate the OAuth client ID in the PR description or this doc. The mechanism is documented; values live in `local.gradle.properties` (dev) / GitHub Actions vars (release).
