# Phase 0 — Android Auto Backup

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the OS-level device-transfer gap independently of the Drive feature, by opting Zero's Room DB into Android Auto Backup.

**Architecture:** Pure XML + manifest wiring. No Kotlin changes, no new modules. Android handles upload to a hidden Google-managed quota and restores on the next-device setup wizard.

**Tech Stack:** Android Auto Backup (`fullBackupContent` + `dataExtractionRules` XML).

**Spec:** [Spec §Phase 0: Android Auto Backup](../specs/2026-05-21-gdrive-backup-design.md#phase-0-android-auto-backup)

---

### Task 1: Create legacy backup rules XML (SDK ≤ 30)

**Files:**
- Create: `app/src/main/res/xml/backup_rules.xml`

- [ ] **Step 1: Create the file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Rules for Android Auto Backup on SDK 23-30. See data_extraction_rules.xml for SDK 31+. -->
<full-backup-content>
    <include domain="database" path="zero.db" />
    <exclude domain="database" path="zero.db-shm" />
    <exclude domain="database" path="zero.db-wal" />
    <exclude domain="sharedpref" path="com.google.android.gms.appid.xml" />
</full-backup-content>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/xml/backup_rules.xml
git commit -m "backup(auto): add legacy backup_rules.xml for SDK <=30"
```

---

### Task 2: Create modern data extraction rules XML (SDK 31+)

**Files:**
- Create: `app/src/main/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: Create the file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Rules for Android Auto Backup (cloud) + D2D transfer on SDK 31+.
     Mirror backup_rules.xml; the two paths share the Room DB include. -->
<data-extraction-rules>
    <cloud-backup>
        <include domain="database" path="zero.db" />
        <exclude domain="database" path="zero.db-shm" />
        <exclude domain="database" path="zero.db-wal" />
        <exclude domain="sharedpref" path="com.google.android.gms.appid.xml" />
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="zero.db" />
        <exclude domain="database" path="zero.db-shm" />
        <exclude domain="database" path="zero.db-wal" />
        <exclude domain="sharedpref" path="com.google.android.gms.appid.xml" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/xml/data_extraction_rules.xml
git commit -m "backup(auto): add data_extraction_rules.xml for SDK 31+"
```

---

### Task 3: Wire rules into manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (the `<application>` element)

- [ ] **Step 1: Add attributes to `<application>`**

Locate the `<application ...>` element. Today it reads:

```xml
<application
    android:name=".MainApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Zero">
```

Add two attributes after `android:allowBackup`:

```xml
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

Final block:

```xml
<application
    android:name=".MainApplication"
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Zero">
```

- [ ] **Step 2: Build to verify lint passes**

Run: `./gradlew :app:lintDebug 2>&1 | tail -20`
Expected: no errors related to backup rules.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "backup(auto): wire data_extraction_rules + fullBackupContent in manifest"
```

---

### Task 4: Add note to data-layer docs

**Files:**
- Modify: `docs/agents/data-layer.md` (append section)

- [ ] **Step 1: Add a short subsection at the end**

Append:

```markdown
## Android Auto Backup

The Room DB (`zero.db`) is opted into Android Auto Backup via
`app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`.
SQLite WAL/SHM journals are excluded — they are device-specific. On a phone
swap, the OS restores the DB before the app launches. Room migrations in
`MainDatabase.kt` cover any schema mismatch between source and target devices.

If a new SharedPreferences file is introduced that must NOT be backed up
(e.g. anything caching device-local tokens that should re-acquire after
transfer), add an `<exclude domain="sharedpref" path="..." />` line to
both XML files in the same commit as the SharedPreferences introduction.
```

- [ ] **Step 2: Commit**

```bash
git add docs/agents/data-layer.md
git commit -m "docs(data-layer): document Android Auto Backup rules"
```

---

## Verification

```bash
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -20
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: no errors. App builds.

**Manual E2E** (optional, requires two devices or the Backup & Restore test framework):

```bash
# On dev device A:
./scripts/ui/adb.sh shell bmgr backupnow com.hluhovskyi.zero
./scripts/ui/adb.sh shell bmgr list sets   # confirm Zero appears with non-zero size

# Reinstall the app and trigger a restore:
./scripts/ui/adb.sh shell pm clear com.hluhovskyi.zero
# Reinstall, then:
./scripts/ui/adb.sh shell bmgr restore <set-id> com.hluhovskyi.zero
# Open the app and verify transactions are present.
```

No UI verification needed — this phase doesn't touch any UI.

## Out of Scope

- Notifying users that Auto Backup ran. Auto Backup is implicit; that's the point.
- Excluding specific tables. Whole DB is in or out.
- Backing up Drive credentials. Phase 2 introduces `zero_secure_prefs` and adds an explicit `<exclude>` rule for it — Keystore master keys are device-bound, so the encrypted blob cannot survive transfer. Re-sign-in on the new device is the correct flow.
