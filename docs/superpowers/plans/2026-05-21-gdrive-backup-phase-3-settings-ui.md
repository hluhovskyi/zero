# Phase 3 — Settings UI: Connect, Manual Backup, Status

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the user-visible side of the connect-and-manual-backup loop. New `BACKUP` section in settings root, a dedicated `BackupDetailComponent` detail screen, sign-in CTA, "Back up now" button, status row, disconnect (basic — confirm dialog comes in Phase 7). No WorkManager yet.

**Architecture:** Standard project pattern — `BackupDetailComponent` / `BackupDetailViewModel` / `BackupDetailViewProvider` triad scaffolded via `zero-project:scaffold-feature`. `BackupDetailViewModel` is a thin projection of `BackupUseCase.state`; per `feedback_viewmodel_no_derivation`, no sort/check/mapping in ViewModel — extend the use case's State if needed. Settings root gets a new section row that navigates to the detail screen.

**Tech Stack:** Compose, Dagger, existing project navigation.

**Spec:** [Spec §Settings UX](../specs/2026-05-21-gdrive-backup-design.md#settings-ux)

**Design reference:** Design archive `Nkx9GtG-6hWj_RKmVm3biQ` (Claude Design). Before starting Phase 3, run `zero-project:fetch-design` for that hash. Read:
- `zero-design-system/project/ui_kits/zero/index.html` — defines `BackupDetailScreen`, `BackupHeroIllustration`, `BackupStatusBlock` and shows all 5 visual states (Disconnected / Idle / Uploading / Failed / Restoring). The Settings root `BACKUP` section + `Cloud Backup` row with state-driven subtitle and status color is also in this file.
- `zero-design-system/project/colors_and_type.css` — design tokens; map to `zero-ui/.../theme/` tokens, **never hardcode hex**.

**One copy correction at implementation time:** the Disconnected-state body copy in the design reads *"Save an encrypted copy of your data to your Google Drive."* v1 ships plaintext (encryption is the `format: 2` v2 hedge per the spec). Implement the copy as *"Save a copy of your data to your Google Drive. Restore it on any device, anytime."* — drop "encrypted." Bring the word back when `format: 2` ships.

**Structural analogs:**
- `SettingsComponent.kt` for the detail-screen Component shape (Dagger component with `@<Feature>Scope`, Builder + `@BindsInstance`, internal ViewModel).
- `SettingsViewProvider.kt` for the row + section composables (reuse `MoreSection` + `MoreRow`).
- `BiometricLockGateViewModel.kt` for a small state-projection ViewModel.

**Scope annotation:** use `@BackupDetailScope` for the UI Dagger component — distinct from the `BackupComponent` factory in `zero-backup` (which is manual DI, no Dagger scope). The UI scope owns Compose-collector coroutines; the factory scope owns the use case.

---

### Task 1: Scaffold `BackupDetail` triad

**Naming note:** the UI component is `BackupDetailComponent`, not `BackupComponent`. The factory in `zero-backup` already owns the `BackupComponent` name (see Phase 1 Task 5) and both would live in the same `com.hluhovskyi.zero.backup` package — Dagger would generate two `DaggerBackupComponent`s and collide. `BackupDetailComponent` matches the screen's purpose (the settings-detail screen for backup configuration) and avoids the collision.

**Files:** Auto-created by skill, then renamed.

- [ ] **Step 1: Run scaffold-feature**

```
Run `zero-project:scaffold-feature` for `BackupDetail` in `zero-core/.../backup/`.
```

Generates: `BackupDetailComponent`, `BackupDetailViewModel`, `DefaultBackupDetailViewModel`, `BackupDetailViewProvider` in `zero-core/src/main/java/com/hluhovskyi/zero/backup/`.

If the scaffold-feature skill doesn't accept a multi-word feature name cleanly, run with `Backup` and then rename `Backup*` → `BackupDetail*` across the four generated files before committing.

- [ ] **Step 2: Confirm stubs build**

Run: `./gradlew :zero-core:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/backup/
git commit -m "backup(ui): scaffold BackupDetail triad"
```

---

### Task 2: Define `BackupDetailViewModel.State` + `Action` + projection

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupDetailViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/DefaultBackupDetailViewModel.kt`

Per `feedback_viewmodel_no_derivation`, this ViewModel **does no derivation**. It maps `BackupUseCase.State` 1:1 to a display state plus the in-memory `isSignedIn` flow from `OAuthTokenProvider`.

- [ ] **Step 1: Define the State**

```kotlin
sealed interface BackupDetailViewModel {
    val state: kotlinx.coroutines.flow.Flow<State>
    fun perform(action: Action)
    fun attach(): java.io.Closeable

    data class State(
        val isSignedIn: Boolean,
        val accountLabel: String?,
        val phase: BackupUseCase.Phase,
        val lastSuccessAt: kotlinx.datetime.LocalDateTime?,
        val lastError: BackupError?,
    )

    sealed interface Action {
        object Connect : Action
        object BackupNow : Action
        object Restore : Action      // Phase 5 wires this; in Phase 3 it's a no-op + Timber.w
        object Disconnect : Action
    }
}
```

- [ ] **Step 2: Implement `DefaultBackupDetailViewModel`** with a `combine` of `oauthTokenProvider.isSignedIn` + `backupUseCase.state` + a held `accountLabel` (re-read from `SecureKeyValueStore` once at attach). `perform(Connect)` calls `oauthTokenProvider.signIn()`. `perform(BackupNow)` calls `backupUseCase.perform(BackupNow)`. `perform(Restore)` logs `Timber.w("Restore not wired until Phase 5")`. `perform(Disconnect)` calls `oauthTokenProvider.revoke()` directly (confirm dialog lands in Phase 7).

- [ ] **Step 3: Compile**

Run: `./gradlew :zero-core:assembleDebug 2>&1 | tail -10`

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/backup/
git commit -m "backup(ui): BackupDetailViewModel state + projection over BackupUseCase"
```

---

### Task 3: `BackupDetailViewProvider` Compose UI

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupDetailViewProvider.kt`
- Add: strings to `zero-core/src/main/res/values/strings.xml`

Use the same `MoreSection` + `MoreRow` building blocks as `SettingsViewProvider`. **Read `SettingsViewProvider.kt` first** to copy the section composable rather than redefining it.

Layout:

```
[Scaffold + SnackbarHost]
  ┌── Header ────────────────────────────┐
  │   "Google Drive backup"              │
  └──────────────────────────────────────┘
  ┌── Section ACCOUNT (when signed-in) ──┐
  │   Account: <accountLabel>            │
  └──────────────────────────────────────┘
  ┌── Section ACTIONS ──────────────────┐
  │   [Connect Google Drive]  (CTA)      │  ← when !isSignedIn
  │       OR                             │
  │   [Back up now]  [Restore]           │  ← when isSignedIn
  └──────────────────────────────────────┘
  ┌── Section STATUS (signed-in only) ───┐
  │   "Last backed up X ago" / "Failed"  │
  │   "Backing up…"  (when Uploading)    │
  └──────────────────────────────────────┘
  ┌── Section DANGER (signed-in only) ───┐
  │   [Disconnect]                       │
  └──────────────────────────────────────┘
```

- [ ] **Step 1: Add strings** — backup_section_account, backup_section_actions, backup_section_status, backup_section_danger, backup_connect, backup_now, backup_restore, backup_disconnect, backup_last_at, backup_failed, backup_in_progress, backup_signed_in_as.

- [ ] **Step 2: Implement composable** in `BackupDetailViewProvider.View()`. Use `DateFormatter` for the "X ago" rendering (the same one `MainScreen` uses; pull it from existing usage as a model).

- [ ] **Step 3: Wire snackbar messages** for `Result.Cancelled` from sign-in, generic failure, etc. Reuse the existing snackbar host pattern from `SettingsViewProvider`.

- [ ] **Step 4: Compile**

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupDetailViewProvider.kt \
        zero-core/src/main/res/values/strings.xml
git commit -m "backup(ui): BackupDetailViewProvider Compose layout"
```

---

### Task 4: Add `BACKUP` section to Settings root + navigate to detail

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/DefaultSettingsViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsComponent.kt`
- Add a destination + entry — wire per [Navigation](../../agents/navigation.md).
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt` (register destination)

The settings-root row reads `BackupUseCase.state` directly to render its secondary text — same pattern as Biometric row does today. Tapping navigates to the new `Backup` destination.

- [ ] **Step 1: Add a new `OnBackupSelectedHandler`** (`fun interface OnBackupSelectedHandler { fun onSelected() }`) in `zero-core/.../settings/`.

  Per `feedback_default_handlers_not_per_screen`: default this handler at the Builder `@Provides` layer with Navigator access in `app`, not as `Noop`. Read `OnImportSelectedHandler` and its default provider to see the exact pattern.

- [ ] **Step 2: Extend `SettingsComponent.Builder`** to accept the handler via `@BindsInstance`. Mirror existing handler bindings.

- [ ] **Step 3: Extend `SettingsComponent.Dependencies`** to include `backupUseCase: BackupUseCase`.

- [ ] **Step 4: Extend `SettingsViewModel.State`** with `backup: BackupSummary` field carrying isSignedIn + lastSuccessAt + phase + lastError. **Do not derive in ViewModel** — extend `SettingsCurrencyUseCase`-style query if state composition gets non-trivial; for now, a simple `combine` of `backupUseCase.state` into the existing `SettingsViewModel` state flow is acceptable.

- [ ] **Step 5: Render the section** in `SettingsViewProvider`. Add a `MoreSection("BACKUP")` above the existing `DATA` section. Secondary text:
  - `!isSignedIn` → "Off"
  - `phase == Uploading` → "Backing up…"
  - `phase == Failed && consecutiveFailures > 0` → "Backup failed — Tap to retry"
  - else with `lastSuccessAt` → "Last backed up <X ago>"
  - else → "On"

- [ ] **Step 6: Register the destination** in `ActivityComponent` per [navigation.md](../../agents/navigation.md). Use `accountNavigationEntry` as the structural reference (it's a similar single-screen destination without args). Build it via `NavigatorScope.buildable(BackupDestination) { ... BackupDetailComponent.builder(...) ... }`.

- [ ] **Step 7: Compile + lint**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Run: `./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10`

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/ \
        zero-core/src/main/java/com/hluhovskyi/zero/backup/ \
        app/src/main/java/com/hluhovskyi/zero/activity/ \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "backup(ui): BACKUP section in settings + Backup destination + navigation"
```

---

### Task 5: Manual UI verification

Per AGENTS.md rule 5 ("UI Validation"), compilation is not validation.

- [ ] **Step 1: Acquire emulator**

Per `feedback_emulator_concurrent_sessions`: acquire explicitly at UI verification.

Run: `./scripts/emulator/acquire`

- [ ] **Step 2: Install + launch**

```bash
./scripts/install-app.sh
./scripts/ui/adb.sh shell am start -n com.hluhovskyi.zero/.activity.MainActivity
```

- [ ] **Step 3: Inspect**

Invoke `zero-project:android-ui-inspector` skill.

Verify:
1. Settings screen renders a `BACKUP` section above `DATA`.
2. The row shows "Off" when not signed in.
3. Tapping the row opens the new Backup detail screen.
4. Detail screen shows "Connect Google Drive" CTA.
5. Tap CTA → Credential Manager bottom sheet appears.
6. After sign-in, detail screen flips to show account label + "Back up now" + "Restore" + "Disconnect" buttons.
7. Tapping "Back up now" → row says "Backing up…" briefly → returns to "Last backed up just now".
8. Tapping "Disconnect" → returns to "Off" (no confirm dialog yet; that's Phase 7).
9. Settings root row reflects the latest state.

- [ ] **Step 4: Commit any string/layout fixes**

If the inspector found bugs (alignment, copy, etc.), fix and commit per the inspector skill's loop.

---

## Verification

```bash
./gradlew :zero-core:testDebugUnitTest 2>&1 | tail -10
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Plus the manual UI inspection above. PR doesn't go ready until the inspector confirms.

## Out of Scope

- Automatic scheduling — Phase 4.
- Restore — Phase 5 (the button exists, but its handler is a Timber.w until then).
- Disconnect confirm dialog + remote file delete — Phase 7.
- "Back up over mobile data" toggle — Phase 4 (added when WorkManager constraints land).
- Welcome restore prompt — Phase 6.
