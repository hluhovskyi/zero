# Phase 2 — Drive REST Client + OAuth

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Phase 1's `NoopBackupClient` with a real Drive REST implementation backed by Credential Manager OAuth, OkHttp transport, and EncryptedSharedPreferences. End-to-end against a real Google account by the end of the phase. Still no UI — exercised via a temporary developer entry point.

**Architecture:** Three new Android-side implementations of `zero-api` interfaces:
- `OkHttpHttpExecutor` and `DriveOAuthTokenProvider` — `internal` to `zero-remote`, provided via `RemoteComponent`.
- `AndroidSecureKeyValueStore` — lives in `app/`, provided directly from `ApplicationComponent` (not networked, so it doesn't belong in `zero-remote`). `DriveOAuthTokenProvider` receives it via an expanded `RemoteComponent.Dependencies`.

`DriveBackupClient` (pure Kotlin in `zero-backup`) composes them. `ApplicationComponent` wires it all and replaces the noop.

**Tech Stack:** OkHttp (existing), kotlinx-serialization (existing), `androidx.credentials` (new), `com.google.android.libraries.identity.googleid` (new), `androidx.security:security-crypto` (new for EncryptedSharedPreferences).

**Spec:** [Spec §Drive Integration](../specs/2026-05-21-gdrive-backup-design.md#drive-integration) and [§Security Model](../specs/2026-05-21-gdrive-backup-design.md#security-model)

**Structural analogs:**
- `PlayIntegrityTokenProvider.kt` → `DriveOAuthTokenProvider.kt`
- `OkHttpFeedbackService.kt` → `OkHttpHttpExecutor.kt` + `DriveBackupClient.kt`
- `RemoteComponent.kt` (Builder + `@BindsInstance` + qualifiers) → additions to `RemoteComponent.kt`

---

### Task 1: Add dependencies

**Files:**
- Modify: `build.gradle` (root, the `deps` map and `versions` map)
- Modify: `zero-remote/build.gradle`
- Modify: `app/build.gradle`

Credential Manager + googleid are sign-in concerns (used by `DriveOAuthTokenProvider` in `zero-remote`) → go into `zero-remote`. `security-crypto` is the EncryptedSharedPreferences lib, used by `AndroidSecureKeyValueStore` which lives in `app` → goes into `app`.

- [ ] **Step 1: Append to the root `deps` map**

```groovy
credentials                : "androidx.credentials:credentials:1.3.0",
credentialsPlayServicesAuth: "androidx.credentials:credentials-play-services-auth:1.3.0",
googleId                   : "com.google.android.libraries.identity.googleid:googleid:1.1.1",
securityCrypto             : "androidx.security:security-crypto:1.1.0-alpha06",
```

- [ ] **Step 2: Add to `zero-remote/build.gradle` dependencies block**

```
implementation deps.credentials
implementation deps.credentialsPlayServicesAuth
implementation deps.googleId
```

- [ ] **Step 3: Add to `app/build.gradle` dependencies block**

```
implementation deps.securityCrypto
```

- [ ] **Step 4: Build to confirm resolution**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add build.gradle zero-remote/build.gradle app/build.gradle
git commit -m "backup: add Credential Manager (zero-remote) + EncryptedSharedPreferences (app) deps"
```

---

### Task 2: `OkHttpHttpExecutor` (transport adapter)

**Files:**
- Create: `zero-remote/src/main/java/com/hluhovskyi/zero/drive/OkHttpHttpExecutor.kt`
- Create: `zero-remote/src/test/java/com/hluhovskyi/zero/drive/OkHttpHttpExecutorTest.kt`

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
git add zero-remote/src/main/java/com/hluhovskyi/zero/drive/OkHttpHttpExecutor.kt \
        zero-remote/src/test/java/com/hluhovskyi/zero/drive/OkHttpHttpExecutorTest.kt \
        zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt
git commit -m "backup(remote): add OkHttpHttpExecutor + RemoteComponent provide"
```

---

### Task 3: `AndroidSecureKeyValueStore` + Auto Backup exclusion

`AndroidSecureKeyValueStore` lives in `app/` (not `zero-remote`) because EncryptedSharedPreferences is not a networked concern. It's provided directly from `ApplicationComponent`, like `ImageLoader` or `idGenerator` are today. `DriveOAuthTokenProvider` (in `zero-remote`) receives it via `RemoteComponent.Dependencies` — see Task 4.

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

- [ ] **Step 5: Skip instrumented test for v1** — EncryptedSharedPreferences requires an actual device/emulator. Unit-test coverage for this is impractical. Verification is end-to-end in Task 7.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/security/AndroidSecureKeyValueStore.kt \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt \
        app/src/main/res/xml/backup_rules.xml \
        app/src/main/res/xml/data_extraction_rules.xml
git commit -m "backup(app): AndroidSecureKeyValueStore + exclude zero_secure_prefs from Auto Backup"
```

---

### Task 4: `DriveOAuthTokenProvider`

**Files:**
- Create: `zero-remote/src/main/java/com/hluhovskyi/zero/drive/DriveOAuthTokenProvider.kt`

This is the most platform-specific class in the phase. Read `PlayIntegrityTokenProvider.kt` for the broad shape, but the API surface is different.

**DI shape:** `AndroidSecureKeyValueStore` lives in `app` (Task 3) and is provided by `ApplicationComponent`. `DriveOAuthTokenProvider` lives in `zero-remote` and needs `SecureKeyValueStore`. So `RemoteComponent.Dependencies` gains a new field `val secureKeyValueStore: SecureKeyValueStore` — `ApplicationComponent` satisfies this from its own provider. `HttpExecutor` remains internal to `RemoteComponent` (no cross-component dependency needed for it).

Behaviour spec:

- Constructor takes `Context`, `SecureKeyValueStore`, `HttpExecutor`, and the OAuth client ID (`@BindsInstance` qualifier, see Task 6).
- `signIn()`:
  1. Build `GetGoogleIdOption` with `setServerClientId(clientId)` and `setFilterByAuthorizedAccounts(false)`.
  2. Call `CredentialManager.create(context).getCredential(...)` against the activity context. **Note:** Credential Manager needs an `Activity` context, not the application context. We resolve this by deferring construction — see Task 6 wiring.
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
- `DriveOAuthTokenProvider` doesn't hold a `Context` directly. Instead it takes a `currentActivity: () -> Activity?` lambda.
- That lambda is provided from `ActivityComponent` and bound via `@BindsInstance`. `MainActivity` populates a static-or-coroutine-state `WeakReference<Activity>` on `onResume` / clears on `onPause` — same pattern as `AndroidBiometricAuthenticator` uses today (read it first).
- If lambda returns null when `signIn()` is called, return `Result.Failure(BackupError.Unknown("Sign-in requires foreground activity"))`.

- [ ] **Step 1: Implement** the class per the spec above. ~200 LOC.

- [ ] **Step 2: Provide via `RemoteComponent`**

  Expand `RemoteComponent.Dependencies` to include:

  ```kotlin
  val secureKeyValueStore: SecureKeyValueStore
  ```

  Add `val oauthTokenProvider: OAuthTokenProvider` to the public interface; add an `@RemoteScope @Provides internal` factory in the module. Also add a new `@BindsInstance` slot on the Builder for the OAuth client ID:

```kotlin
@Qualifier @Retention(AnnotationRetention.SOURCE) private annotation class DriveOAuthClientId
// ...
@BindsInstance fun driveOauthClientId(@DriveOAuthClientId clientId: String): Builder
@BindsInstance fun driveActivityProvider(@DriveActivity provider: () -> android.app.Activity?): Builder
```

- [ ] **Step 3: Update `RemoteComponent` lint encapsulation rule** if necessary — `OAuthTokenProvider` is already a `zero-api` interface, so this should be safe. Run `./gradlew :lint-rules:test` and `./gradlew :zero-remote:lint` to confirm.

- [ ] **Step 4: Commit**

```bash
git add zero-remote/src/main/java/com/hluhovskyi/zero/drive/DriveOAuthTokenProvider.kt \
        zero-remote/src/main/java/com/hluhovskyi/zero/RemoteComponent.kt
git commit -m "backup(remote): add DriveOAuthTokenProvider with Credential Manager sign-in + token refresh"
```

---

### Task 5: `DriveBackupClient`

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

### Task 6: Wire DriveBackupClient into `ApplicationComponent` (replace `NoopBackupClient`)

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

- [ ] **Step 3: In `ApplicationComponent.Module`**, replace the `@Provides` for `BackupClient`:

  - Remove the `NoopBackupClient` provider.
  - Provide `DriveBackupClient` from `zero-backup`, composing `RemoteComponent.httpExecutor` and `RemoteComponent.oauthTokenProvider` (also exposed via the existing `Dependencies` linkage).

- [ ] **Step 4: In `RemoteModule`**, the `remoteComponent` provider gains two more builder calls:

```kotlin
.driveOauthClientId(BuildConfig.DRIVE_OAUTH_CLIENT_ID)
.driveActivityProvider { activityComponent.currentActivity() }
```

The activity provider needs `ActivityComponent` in scope — this is the trickiest part. **Read how `BiometricAuthenticator` is currently injected and replicate the exact pattern**; do not invent a new injection path.

`ApplicationComponent` already implements `RemoteComponent.Dependencies`. Because Task 3 added `SecureKeyValueStore` to `ApplicationComponent.Module`, the new `secureKeyValueStore: SecureKeyValueStore` field on `RemoteComponent.Dependencies` is satisfied automatically — Dagger picks it up via the existing dependencies bridge. No extra wiring required.

- [ ] **Step 5: Delete `NoopBackupClient.kt`**

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/
git rm app/src/main/java/com/hluhovskyi/zero/backup/NoopBackupClient.kt
git commit -m "backup(app): wire DriveBackupClient + OAuth client ID buildConfigField"
```

---

### Task 7: Manual end-to-end smoke test

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

All four MUST pass. The Phase 2 PR also requires manual confirmation of the smoke test above (Task 7) — note this in the PR body, with a screenshot of the logcat transitions if practical.

## Out of Scope

- Real UI (settings detail screen) — Phase 3.
- WorkManager-driven auto-backup — Phase 4.
- Restore flow — Phase 5.
- Account switching — v2.

## Security Notes

- Refresh tokens in `zero_secure_prefs` are Keystore-wrapped via `EncryptedSharedPreferences`. Per `feedback_ships_in_binary_not_secret`, the OAuth Web client ID is hardcoded with env override — it's not a secret.
- Per `feedback_dont_paste_values_in_public_artifacts`: never restate the OAuth client ID in the PR description or this doc. The mechanism is documented; values live in `local.gradle.properties` (dev) / GitHub Actions vars (release).
