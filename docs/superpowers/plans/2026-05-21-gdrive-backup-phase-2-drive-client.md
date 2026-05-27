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
- `RemoteComponent.kt` (Dagger component with Dependencies + qualifiers) → `AuthComponent.kt` (same general shape, but no `@BindsInstance` — see Task 5 for the slimmer Builder). Plus small additions to `RemoteComponent.kt` (HttpExecutor only).

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
- OAuth scopes are a compile-time constant inside `AuthComponent.Module` (a `@Provides`
  returning `listOf("...drive.appdata")`), not a runtime parameter. Future Google integrations
  needing different scopes either recompile or build a sibling component.
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

- [ ] **Step 2: Add to `zero-auth/build.gradle`**

Dependencies block:

```
implementation deps.credentials
runtimeOnly    deps.credentialsPlayServicesAuth
implementation deps.googleId
```

`credentialsPlayServicesAuth` is the runtime Play Services adapter for Credential Manager — no code references it at compile time; it registers via manifest merger. `runtimeOnly` makes this explicit.

`defaultConfig` block: move the OAuth client ID `buildConfigField` from `app/build.gradle` into here, since the value conceptually belongs to `zero-auth` (Google sign-in concern):

```groovy
buildConfigField "String", "DRIVE_OAUTH_CLIENT_ID",
    "\"${System.getenv("DRIVE_OAUTH_CLIENT_ID") ?: localProps['driveOauthClientId'] ?: ""}\""
```

Enable `buildFeatures.buildConfig = true` in the android block (zero-remote already does this for FEEDBACK_*). Wire `localProps` reader the same way `app/build.gradle` does today.

- [ ] **Step 3: Add to `app/build.gradle` dependencies block**

```
implementation deps.securityCrypto
```

Remove the now-redundant `DRIVE_OAUTH_CLIENT_ID` `buildConfigField` from `app/build.gradle`'s `defaultConfig` — it lives in `zero-auth` now.

- [ ] **Step 4: Build to confirm resolution**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add build.gradle zero-auth/build.gradle app/build.gradle
git commit -m "backup: deps + DRIVE_OAUTH_CLIENT_ID buildConfigField in zero-auth"
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

- [ ] **Step 5: Skip instrumented test for v1** — EncryptedSharedPreferences requires an actual device/emulator. Unit-test coverage for this is impractical. Verification is end-to-end in Task 9.

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

