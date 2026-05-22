# Google Drive Backup — Design Spec

**Date:** 2026-05-21
**Modules touched:** new `zero-backup` (pure Kotlin), additions to `zero-api`, `zero-remote`, `zero-core`, `app`
**Status:** Approved for implementation

---

## Goals

- Let the user enable automatic, daily, transparent backup of all their finance data to their personal Google Drive.
- Restore that data on a new device after a one-time sign-in.
- Reuse the existing `SyncEngine` and Import flow — no new merge logic, no duplicate review UI.
- Stay KMP-ready from day one: the orchestration and protocol are pure Kotlin; Android-specific concerns (Credential Manager, OkHttp, WorkManager, EncryptedSharedPreferences) live behind interfaces.

## Non-Goals

- End-to-end encryption in v1. The envelope format is versioned so encryption can layer on later without a migration. See **Security model** below.
- Multi-slot history. Single rolling file. Versioned slots are a v2 enhancement.
- Multiple backup destinations (Dropbox, iCloud, WebDAV). Google Drive only.
- Real-time sync. This is periodic backup, not collaborative sync.
- Custom retention policies, family sharing, server-side rate limiting.

## Core Principles

**Reuse `zero-sync`, don't fork it.** Backup is `SyncEngine.export(userId)` → envelope → upload. Restore is download → unwrap → either `SyncEngine.import(snapshot, userId)` (fast path) or the existing Import flow (conflict path). No new merge logic.

**KMP discipline.** All orchestration and protocol code lives in a new pure-Kotlin module (`zero-backup`), mirroring `zero-sync`. Android-specific implementations (auth, transport, secure storage, scheduler) live in `zero-remote` and `app` behind interfaces declared in `zero-api`. The day an iOS port begins, no orchestration code needs to move.

**Format-versioned envelope.** The file written to Drive is `BackupEnvelope { format: Int, snapshot: SyncSnapshot }`. v1 is plaintext. v2 can add an encrypted variant by reading `format` and dispatching — non-breaking for existing v1 backups.

**Single source of truth for state.** A `BackupUseCase` owns the entire backup state (idle / uploading / success / failure / 3-strike-fail). ViewModels are thin projections of it. WorkManager calls it. The settings screen reads it. There is no parallel "is backup running" flag anywhere else.

---

## Architecture

### Module split

```
zero-api                              (pure Kotlin — interfaces & DTOs)
  backup/
    BackupClient.kt                   ← interface: upload, latest, download, delete
    BackupEnvelope.kt                 ← @Serializable { format, snapshot }
    BackupMetadata.kt                 ← @Serializable { backupId, createdAt, byteSize, deviceLabel }
    BackupUseCase.kt                  ← interface: state: Flow<State>, perform(Action)
                                         (app-scoped; no attach — consumers observe, don't own)
    BackupError.kt                    ← sealed taxonomy of failure modes
  auth/
    OAuthTokenProvider.kt             ← interface: getAccessToken, signIn, revoke — generic
                                         over any OAuth flow; scope set at construction
  http/
    HttpExecutor.kt                   ← thin generic interface — no OkHttp leak
  security/
    SecureKeyValueStore.kt            ← interface: get/put/remove String — generic primitive

zero-backup                           (NEW, pure Kotlin JVM — mirrors zero-sync)
  DefaultBackupUseCase.kt             ← state machine; uses SyncEngine + BackupClient
  DriveBackupClient.kt                ← Drive REST contract; uses HttpExecutor + OAuthTokenProvider
  BackupEnvelopeSerializer.kt         ← envelope JSON round-trip
  BackupComponent.kt                  ← DI factory with Dependencies interface
  AGENTS.md                           ← rules: no Android, no OkHttp imports

zero-auth                             (NEW Android module — Google sign-in flow)
  AuthComponent.kt                    ← DI root: exposes googleOAuthTokenProvider
  GoogleOAuthTokenProvider.kt         ← Credential Manager + googleid + token exchange
                                         (internal — implements OAuthTokenProvider; scope-parameterized)
  AGENTS.md                           ← rules: only OAuth concerns; no networking, no UI

zero-remote                           (Android — additions, narrowed)
  http/
    OkHttpHttpExecutor.kt             ← wraps existing OkHttpClient, implements HttpExecutor
                                         (provided via RemoteComponent)

zero-core                             (Android — additions)
  backup/                             ← settings detail screen for Google Drive backup
  imports/DriveSnapshotParser.kt      ← downloads envelope from Drive, returns SyncSnapshot
  imports/DefaultImportUseCase.kt     ← gains "all-new fast path" branch
  welcome/                            ← restore-prompt step before presets

app                                   (Android — wiring + Android-only concerns)
  backup/DriveBackupSchedulerWorker.kt    ← WorkManager periodic worker
  backup/BackupNotificationPresenter.kt   ← system notification on 3 consecutive failures
  security/AndroidSecureKeyValueStore.kt  ← EncryptedSharedPreferences — provided directly
                                              from ApplicationComponent (not zero-remote, since it
                                              isn't a networked concern). DriveOAuthTokenProvider
                                              receives it via RemoteComponent.Dependencies.
  ApplicationComponent.kt             ← wires zero-backup against the Android impls

app/src/main/res/xml/                 ← Android Auto Backup rules (Phase 0)
```

