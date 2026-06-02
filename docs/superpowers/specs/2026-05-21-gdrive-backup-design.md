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
  BackupComponent.kt                  ← DI factory for backup orchestration; exposes backupUseCase.
                                         Backend-agnostic: takes a BackupClient via Dependencies.
  DefaultBackupUseCase.kt             ← state machine; uses SyncEngine + BackupClient
  DriveComponent.kt                   ← DI factory for Drive-specific impls. Sibling of BackupComponent.
                                         Exposes backupClient (= DriveBackupClient) and
                                         driveSnapshotParser. Drive REST is platform-agnostic
                                         (just HTTPS endpoints); the impl is pure Kotlin and
                                         uses HttpExecutor + OAuthTokenProvider through interfaces.
                                         If a second backend (Dropbox, WebDAV) lands, add a sibling
                                         DropboxComponent without touching BackupComponent.
  DriveBackupClient.kt                ← Drive REST contract, internal to DriveComponent
  DriveSnapshotParser.kt              ← restore-side parser, internal to DriveComponent
  BackupEnvelopeSerializer.kt         ← envelope JSON round-trip; constructed internally by
                                         DriveBackupClient (no DI surface — no external consumers)
  AGENTS.md                           ← rules: no Android, no OkHttp imports

zero-auth                             (NEW Android module — Google authorization flow)
  AuthComponent.kt                    ← DI root: exposes googleOAuthTokenProvider
  GoogleOAuthTokenProvider.kt         ← Google Identity Authorization API (play-services-auth):
                                         mints drive.appdata access tokens, no refresh token / no
                                         secret (internal; implements OAuthTokenProvider)
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
  scheduling/WorkManagerScheduler.kt      ← GENERIC WorkManager wrapper. Not about backup.
                                              API: enablePeriodic(name, intervalHours, networkType,
                                              workerClass) / cancel(name). Reusable for any future
                                              periodic Android job. Provided by ApplicationComponent.
  security/AndroidSecureKeyValueStore.kt  ← GENERIC EncryptedSharedPreferences-backed
                                              SecureKeyValueStore. Already not about backup.
                                              Provided by ApplicationComponent.
  backup/
    BackupAndroidModule.kt                ← Dagger Module bundling backup-specific Android wiring.
                                              Included by ApplicationComponent.Module via
                                              `@Module(includes = [..., BackupAndroidModule::class])`,
                                              same pattern as the existing RemoteModule.
    DefaultBackupScheduler.kt             ← Implements BackupScheduler by adapting
                                              WorkManagerScheduler with backup-specific config
                                              (job name = "drive-backup-periodic", interval = 24h,
                                              worker = DriveBackupSchedulerWorker::class.java).
    DriveBackupSchedulerWorker.kt         ← WorkManager CoroutineWorker; instantiated by WM,
                                              pulls BackupUseCase from MainApplication.
    BackupNotificationPresenter.kt        ← Observes BackupUseCase.state; system notification
                                              on 3 consecutive failures.
  ApplicationComponent.kt             ← wires all of the above; constructs DriveComponent +
                                          BackupComponent from interfaces provided by RemoteComponent,
                                          AuthComponent, SyncComponent, DatabaseComponent.

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

Owned by `GoogleOAuthTokenProvider` in the new `zero-auth` module. The provider is generic over Google scopes — the scope (`drive.appdata` for the backup use case) is provided at construction. Future integrations needing a Google API scope reuse this provider with different scopes.

> **Design correction (Phase 2 smoke test).** The original design here was "Credential Manager → exchange a grant at the OAuth token endpoint → store a **refresh token**." That is **not achievable for a public client with no backend**: minting a refresh token requires either a client secret (a confidential/Web client → a server) or a PKCE browser flow, and the spec mandates no server and no secret. `GetGoogleIdOption` also only authenticates (it returns an ID token, not a Drive grant). The corrected design below uses the **Google Identity Authorization API** to mint short-lived **access tokens** on demand — no refresh token, no Web client, no secret, no server. This is how Android Drive-backup apps (e.g. WhatsApp) work.

- **Authorization (not authentication):** `Identity.getAuthorizationClient(context).authorize(AuthorizationRequest{ requestedScopes = [drive.appdata] })`. The app is identified purely by its **Android OAuth client** (package name + signing-cert SHA-1) — there is no ID token, no Web client ID, and no client secret. The first call returns a consent `PendingIntent`, launched on the foreground activity (`ActivityResultRegistry`); the user approves Drive access once.
- **Access tokens:** the authorization result carries a short-lived (~1 h) access token, cached in memory. When it expires, `authorize()` is called again — **silently, with no UI** (including from background work), because Google Play services owns the long-lived grant for the device account.
- **No refresh token, no token endpoint, no `SecureKeyValueStore` for credentials.** A small connected-flag is persisted so `isSignedIn` survives process death; there is no secret on device.
- **Revoke (disconnect):** clear local state (`isSignedIn → false`, drop the cached token). There is no on-device refresh token to wipe; the user can fully revoke the grant in their Google account settings (a remote-revoke call is a Phase 7 polish item).
- **Dependencies:** `AuthComponent.Dependencies` takes `context: Context` (for the Authorization client + silent background refresh), `secureKeyValueStore: SecureKeyValueStore` (for the connected flag), and `currentActivityProvider: () -> Activity?` (to launch the one-time consent). No `HttpExecutor`, no OAuth-client-ID, no `@BindsInstance`.

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

