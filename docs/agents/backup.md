# Backup

How Zero's data survives a device swap or reinstall. Two independent mechanisms:
OS-level Android Auto Backup (here, Phase 0) and the explicit user-controlled
Google Drive backup feature (later phases — `zero-backup` module).

## Android Auto Backup

The Room DB (`zero.db`) is opted into Android Auto Backup via
`app/src/main/res/xml/backup_rules.xml` (legacy `<full-backup-content>`, SDK ≤30)
and `app/src/main/res/xml/data_extraction_rules.xml` (`<data-extraction-rules>`,
SDK 31+, mirrored across `<cloud-backup>` and `<device-transfer>`). Both are wired
on the `<application>` element in `AndroidManifest.xml` via
`android:fullBackupContent` and `android:dataExtractionRules`.

These rules run in **allowlist mode**: because each block carries an `<include>`,
Android backs up *only* the included paths. So `zero.db` is the sole thing backed
up — no SharedPreferences, files, or caches. The device-specific
`com.google.android.gms.appid.xml` (GMS instance id) is therefore excluded
implicitly; it does not need an explicit `<exclude>` (and adding one fails the
`FullBackupContent` lint check, since the path isn't under any included path).

SQLite WAL/SHM journals (`zero.db-wal`, `zero.db-shm`) *are* excluded explicitly —
the `zero.db` include is a path prefix that would otherwise sweep them in, and they
are device-specific. On a phone swap, the OS restores the DB before the app
launches. Room migrations in `MainDatabase.kt` cover any schema mismatch between
source and target devices.

### Adding SharedPreferences to the backup

To back up a SharedPreferences file, add an `<include domain="sharedpref" path="..." />`
to **both** XML files (and to every `<cloud-backup>`/`<device-transfer>` block).
For a file that must NOT survive transfer (e.g. anything caching device-local
tokens that should re-acquire after a swap), simply leave it out — allowlist mode
already excludes everything not included. Only add an `<exclude>` when carving a
sub-path back out of something you *did* include (as the WAL/SHM excludes do for
`zero.db`).