### Module dependency rules

- `zero-backup` depends on `zero-api` only. No Android, no OkHttp, no kotlinx-coroutines-android. Enforced by a new lint rule `BackupModuleEncapsulation` mirroring `RemoteComponentEncapsulation`.
- `zero-remote`'s Drive types (`DriveOAuthTokenProvider`, `OkHttpHttpExecutor`) are `internal`. Public surface stays as today.
- `app` is the only place that wires the Android impls to the interfaces — same pattern as `ApplicationComponent` does for `RemoteComponent` and `SyncComponent` today.

---

## Backup Envelope Format

```json
{
  "format": 1,
  "snapshot": { /* existing SyncSnapshot v1 — version, userId, exportedAt, categories, accounts, transactions, budgets */ }
}
```

- `format`: integer. v1 = plaintext snapshot. v2 (future) = encrypted variant — `{ "format": 2, "kdf": {...}, "nonce": "...", "ciphertext": "..." }`.
- `snapshot`: the existing `SyncSnapshot` exactly as written by `SyncSerializer`. No changes to `SyncSnapshot` or any of its sub-types.
- Serializer lives in `zero-backup` (`BackupEnvelopeSerializer`). Uses the same `Json { ignoreUnknownKeys = true }` policy as `SyncSerializer`. Restore code rejects unknown `format` with a versioned error string identical in shape to the existing `SyncSerializer` version-check.

**Why a separate envelope and not `SyncSnapshot.version = 2`?** Because the envelope is about *transport encoding*, not *snapshot content*. Putting encryption details on `SyncSnapshot` would force every consumer of `SyncSnapshot` (today: file export, file import, ZenMoney parser) to deal with crypto fields they don't care about. The envelope keeps that concern bottled up at the Drive layer.

---

## Drive Integration

### Scope: `drive.appdata`

The OAuth scope `https://www.googleapis.com/auth/drive.appdata` grants access to a hidden per-app folder (`appDataFolder`). Files there are invisible in `drive.google.com`'s UI and cannot be accessed by any other app. Consent prompt wording is the gentlest Drive offers: *"see, edit, create, and delete its own configuration data in your Google Drive."*

### Auth (`zero-auth` module)

Owned by `GoogleOAuthTokenProvider` in the new `zero-auth` module. The provider is generic over Google OAuth — scope (`drive.appdata` for the backup use case) is configured at construction. Future integrations that need Google sign-in (Calendar, Sheets, etc.) reuse this provider with different scopes.

- **Sign in:** Android's `androidx.credentials.CredentialManager` + `com.google.android.libraries.identity.googleid` (`GoogleIdTokenCredential`). User picks an account, grants the configured scope. We receive an OAuth ID token + a Google authorization grant.
- **Token exchange:** the authorization grant is exchanged for an access + refresh token via the standard OAuth 2.0 token endpoint `https://oauth2.googleapis.com/token`. We use `HttpExecutor` (the same one `RemoteComponent` provides for Drive REST calls) — no Google client lib.
- **Storage:** access token is held in memory only. Refresh token is stored in `EncryptedSharedPreferences` (Keystore-wrapped) via `SecureKeyValueStore`. On every authenticated call, if the access token is missing or returns 401, we refresh.
- **Revoke:** `https://oauth2.googleapis.com/revoke?token=<refresh_token>` clears the grant server-side. On disconnect we revoke, then clear the local refresh token.
- **Dependencies:** `AuthComponent.Dependencies` takes `httpExecutor: HttpExecutor` (from `RemoteComponent`), `secureKeyValueStore: SecureKeyValueStore` (from `ApplicationComponent`), `currentActivity: () -> Activity?` (from `ActivityComponent`), and the OAuth client ID via `@BindsInstance`.

