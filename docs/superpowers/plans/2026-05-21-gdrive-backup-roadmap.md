# Google Drive Backup — Implementation Roadmap

**Branch:** `worktree-gdrive-backup-plan` (this planning branch); each phase opens its own implementation branch.
**Spec:** [docs/superpowers/specs/2026-05-21-gdrive-backup-design.md](../specs/2026-05-21-gdrive-backup-design.md)

Index across multiple PRs. Each phase is its own plan file and ships an independently mergeable PR.

## Goal

Let the user enable transparent daily backup of all their finance data to their own Google Drive, and restore on a new device with one tap. Ship in 8 phases so each PR is reviewable, the Android device-transfer gap closes immediately (Phase 0), and explicit-user-controlled Drive backup follows.

## Phase Index & Status Tracker

Update the **Status** column when a phase merges. Acceptable values:
`☐ Pending` · `▶ In progress (PR #N)` · `✅ Merged (PR #N)`.

| # | Plan | Ships | Status |
|---|------|-------|--------|
| 0 | [Auto Backup](2026-05-21-gdrive-backup-phase-0-auto-backup.md) | `backup_rules.xml` + `data_extraction_rules.xml` + manifest wiring; closes the OS device-transfer gap | ☐ Pending |
| 1 | [Foundation](2026-05-21-gdrive-backup-phase-1-foundation.md) | New `zero-backup` module; `zero-api` backup interfaces; `BackupEnvelope`; `DefaultBackupUseCase` skeleton + tests; lint rule | ☐ Pending |
| 2 | [Drive Client + OAuth](2026-05-21-gdrive-backup-phase-2-drive-client.md) | `DriveBackupClient`; `OkHttpHttpExecutor`; `DriveOAuthTokenProvider`; `AndroidSecureKeyValueStore`; Drive REST integration tests | ☐ Pending |
| 3 | [Settings UI](2026-05-21-gdrive-backup-phase-3-settings-ui.md) | `BackupComponent` settings detail screen; new `BACKUP` section in settings; manual "Back up now" loop end-to-end | ☐ Pending |
| 4 | [Auto Schedule](2026-05-21-gdrive-backup-phase-4-auto-schedule.md) | WorkManager periodic worker; Wi-Fi-only default with toggle; 3-strike failure notification; coalescing | ☐ Pending |
| 5 | [Restore](2026-05-21-gdrive-backup-phase-5-restore.md) | `DriveSnapshotParser`; new Drive source in source-selection; "Restore now" in settings; all-new fast path in `DefaultImportUseCase` | ☐ Pending |
| 6 | [Welcome Restore](2026-05-21-gdrive-backup-phase-6-welcome.md) | Welcome screen gains "Restore from Google Drive?" step before presets | ☐ Pending |
| 7 | [Disconnect + Remote Delete](2026-05-21-gdrive-backup-phase-7-disconnect.md) | Confirm dialog on disconnect; token revoke; optional remote file delete; orphan-cleanup helpers | ☐ Pending |

Phases 0 and 1 can run in parallel (Phase 0 is XML, Phase 1 is Kotlin). Phases 2–7 are sequential.

## Cross-Cutting Decisions

### Format-versioned envelope

The file written to Drive is `BackupEnvelope { format: Int, snapshot: SyncSnapshot }`. v1 = plaintext. v2 (future) = encrypted variant. Restore code reads `format` and dispatches. **All v1 code MUST reject unknown `format` values with a forward-compatible error.**

### `zero-backup` is pure Kotlin

No Android imports, no OkHttp imports, no Coroutines-Android. Enforced by a lint rule `BackupModuleEncapsulation` introduced in Phase 1, mirroring `RemoteComponentEncapsulation`. All Android-specific impls live in `zero-remote` (transport, OAuth, secure store) or `app` (scheduler, notifications, manifest wiring).

### Reuse `SyncEngine`, don't fork

Backup = `SyncEngine.export(userId)` → envelope. Restore = envelope → `SyncEngine.import(snapshot, userId)` (fast path) or existing Import review flow (conflict path). No new merge logic. **If a phase needs new merge logic, it's wrong — re-read the spec.**

### `SyncSnapshot` is untouched

