# Backup

Android Auto Backup rules live in `app/src/main/res/xml/backup_rules.xml` (SDK ≤30) and `data_extraction_rules.xml` (SDK 31+), wired on `<application>` in the manifest. Explicit Google Drive backup (the `zero-backup` module) arrives in later phases.

## Non-obvious conventions

- **An `<include>` turns the block into an allowlist** — once any path is included, *everything else* (all prefs, files, caches) is silently dropped from the backup. Only `zero.db` is included today, so it's the only thing backed up.
- **Never `<exclude>` a path that's already outside the allowlist** — e.g. a `sharedpref` file when only a database is included. It's redundant *and* fails the `FullBackupContent` lint check, because the path sits under no `<include>`. This is why the device-bound `com.google.android.gms.appid.xml` has no exclude. `<exclude>` is valid *only* to carve a sub-path out of an include — the `zero.db-wal`/`-shm` excludes do exactly that, since `zero.db` is a path *prefix* that would otherwise sweep them in.