### Transport (OkHttp behind `HttpExecutor`)

We don't add the Google Drive Java client lib. The Drive endpoints we need are simple enough that OkHttp + kotlinx-serialization (both already in `zero-remote`) is the right tool.

Endpoints used:

| Operation | HTTP | Path |
|---|---|---|
| Multipart upload (create or replace) | `POST` | `https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size` |
| List files in appDataFolder | `GET` | `https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,modifiedTime,size)` |
| Download file content | `GET` | `https://www.googleapis.com/drive/v3/files/{fileId}?alt=media` |
| Delete file | `DELETE` | `https://www.googleapis.com/drive/v3/files/{fileId}` |

All calls carry `Authorization: Bearer <access_token>`. The `HttpExecutor` interface in `zero-api` is a thin abstraction: `suspend fun execute(request: HttpRequest): HttpResponse`. OkHttp lives behind it in `zero-remote`. `DriveBackupClient` in `zero-backup` knows nothing about OkHttp.

### File slot model

One file per user, named `zero-backup.json`, in `appDataFolder`. Each backup:

1. Looks up the existing file id via list.
2. If present, multipart-upload replaces it (same id, new content).
3. If absent, multipart-upload creates it.

No history, no rotation. v2 enhancement could add a `keep_last_N` mode.

---

## Backup Lifecycle

### State machine

```
Idle ────► Uploading ────► Success
  ▲           │              │
  │           ▼              │
  └────── Failed ◄────────── │ (3 consecutive Failed → emit Notification)
                             ▼
                            Idle (after success)
```

Owned by `DefaultBackupUseCase`. Exposed as `Flow<BackupUseCase.State>` with fields `lastBackupAt`, `lastError`, `consecutiveFailures`, `inProgress`. Both settings UI and WorkManager observe this.

### Cadence

- WorkManager `PeriodicWorkRequest`, 24h interval.
- Constraints: `NetworkType.UNMETERED` (Wi-Fi) by default. Toggleable via a settings switch persisted in `ConfigurationRepository` (config key `backup.wifiOnly: Boolean = true`).
- The worker calls `backupUseCase.perform(BackupNow)` and observes the resulting state until it terminates.
- **No-op skip:** before uploading, compare the `max(updatedDateTime)` across all entities (cheap query) against the timestamp of the last successful backup (persisted in config). If unchanged, mark the run as a "no-op success" and don't touch Drive. This protects against zero-change runs costing API quota.

### Concurrency / coalescing

- `DefaultBackupUseCase` holds a single `MutableStateFlow<State>`. Calling `perform(BackupNow)` while `inProgress == true` is a no-op (returns the same in-flight job). Both the UI button and the WorkManager worker observe the same state.
- Race-free because state mutation goes through a `Mutex.withLock` around the upload section.

### Failure UX

- `state.lastError` carries a structured `BackupError` (auth-expired, network, quota, parse, unknown).
- Settings row renders `Last backed up X ago` on success, `Backup failed — Tap to retry` on `Failed`.
- After 3 consecutive `Failed` states without a `Success` in between, `BackupNotificationPresenter` posts a system notification. The counter resets on any `Success`.
- Tapping the notification deep-links to the settings backup screen.

### Manual "Back up now"

- Ignores the Wi-Fi constraint (the user explicitly chose to do this).
- Coalesces with any in-flight auto-backup (see Concurrency).
- Records the same state transition; the settings UI shows progress in the same row.

---

## Restore Lifecycle

### Two entry points

1. **Settings → Backup → "Restore now"** — already-signed-in user wants to overwrite local state with the cloud version.
2. **Welcome screen → "Restore from Google Drive?"** — fresh install, before presets.

