# Backup

Two independent mechanisms keep Zero's data alive across device swaps/reinstalls:

- **Android Auto Backup** — OS-level, automatic, no user control. Covered here.
- **Google Drive backup** — explicit, user-controlled, `zero-backup` module. Later phases.

## Android Auto Backup

Opts the Room DB (`zero.db`) into the OS backup. On a phone swap the OS restores the
DB before the app launches; `MainDatabase.kt` migrations handle any schema gap.

| File | SDK | Root element |
|---|---|---|
| `app/src/main/res/xml/backup_rules.xml` | ≤30 | `<full-backup-content>` |
| `app/src/main/res/xml/data_extraction_rules.xml` | 31+ | `<data-extraction-rules>` → `<cloud-backup>` + `<device-transfer>` |

Both are wired on `<application>` in `AndroidManifest.xml` (`android:fullBackupContent`,
`android:dataExtractionRules`).

### The one rule that matters: allowlist mode

An `<include>` flips the whole block to **allowlist** — *only* included paths are
backed up; everything else (all SharedPreferences, files, caches) is dropped.
Today only `zero.db` is included, so it's the only thing backed up.

Consequences, and the traps:

- **Don't add `<exclude domain="sharedpref" ...>`** to keep a pref out — it's already
  out, and the exclude points outside any included path, which **fails the
  `FullBackupContent` lint check**. (This is why the GMS appid file has no exclude.)
- **`<exclude>` is only for carving a sub-path out of something you included.** The
  `zero.db-wal`/`zero.db-shm` excludes exist because the `zero.db` include is a
  *prefix* that would otherwise sweep in those device-local journals.

### How to change what's backed up

- **Back up a new file:** add `<include domain="..." path="..."/>` to **both** XML
  files, and to **both** the `<cloud-backup>` and `<device-transfer>` blocks. Keep
  the two files mirrored.
- **Keep something out:** do nothing — allowlist mode already excludes it. Only reach
  for `<exclude>` per the carve-out rule above.

`domain` values: `database`, `sharedpref`, `file`, `external`. `path` is a prefix.