Tapping the row opens a `BackupDetailComponent` screen with (note: the screen is reachable from anywhere — settings row, notification deep-link, restore-failure banner — so the name doesn't bake in "settings"):

- Sign-in CTA (when disconnected) — primary button "Connect Google Drive". Tap triggers `OAuthTokenProvider.signIn()` flow.
- Account row (when connected) — shows the signed-in Google account email.
- "Back up now" button.
- "Restore from backup" button.
- "Back up over mobile data" toggle (defaults off).
- "Disconnect" destructive action.
- Status block with last-backup info, last-error info if any.

This screen is a single new `BackupDetailComponent` / `BackupDetailViewModel` / `BackupDetailViewProvider` triad. Standard pattern; no special scaffolding. (Renamed from `BackupComponent*` to avoid FQN collision with the `BackupComponent` factory in `zero-backup`. The screen is reachable from settings, notification deep-links, and inline error banners — not coupled to a single launching context.)

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
- Stolen credentials via root/backup attack: there is **no refresh token on device**. Access tokens live in memory only (~1 h) and are re-minted by Play services; the long-lived grant is held by Google, not by Zero.

### What we do NOT protect against in v1

- A compromised Google account. If an attacker controls the user's Google account, they can grant themselves `drive.appdata` access via the OAuth flow on a different device and read the backup. This is the same threat model as WhatsApp's pre-2021 backups, Google Photos, Google Keep, etc.
- Google itself, or anyone with legal authority over Google.

If those threats matter to a user, v2 will add the encrypted envelope (`format: 2`). The hook is in place from day one.

### Privacy / data handling

- The cloud function setup (per `feedback-infra.md`) is not used here. There is **no Zero server**. The app talks directly to Google's OAuth and Drive endpoints. No first-party server-side logging.
- No backup metadata leaves the device except via the Drive API itself.
- No analytics on backup events.

### Secrets matrix

There are **no secrets and no build-time OAuth config**. The Authorization API identifies the app by its **Android OAuth client** (package name + signing-cert SHA-1), which is registered in Google Cloud but never embedded in the app — the OS presents the signature. Nothing OAuth-related ships in the binary, so there is no `buildConfigField`, no `driveOauthClientId`, and no client secret anywhere.

| Artifact | Where | Secret? |
|---|---|---|
| Android OAuth client (package + SHA-1) | Google Cloud Console only | no — identity, not a secret; nothing in the binary |
| OAuth client secret | n/a | none — public client, no backend |

(Setup note: a Google Cloud project with the Drive API enabled, an OAuth consent screen requesting `drive.appdata`, and an Android OAuth client for the app's signing cert. A Web client ID is **not** required — it was only needed by the abandoned sign-in / refresh-token design.)

---

## Testing

Tiered, from cheapest/most-coverage to highest-fidelity/least-automatable.

### Tier 1 — Unit tests (per module, run in CI)

- `zero-backup`: `DefaultBackupUseCaseTest` (state machine, coalescing, 3-strike counter), `BackupEnvelopeSerializerTest` (round-trip + v1 fixture + unknown-format rejection), `DriveBackupClientTest` against a fake `HttpExecutor` (URLs, headers, status-code → BackupError mapping). No Android, no real Drive.
- `zero-auth`: **no unit tests.** `GoogleOAuthTokenProvider` talks to the Google Identity Authorization API (Play services) for both sign-in and access-token minting, neither of which is unit-testable. It is validated on-device in the Tier 4 manual smoke test.
- `zero-remote`: `OkHttpHttpExecutorTest` against `MockWebServer` (same pattern as `OkHttpFeedbackServiceTest`).
- `zero-core`: `DefaultImportUseCaseTest` gains "all-new fast path" cases.

### Tier 2 — MockWebServer wire-format test (in CI)

`DriveBackupClientWireFormatTest` uses the real `OkHttpHttpExecutor` against `MockWebServer` configured to look like Drive. Catches OkHttp-level bugs and Drive-shape mistakes (wrong query params, response-field mismatches) that the Tier 1 fakes mask. Six cases covering upload multipart, list query, download `alt=media`, 401 → AuthExpired, 403 quota → QuotaExceeded. Phase 2 Task 8.

### Tier 3 — Instrumented backup→restore round-trip (in CI on emulator)

`BackupRestoreRoundTripTest` on an emulator. Wires the full pipeline (DB → SyncEngine.export → envelope → fake upload → fake download → SyncEngine.import → DB) end-to-end. `BackupClient` is substituted with an in-memory `InMemoryBackupClient`. Catches integration bugs across SyncEngine + envelope + use case + restore parser + UI state transitions. Phase 5 Task 5.

### Tier 4 — Real-Drive manual smoke (per phase, gated on PR)

A pre-set dev Google account, manual sign-in through Credential Manager, real upload/download/restore against `appDataFolder`. Can't be automated: Credential Manager UI is system UI, and tying CI to a live Google account is operationally brittle. Phase 2 Task 9 establishes the protocol; subsequent phases re-run it.

### Tier 5 — Internal Testing track canary

v1 ships to Play Internal Testing only (matching `feedback-infra` precedent). Catches real-world issues no test rig simulates: cellular conditions, real Drive quota behaviour, OAuth token expiry under real load, consent-prompt UX. Promote to Production only after a multi-week canary.

### Backward-compatibility fixtures

Committed JSON fixtures in `zero-backup/src/test/resources/fixtures/backup/`:
- `v1-envelope.json` — added in Phase 1. Round-tripped by every CI run.

When the envelope format changes in a breaking way, add a `v2-*.json` fixture set — **never modify existing ones.** This guarantees historical backups remain readable.

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