Both routes funnel through the same code path: sign in if needed → download → `BackupEnvelopeSerializer.deserialize` → produce `SyncSnapshot` → hand it to the existing Import flow.

### Drive as Import source

`DriveSnapshotParser : SnapshotParser` is added alongside `ZeroBackupParser` and `ZenMoneySnapshotParser`. It:

1. Ensures sign-in (delegates to `OAuthTokenProvider`).
2. Calls `BackupClient.download()` for the latest envelope.
3. Validates `format == 1`; throws with a forward-compatible error if higher.
4. Returns `envelope.snapshot` to the caller.

`ApplicationComponent.importComponentBuilder` adds the new parser to the parsers list.

### All-new fast path

`DefaultImportUseCase` already runs `syncEngine.delta(snapshot, userId)` after a file is selected. We extend it with one new branch:

- After computing `delta`, check whether **every** entity in `delta` has no match in local DB — i.e. `matchedCategoryByImportId.isEmpty() && matchedAccountByImportId.isEmpty() && existingTransactionSignatures.isEmpty()`. (This is a special case of "local DB is empty," but also covers "DB is non-empty but disjoint from incoming.")
- If all-new: call `syncEngine.import(delta, userId)` directly, skip review screens, transition to `State.UpToDate` (or a new terminal `State.RestoreSuccess`).
- Otherwise: existing flow — Categories Review → Accounts Review → Transactions Preview → Confirm.

This is **one branch**, not a duplicate code path. The fast path is a special case that already exists conceptually as `UpToDate` (zero changes) — we just add `AllNew` next to it.

---

## Settings UX

The existing Settings screen has sections: `PREFERENCES`, `DATA`, `SECURITY`. We add a new `BACKUP` section above `DATA`:

```
BACKUP
  ┌──────────────────────────────────────────────────┐
  │ ⛅  Google Drive backup                          │
  │     Off                          ›               │  ← when disconnected
  └──────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────┐
  │ ⛅  Google Drive backup                          │
  │     Last backed up 3 hours ago    ›              │  ← when connected
  └──────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────┐
  │ ⛅  Google Drive backup                          │
  │     Backup failed — Tap to retry  ›              │  ← when 1+ failure
  └──────────────────────────────────────────────────┘
```

Tapping the row opens a `BackupComponent` detail screen with:

- Sign-in CTA (when disconnected) — primary button "Connect Google Drive". Tap triggers `OAuthTokenProvider.signIn()` flow.
- Account row (when connected) — shows the signed-in Google account email.
- "Back up now" button.
- "Restore from backup" button.
- "Back up over mobile data" toggle (defaults off).
- "Disconnect" destructive action.
- Status block with last-backup info, last-error info if any.

This screen is a single new `BackupComponent` / `BackupViewModel` / `BackupViewProvider` triad. Standard pattern; no special scaffolding.

---

## Phase 0: Android Auto Backup

Independent of the Drive feature. Closes the OS-level device-transfer gap.

Files:
- `app/src/main/res/xml/backup_rules.xml` — legacy `<full-backup-content>` for SDK 23-30.
- `app/src/main/res/xml/data_extraction_rules.xml` — modern `<data-extraction-rules>` for SDK 31+.
- `app/src/main/AndroidManifest.xml` — adds `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"`.

Rules include:
- `<include domain="database" path="zero.db">` — the main Room DB.
- `<exclude domain="database" path="zero.db-shm">` and `<exclude domain="database" path="zero.db-wal">` — SQLite journals are device-specific.
- `<exclude domain="sharedpref" path="com.google.android.gms.appid.xml">` — Play Services internal.
- `<exclude domain="sharedpref" path="zero_secure_prefs">` — Drive OAuth refresh token store (added by Phase 2). Encrypted with a Keystore-backed `MasterKey` that is **device-bound** — Keystore keys cannot be exported or restored on another device, so an `<include>` would push an undecryptable blob. The correct UX on phone swap is to re-sign-in.

**Migration caveat:** if a user backed up at schema vN and restores into vN+M, Room migrations run on first launch. Already supported by `MainDatabase.kt` — no new code needed.