> **⚠️ Superseded as built.** The Credential-Manager + token-endpoint + refresh-token design
> described below proved unworkable for a serverless public client (no way to mint a refresh
> token without a backend secret; `GetGoogleIdOption` only authenticates). The implementation
> instead uses the **Google Identity Authorization API** (`play-services-auth`) to mint
> `drive.appdata` access tokens directly — no refresh token, no token endpoint, no `HttpExecutor`,
> no Web client ID, no `buildConfigField`. `AuthComponent.Dependencies` is `{ context,
> secureKeyValueStore, currentActivityProvider }`. There are no `zero-auth` unit tests (Play
> services isn't unit-testable). See the spec's §Auth design-correction. The text below is kept
> for historical context.

**Files:**
- Create: `zero-auth/src/main/java/com/hluhovskyi/zero/auth/GoogleOAuthTokenProvider.kt`
- Create: `zero-auth/src/main/java/com/hluhovskyi/zero/auth/AuthComponent.kt`

`AuthComponent` is the DI root for `zero-auth`. Model after `RemoteComponent.kt` — Dagger component with `Dependencies` interface, `internal` impl class, public interface. **Difference from RemoteComponent:** the Builder collapses to just `dependencies(...).build()` — no `@BindsInstance` parameters. Config values (OAuth client ID, scopes) are `@Provides` inside the Module; the activity provider arrives via `Dependencies` (a single reusable `() -> Activity?` provided at app scope).

**DI shape:**
- `AuthComponent.Dependencies` takes `httpExecutor: HttpExecutor` (provided by `RemoteComponent`), `secureKeyValueStore: SecureKeyValueStore` (provided by `ApplicationComponent`), and `currentActivityProvider: () -> Activity?` (provided by `ApplicationComponent` via `CurrentActivityTracker` — see Task 7).
- `AuthComponent.Builder` takes no `@BindsInstance` parameters.
- `AuthComponent.Module` provides the OAuth client ID via `BuildConfig.DRIVE_OAUTH_CLIENT_ID` (read from `zero-auth/build.gradle`'s buildConfigField) and scopes as a hardcoded `@Provides`.
- `AuthComponent.googleOAuthTokenProvider: OAuthTokenProvider` is the public surface.
- `GoogleOAuthTokenProvider` is `internal` to `zero-auth`. Lint encapsulation rule (existing `DefaultImplMustBeInternal`) enforces this.

Read `PlayIntegrityTokenProvider.kt` for the broad shape — the API surface is different, but the construction pattern and try/catch boundary apply. Behaviour spec:

- Constructor takes `SecureKeyValueStore`, `HttpExecutor`, OAuth client ID, scopes list, and the activity provider lambda. All five come from Dagger `@Provides` in `AuthComponent.Module`; no `@BindsInstance` plumbing.
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

**Activity vs Application context:** Credential Manager `getCredential` requires an Activity context. We solve this with a **single reusable current-activity provider** at the `app/` layer (introduced in Task 7), not via `@BindsInstance` on AuthComponent's Builder:

- A new `CurrentActivityTracker` in `app/.../activity/` registers `Application.ActivityLifecycleCallbacks`, holds a `WeakReference<Activity>`, exposes `current(): Activity?`.
- `ApplicationComponent.Module` provides a `() -> Activity?` lambda backed by that tracker, app-scoped.
- `AuthComponent.Dependencies` declares `val currentActivityProvider: () -> Activity?` — same shape as `httpExecutor` and `secureKeyValueStore`.
- `GoogleOAuthTokenProvider` invokes the lambda; if it returns null when `signIn()` is called, returns `Result.Failure(BackupError.Unknown("Sign-in requires foreground activity"))`.

Reusable in the future for any other Android-platform code that needs the current foreground activity (biometric flow migration, etc. — out of scope for this PR).

**Why this matters for `getAccessToken()`**: `AuthComponent` stays at app scope (not activity scope) so background work (WorkManager auto-backup) can mint access tokens without a foreground activity. The activity is only needed for `signIn()`, which is UI-triggered.

- [ ] **Step 1: Implement** the class per the spec above. ~200 LOC.

- [ ] **Step 2: Define `AuthComponent`**

Client ID + scopes are scoped inside the Module (not Builder params). Client ID comes from `zero-auth`'s own `BuildConfig` (Step 2 of Task 2 moved the buildConfigField here). Scopes are a compile-time constant. The Builder collapses to just `dependencies(...).build()`.

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
        val currentActivityProvider: () -> android.app.Activity?
    }

    @dagger.Component.Builder
    interface Builder {
        fun dependencies(dependencies: Dependencies): Builder
        fun build(): AuthComponent
    }

    @dagger.Module
    object Module {
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

        @Provides
        @AuthScope
        @GoogleOAuthClientId
        internal fun clientId(): String = BuildConfig.DRIVE_OAUTH_CLIENT_ID

        @Provides
        @AuthScope
        @GoogleOAuthScopes
        internal fun scopes(): List<String> = listOf(DRIVE_APPDATA_SCOPE)

        @Provides
        @AuthScope
        internal fun googleOAuthTokenProvider(
            secureKeyValueStore: SecureKeyValueStore,
            httpExecutor: HttpExecutor,
            @GoogleOAuthClientId clientId: String,
            @GoogleOAuthScopes scopes: List<String>,
            currentActivityProvider: () -> android.app.Activity?,
        ): OAuthTokenProvider = GoogleOAuthTokenProvider(
            secureKeyValueStore = secureKeyValueStore,
            httpExecutor = httpExecutor,
            clientId = clientId,
            scopes = scopes,
            currentActivity = currentActivityProvider,
        )
    }
}
```

Two `@Qualifier` annotations (`GoogleOAuthClientId`, `GoogleOAuthScopes`) plus one `@Scope` (`AuthScope`) — colocated in the same file (mirroring how `RemoteComponent` colocates its qualifiers). The `currentActivityProvider` lambda is unqualified (no qualifier needed — type `() -> Activity?` is unique in the graph at app scope; if a second producer ever appears, add a qualifier then).

- [ ] **Step 3: Lint encapsulation** — `OAuthTokenProvider` is already a `zero-api` interface; no Android types leak through the `AuthComponent` public surface. Consider adding an `AuthComponentEncapsulation` lint rule analogous to `RemoteComponentEncapsulation` to enforce this going forward (out of scope for v1 but worth a tracking note).

- [ ] **Step 4: Tests against `MockWebServer` + fakes**

`GoogleOAuthTokenProvider` has Credential-Manager-flavored bits that can't be unit-tested (system UI), but the **token-management half** is fully testable. Sign-in itself is exercised in the manual smoke test (Task 9).

Create `zero-auth/src/test/java/com/hluhovskyi/zero/auth/GoogleOAuthTokenProviderTest.kt`. Use `MockWebServer` for the `HttpExecutor` (via a small adapter) + an in-memory `FakeSecureKeyValueStore`. Cover:

1. `getAccessToken()` returns the cached in-memory token if not expired.
2. `getAccessToken()` reads refresh token from store and calls `oauth2.googleapis.com/token` endpoint when access token missing.
3. `getAccessToken()` returns `null` if no refresh token is present.
4. `getAccessToken()` returns `null` and clears in-memory state if refresh endpoint returns 400 `invalid_grant` (signed-out server-side).
5. `revoke()` calls `oauth2.googleapis.com/revoke`, clears both keys from store, flips `isSignedIn` to false.
6. `revoke()` clears local state even if the revoke HTTP call fails (we can't leave a dangling local credential).
7. `isSignedIn` initial value derives from `secureKeyValueStore.get("drive.refresh_token") != null` at construction.

Skipped (manual-only):
- `signIn()` — Credential Manager bottom sheet is system UI, can't be unit-tested.

- [ ] **Step 5: Commit**

```bash
git add zero-auth/src/main/java/com/hluhovskyi/zero/auth/ \
        zero-auth/src/test/java/com/hluhovskyi/zero/auth/
