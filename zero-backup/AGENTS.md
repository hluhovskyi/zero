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