**Why ship this even with Drive backup planned?**
- Free, automatic, requires no user setup.
- Covers users who never set up the explicit Drive backup.
- Closes the gap *now* — independent of the rest of the roadmap.

---

## Security Model

### What we protect against in v1

- Casual exposure via the Drive UI: `appDataFolder` is invisible to the user and to third-party apps.
- Other apps on the same device reading the file: only Zero with the user's grant can read.
- Stolen refresh token via root/backup attack: refresh token is in `EncryptedSharedPreferences` (Android Keystore-wrapped, hardware-backed on modern devices).

### What we do NOT protect against in v1

- A compromised Google account. If an attacker controls the user's Google account, they can grant themselves `drive.appdata` access via the OAuth flow on a different device and read the backup. This is the same threat model as WhatsApp's pre-2021 backups, Google Photos, Google Keep, etc.
- Google itself, or anyone with legal authority over Google.

If those threats matter to a user, v2 will add the encrypted envelope (`format: 2`). The hook is in place from day one.

### Privacy / data handling

- The cloud function setup (per `feedback-infra.md`) is not used here. There is **no Zero server**. The app talks directly to Google's OAuth and Drive endpoints. No first-party server-side logging.
- No backup metadata leaves the device except via the Drive API itself.
- No analytics on backup events.

### Secrets matrix

| Where | OAuth Client ID | OAuth Client Secret |
|---|---|---|
| Local dev | `local.gradle.properties` (`googleOauthClientId`) | none — public client (Android-type OAuth clients have no secret) |
| Release builds | GitHub Actions variable `GOOGLE_OAUTH_CLIENT_ID` | none |

Android-type OAuth clients are public clients identified by package name + SHA-1 signing cert. There is no secret to leak. Per `feedback_ships_in_binary_not_secret`, this is a default not a secret; we hardcode-with-env-override using the same `buildConfigField` pattern as `FEEDBACK_ENDPOINT`.

---

## Testing

### Unit tests (per module)

- `zero-backup`: `DefaultBackupUseCaseTest`, `BackupEnvelopeSerializerTest`, `DriveBackupClientTest` (against a fake `HttpExecutor`). No Android, no real Drive.
- `zero-remote`: `OkHttpHttpExecutorTest` against `MockWebServer` (same pattern as `OkHttpFeedbackServiceTest`). `DriveOAuthTokenProviderTest` against a fake Credential Manager.
- `zero-core`: `DefaultImportUseCaseTest` gains "all-new fast path" cases.

### Integration tests

- Round-trip envelope: `Snapshot → envelope → JSON → envelope → Snapshot` equality.
- Backward-compat fixture: commit a `v1-envelope.json` fixture. Future format changes never modify existing fixtures.

### Manual end-to-end (per phase)

Each phase plan includes its own manual verification step. The cross-phase E2E is: install on Device A → enable backup → back up → install on Device B → restore → diff DB.

---

## Out of Scope (deferred to later)

- End-to-end encryption (`format: 2`).
- Multi-slot history.
- Non-Drive destinations.
- Server-side rate limiting (no Zero server).
- Family sharing of backups.
- Selective backup (omit categories, etc.).
- Notification deep-link payload (Phase 4 ships a generic open-app notification; deep-linking to the Backup screen is a polish item).
- iOS port. Module split is KMP-aware; actual iOS code is its own roadmap.

---

## Phase Index

See `docs/superpowers/plans/2026-05-21-gdrive-backup-roadmap.md` for the phase-by-phase implementation plan.

| # | Plan | Ships |
|---|------|-------|
| 0 | Auto Backup | XML rules + manifest wiring |
| 1 | Foundation | `zero-backup` module, `zero-api` interfaces, envelope serializer, `DefaultBackupUseCase` skeleton, fake `BackupClient` |
| 2 | Drive client | `DriveBackupClient`, OkHttp transport, OAuth provider, secure store |
| 3 | Settings UI | Connect / disconnect / manual backup |
| 4 | Auto schedule | WorkManager, Wi-Fi constraint, failure notifications |
| 5 | Restore | Drive as Import source + all-new fast path |
| 6 | Welcome restore | First-launch restore prompt |
| 7 | Disconnect + remote delete | Confirm dialog, token revoke, optional file delete |