git commit -m "backup(auth): GoogleOAuthTokenProvider + AuthComponent + token-mgmt tests"
```

---

### Task 6: `DriveBackupClient` + `DriveComponent`

`DriveBackupClient` is the Drive-specific impl of `BackupClient`. It lives inside a new sibling component `DriveComponent` in `zero-backup` — same module as `BackupComponent`, but a separate factory because the two have different responsibilities. `BackupComponent` is backend-agnostic (orchestration); `DriveComponent` owns Drive-flavored impls (this task: `DriveBackupClient`; Phase 5: `DriveSnapshotParser`). Future Dropbox/WebDAV/etc. backends would each get a sibling component (`DropboxComponent`, etc.) without touching `BackupComponent`.

`DriveComponent` is **KMP-pure** (no Android imports, no OkHttp imports) — Drive REST is just HTTPS endpoints; the impl reaches them through `HttpExecutor` + `OAuthTokenProvider` interfaces from `zero-api`.

**Files:**
- Create: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveComponent.kt`
- Create: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveBackupClient.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/DriveBackupClientTest.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeHttpExecutor.kt`
- Create: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeOAuthTokenProvider.kt`

Pure-Kotlin implementation of `BackupClient`. Composes `HttpExecutor` + `OAuthTokenProvider` + an internal `BackupEnvelopeSerializer` (private to `DriveBackupClient`, no external surface). Knows Drive REST contract:

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

- [ ] **Step 6: Implement `DriveComponent`** — manual DI factory, sibling of `BackupComponent`. Model after `BackupComponent.kt` shape.

