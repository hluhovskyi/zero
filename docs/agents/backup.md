# Backup

Android Auto Backup rules live in `app/src/main/res/xml/backup_rules.xml` (SDK ≤30) and `data_extraction_rules.xml` (SDK 31+), wired on `<application>` in the manifest. Explicit Google Drive backup (the `zero-backup` module) arrives in later phases.

## Non-obvious conventions

- **An `<include>` turns the block into an allowlist** — once any path is included, *everything else* (all prefs, files, caches) is silently dropped from the backup. Only `zero.db` is included today, so it's the only thing backed up.
- **Never `<exclude>` a path that's already outside the allowlist** — e.g. a `sharedpref` file when only a database is included. It's redundant *and* fails the `FullBackupContent` lint check, because the path sits under no `<include>`. This is why the device-bound `com.google.android.gms.appid.xml` has no exclude. `<exclude>` is valid *only* to carve a sub-path out of an include — the `zero.db-wal`/`-shm` excludes do exactly that, since `zero.db` is a path *prefix* that would otherwise sweep them in.
- **`zero_secure_prefs` is already excluded by the allowlist — keep it that way.** Phase 2 added the Drive OAuth refresh-token store (`AndroidSecureKeyValueStore`, file `zero_secure_prefs`), encrypted with a Keystore-backed `MasterKey`. Keystore keys are **device-bound** — they can't be exported or restored on another device, so backing up the file would push an undecryptable blob (and silently transfer a long-lived refresh token without consent). Because only `zero.db` is included, this file is dropped implicitly; **never add it to an `<include>`.** The correct UX on phone swap is to re-sign-in.
