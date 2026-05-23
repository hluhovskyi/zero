# Backup

Two independent mechanisms survive a device swap/reinstall: **Android Auto Backup** (OS-level, automatic — covered here) and **Google Drive backup** (explicit, user-controlled, `zero-backup` module — later phases).

## Non-obvious conventions

- **Two mirrored XML files, kept in sync** — `app/src/main/res/xml/backup_rules.xml` (`<full-backup-content>`, SDK ≤30) and `data_extraction_rules.xml` (`<data-extraction-rules>` → `<cloud-backup>` + `<device-transfer>`, SDK 31+). Any rule change goes into both files, and into both blocks of the 31+ file. Wired on `<application>` via `android:fullBackupContent` + `android:dataExtractionRules`.
- **Allowlist mode — an `<include>` flips the whole block** — once any path is included, *only* included paths are backed up; all other SharedPreferences, files, and caches are dropped. Today only `zero.db` is included, so it's the only thing backed up.
- **Never `<exclude>` a SharedPreferences file to keep it out** — allowlist mode already excludes everything not included, and an exclude pointing outside any included path **fails the `FullBackupContent` lint check**. This is why the device-specific `com.google.android.gms.appid.xml` (GMS instance id) has no exclude — it's out implicitly.
- **`<exclude>` is only for carving a sub-path out of something you included** — the `zero.db-wal`/`zero.db-shm` excludes exist because the `zero.db` include is a *prefix* that would otherwise sweep in those device-local journals. `path` is always a prefix; `domain` is one of `database`, `sharedpref`, `file`, `external`.
- **To back up a new file, add an `<include>`, not anything else** — and add it to both XML files (and both blocks). To keep something out, do nothing.
- **Restore happens before the app launches** — the OS restores `zero.db` pre-launch; `MainDatabase.kt` migrations cover any schema gap between source and target devices.