```kotlin
package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.http.HttpExecutor

interface DriveComponent {

    interface Dependencies {
        val httpExecutor: HttpExecutor
        val oauthTokenProvider: OAuthTokenProvider
    }

    val backupClient: BackupClient
    // driveSnapshotParser added in Phase 5

    class Factory(private val dependencies: Dependencies) {
        fun create(): DriveComponent = DefaultDriveComponent(dependencies)
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}

internal class DefaultDriveComponent(dependencies: DriveComponent.Dependencies) : DriveComponent {

    private val envelopeSerializer = BackupEnvelopeSerializer()

    override val backupClient: BackupClient by lazy {
        DriveBackupClient(
            httpExecutor = dependencies.httpExecutor,
            oauthTokenProvider = dependencies.oauthTokenProvider,
            envelopeSerializer = envelopeSerializer,
        )
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveBackupClient.kt \
        zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveComponent.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/DriveBackupClientTest.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeHttpExecutor.kt \
        zero-backup/src/test/java/com/hluhovskyi/zero/backup/FakeOAuthTokenProvider.kt
git commit -m "backup: DriveBackupClient + DriveComponent factory in zero-backup"
```

---

### Task 7: Wire AuthComponent + DriveComponent into `ApplicationComponent` (replace `NoopBackupClient`)

**Files:**
- Create: `app/src/main/java/com/hluhovskyi/zero/activity/CurrentActivityTracker.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/MainApplication.kt` (register the tracker on app start)
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`
- Delete: `app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt`

The OAuth client ID buildConfigField already lives in `zero-auth/build.gradle` from Task 2 — no `app/build.gradle` changes needed here. `local.gradle.properties` example doc note (in `feedback-infra.md` style): `driveOauthClientId` is the **Web client ID** from Google Cloud Console (Android-type OAuth clients are auto-derived from package name + SHA-1; the Web client ID is what `setServerClientId(...)` needs).

- [ ] **Step 1: Implement `CurrentActivityTracker`** — single reusable foreground-activity provider.

```kotlin
package com.hluhovskyi.zero.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

class CurrentActivityTracker(application: Application) : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null

    init { application.registerActivityLifecycleCallbacks(this) }

    fun current(): Activity? = current?.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
```

App-scoped. Single source of truth for "what's the foreground activity?" Reusable by any future Android code that needs activity context (biometric flow migration, etc. — out of scope here).

- [ ] **Step 2: Provide it in `ApplicationComponent.Module`**

```kotlin
@Provides
@ApplicationScope
fun currentActivityTracker(application: Application): CurrentActivityTracker =
    CurrentActivityTracker(application)

@Provides
@ApplicationScope
fun currentActivityProvider(tracker: CurrentActivityTracker): () -> Activity? =
    { tracker.current() }
```

`ApplicationComponent` already has `Application` in scope (or `Context` from which Application is derived via cast). Add `Application` to `ApplicationComponent.Dependencies` if it isn't there.

- [ ] **Step 3: Add `AuthModule` to `ApplicationComponent.Module`**

```kotlin
@dagger.Module
internal object AuthModule {

    @Provides
    @ApplicationScope
    fun authComponent(component: ApplicationComponent): AuthComponent =
        AuthComponent.builder()
            .dependencies(component)
            .build()

    @Provides
    fun oauthTokenProvider(authComponent: AuthComponent): OAuthTokenProvider =
        authComponent.googleOAuthTokenProvider
}
```

`ApplicationComponent` already implements `AuthComponent.Dependencies` because it exposes:
- `httpExecutor` (from `RemoteComponent`)
- `secureKeyValueStore` (Task 4)
- `currentActivityProvider: () -> Activity?` (Step 2 of this task)

No `@BindsInstance` builder calls — all three flow through the `Dependencies` bridge.

Include `AuthModule` in `ApplicationComponent.Module`'s includes list:

```kotlin
@dagger.Module(
    includes = [
        DatabaseModule::class,
        RemoteModule::class,
        AuthModule::class,
    ],
)
object Module { ... }
```

- [ ] **Step 4: Add `DriveComponent` provider** (replace `NoopBackupClient`)

```kotlin
@Provides
@ApplicationScope
fun driveComponent(
    httpExecutor: HttpExecutor,                    // from RemoteComponent
    oauthTokenProvider: OAuthTokenProvider,         // from AuthComponent
): DriveComponent = DriveComponent.factory(object : DriveComponent.Dependencies {
    override val httpExecutor = httpExecutor
    override val oauthTokenProvider = oauthTokenProvider
}).create()