Every entity DTO (`SyncTransaction`, `SyncAccount`, etc.) stays as-is. The envelope wraps `SyncSnapshot` from the outside.

### Single rolling file

One Drive file per user: `zero-backup.json` in `appDataFolder`. Each backup multipart-uploads to the same file id (create on first run, replace thereafter). No history slots, no rotation. This is a v1 deliberate simplification — v2 may add `keep_last_N`.

### Single source of truth for state

`DefaultBackupUseCase` owns the entire backup state. WorkManager calls it. The settings ViewModel reads it. No parallel "is backup running" flag anywhere. The use case's `State` is the only state.

Per `feedback_viewmodel_no_derivation`: the ViewModel does not sort, check, or transform `BackupUseCase.State`. If a derivation is needed, extend the use case's State; don't compute in the ViewModel.

### No OAuth secrets or build-time config

**(Revised in Phase 2 — supersedes the earlier "OAuth client ID is a default" note.)** Auth uses the Google Identity **Authorization API**, which identifies the app by its Android OAuth client (package + signing-cert SHA-1). Nothing OAuth-related ships in the binary: no client ID `buildConfigField`, no `driveOauthClientId`, no secret. The earlier plan to embed a Web client ID applied only to the abandoned sign-in / refresh-token flow (which a serverless public client can't do). See the spec's §Auth design-correction.

### Scope: `drive.appdata` only

Never request broader Drive scopes. The lint rule `BackupModuleEncapsulation` doesn't enforce this (it's a string at the OAuth layer), but the Phase 2 code review must verify only `drive.appdata` is requested.

## Module Map (summary)

```
zero-api/.../backup/                  ← BackupClient/UseCase/Envelope/Metadata/Error/Scheduler interfaces
zero-api/.../auth/                    ← OAuthTokenProvider interface
zero-api/.../http/                    ← HttpExecutor interface
zero-api/.../security/                ← SecureKeyValueStore interface
zero-backup/                          ← NEW (pure Kotlin):
  BackupComponent.kt                  ←   provides backupUseCase (backend-agnostic)
  DriveComponent.kt                   ←   provides backupClient + driveSnapshotParser
                                          (Drive-specific impls live here, not in app/)
zero-auth/                            ← NEW (Android): AuthComponent + GoogleOAuthTokenProvider
zero-remote/.../http/                 ← OkHttpHttpExecutor (no Drive code lives here)
zero-core/.../backup/                 ← Android: BackupDetailComponent UI + ViewModel + ViewProvider
zero-core/.../imports/                ← DefaultImportUseCase fast-path branch
zero-core/.../welcome/                ← restore-prompt step (Phase 6)
app/.../scheduling/                   ← WorkManagerScheduler (GENERIC, not about backup)
app/.../security/                     ← AndroidSecureKeyValueStore (GENERIC)
app/.../backup/                       ← Backup-specific Android wiring:
  BackupAndroidModule.kt              ←   Dagger Module included by ApplicationComponent.Module
  DefaultBackupScheduler.kt           ←   implements BackupScheduler using WorkManagerScheduler
  DriveBackupSchedulerWorker.kt       ←   WorkManager worker that calls BackupUseCase
  BackupNotificationPresenter.kt      ←   3-strike failure notification
app/.../ApplicationComponent.kt       ← wires DriveComponent, BackupComponent, AuthComponent
app/src/main/res/xml/                 ← Phase 0: auto-backup rules
app/build.gradle                      ← buildConfigField for OAuth client ID; security-crypto dep
```

## Session Handoff

This roadmap and all 8 phase plans are committed in the planning session. Each phase plan is self-contained — a future session reads only its phase file plus this roadmap and executes. No phase reads another phase's plan.

When starting Phase N execution:
1. Re-enter or create the worktree for the phase.
2. Open this roadmap + `2026-05-21-gdrive-backup-phase-N-*.md`.
3. Invoke `superpowers:subagent-driven-development` and execute.
4. Verify per the phase plan's verification block.
5. PR title: `backup: phase N — <short>`. Body links the phase plan.

## Out of Scope of This Roadmap

Captured in spec §Out of Scope. Notably: E2E encryption (`format: 2`), multi-slot history, non-Drive destinations, iOS port. Each gets its own roadmap when prioritized.