@Provides
fun backupClient(driveComponent: DriveComponent): BackupClient =
    driveComponent.backupClient
```

Remove the `NoopBackupClient` provider added in Phase 1 Task 7.

- [ ] **Step 5: Delete `NoopBackupClient.kt`**

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/CurrentActivityTracker.kt \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git rm app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt
git commit -m "backup(app): CurrentActivityTracker + wire AuthComponent + DriveComponent"
```

---

### Task 8: MockWebServer wire-format integration test

The Phase 2 Task 6 unit tests for `DriveBackupClient` use a `FakeHttpExecutor` — they verify the client's logic but not the actual HTTP bytes that go on the wire. This task adds a test that uses the **real** `OkHttpHttpExecutor` against a `MockWebServer` configured to behave like Drive. Catches OkHttp-level bugs (multipart boundary issues, Content-Type setting, Content-Length on ByteArray bodies) and Drive-shape mistakes (wrong query params, wrong response field names) that the fakes mask.

**Files:**
- Create: `app/src/test/java/com/hluhovskyi/zero/backup/DriveBackupClientWireFormatTest.kt`

Lives in `app/` because `zero-backup` is pure Kotlin and can't pull OkHttp; `app/` already has OkHttp on its classpath via the `zero-remote` dep.

- [ ] **Step 1: Write tests** against `MockWebServer`. Six cases:
  1. **Upload constructs the right multipart request** — POST, `Authorization: Bearer <token>`, `Content-Type: multipart/related; boundary=...`, two parts (metadata JSON with `{"name":"zero-backup.json","parents":["appDataFolder"]}`, content JSON envelope).
  2. **Upload parses the response into `BackupMetadata`** — drive's `{id, name, modifiedTime, size}` shape.
  3. **List endpoint sends the right query** — `q=name='zero-backup.json'&spaces=appDataFolder&fields=files(id,name,modifiedTime,size)`.
  4. **Download sends `alt=media` and parses raw body as envelope.**
  5. **401 surfaces as `BackupError.AuthExpired`.**
  6. **403 with quota body surfaces as `BackupError.QuotaExceeded`.**

Use real `OkHttpClient`, real `OkHttpHttpExecutor`. `OAuthTokenProvider` is faked (returns a static `"test-token"`). The Drive base URL is overridden to `mockWebServer.url("/")` via a constructor parameter on `DriveBackupClient` — add a `baseUrl: String = "https://www.googleapis.com"` constructor default (this is the only production-vs-test difference).

- [ ] **Step 2: Run**

Run: `./gradlew :app:testDebugUnitTest --tests DriveBackupClientWireFormatTest 2>&1 | tail -15`

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hluhovskyi/zero/backup/DriveBackupClientWireFormatTest.kt \
        zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveBackupClient.kt
git commit -m "backup: MockWebServer wire-format test for DriveBackupClient"
```

---

### Task 9: Manual end-to-end smoke test

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

All four MUST pass. The Phase 2 PR also requires manual confirmation of the smoke test above (Task 9) — note this in the PR body, with a screenshot of the logcat transitions if practical.

## Out of Scope

- Real UI (settings detail screen) — Phase 3.
- WorkManager-driven auto-backup — Phase 4.
- Restore flow — Phase 5.
- Account switching — v2.

## Security Notes

- Refresh tokens in `zero_secure_prefs` are Keystore-wrapped via `EncryptedSharedPreferences`. Per `feedback_ships_in_binary_not_secret`, the OAuth Web client ID is hardcoded with env override — it's not a secret.
- Per `feedback_dont_paste_values_in_public_artifacts`: never restate the OAuth client ID in the PR description or this doc. The mechanism is documented; values live in `local.gradle.properties` (dev) / GitHub Actions vars (release).
