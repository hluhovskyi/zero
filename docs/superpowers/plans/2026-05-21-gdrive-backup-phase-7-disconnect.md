# Phase 7 — Disconnect with Confirm + Remote File Delete

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Disconnecting from Google Drive shows a confirm dialog that asks "Also delete the existing backup from Google Drive?" with `Delete backup` (default, destructive) and `Keep backup` options. The token is revoked either way. Phase 3's basic disconnect is replaced by this richer flow.

**Architecture:** Two additions: a confirm dialog in `BackupViewProvider` and a new `Action.DisconnectConfirmed(deleteRemote: Boolean)`. The ViewModel orchestrates revoke + optional remote delete via `BackupClient.delete()`.

**Tech Stack:** Existing — Compose AlertDialog, `BackupClient`, `OAuthTokenProvider`.

**Spec:** [Spec §Settings UX](../specs/2026-05-21-gdrive-backup-design.md#settings-ux) — Disconnect.

**Structural analogs:**
- `TransactionDeleteConfirmation` or any existing destructive-confirm dialog in the project — find via `grep -rn "AlertDialog" zero-core/src/main`.

---

### Task 1: Add confirm-dialog state + actions

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/DefaultBackupViewModel.kt`

- [ ] **Step 1: Extend `BackupViewModel.State`** with a `confirmDialog: ConfirmDialog? = null` field.

```kotlin
sealed interface ConfirmDialog {
    object Disconnect : ConfirmDialog
}
```

- [ ] **Step 2: Extend `BackupViewModel.Action`** with:

```kotlin
object Disconnect : Action          // shows the dialog
object DisconnectDismiss : Action   // user backed out
data class DisconnectConfirmed(val deleteRemote: Boolean) : Action
```

(Replace the Phase 3 `Disconnect` semantics — that one currently revokes directly.)

- [ ] **Step 3: Implement transitions in `DefaultBackupViewModel`**:

- `Disconnect` → emit state with `confirmDialog = ConfirmDialog.Disconnect`. Do not call revoke yet.
- `DisconnectDismiss` → emit state with `confirmDialog = null`. No side effect.
- `DisconnectConfirmed(deleteRemote)` → emit state with `confirmDialog = null`, then:
  1. If `deleteRemote` → `backupClient.latest()` to get the file id, then `backupClient.delete(id)`. Ignore `NotFound`.
  2. `oauthTokenProvider.revoke()` (always).
  3. State updates flow naturally from `isSignedIn` becoming `false`.

If `delete` fails, still revoke (we can't leave the credential dangling). Show a snackbar error: "Couldn't delete cloud backup. You can delete it manually in your Google Drive's app settings."

- [ ] **Step 4: Build**

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/backup/
git commit -m "backup(disconnect): add confirm-dialog state + actions"
```

---

### Task 2: Render confirm dialog in `BackupViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupViewProvider.kt`
- Add strings.

- [ ] **Step 1: Render the dialog**

When `state.confirmDialog == ConfirmDialog.Disconnect`, show a Compose `AlertDialog`:

- Title: "Disconnect Google Drive?"
- Body: "Also delete the existing backup from your Google Drive? You can always create a new backup later."
- Confirm button (destructive style): "Delete backup" → dispatches `Action.DisconnectConfirmed(deleteRemote = true)`.
- Neutral button: "Keep backup" → dispatches `Action.DisconnectConfirmed(deleteRemote = false)`.
- Cancel/dismiss: dispatches `Action.DisconnectDismiss`.

Match the existing destructive-confirm style in the project (find via grep, e.g. the existing transaction-deletion dialog). If no precedent exists, use Material `AlertDialog` with `MaterialTheme.colorScheme.error` on the destructive button.

- [ ] **Step 2: Snackbar wiring for delete-failure path**

If the ViewModel emits a one-shot error after `DisconnectConfirmed(true)` fails the delete step (use the same one-shot feedback pattern Phase 3 uses for sign-in errors), show the snackbar.

- [ ] **Step 3: Add strings**

backup_disconnect_title, backup_disconnect_body, backup_disconnect_delete, backup_disconnect_keep, backup_disconnect_delete_failed.

- [ ] **Step 4: Build + lint**

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupViewProvider.kt \
        zero-core/src/main/res/values/strings.xml
git commit -m "backup(disconnect): confirm AlertDialog + delete-failed snackbar"
```

---

### Task 3: Manual UI verification

- [ ] **Step 1: Acquire emulator + ensure signed in with an existing backup file**

- [ ] **Step 2: Path 1 — Delete + revoke**

   1. Open Settings → Backup → tap Disconnect.
   2. Confirm dialog appears.
   3. Tap "Delete backup".
   4. Spinner briefly, then row returns to "Off" (signed-out).
   5. Verify the file is gone from Drive (via API explorer or by attempting another sign-in + restore in a different account / device — should `NotFound`).

- [ ] **Step 3: Path 2 — Keep + revoke**

   1. Sign in again (Phase 3 flow), perform a backup so a file exists.
   2. Tap Disconnect → confirm dialog → tap "Keep backup".
   3. Row returns to "Off". Token is revoked.
   4. Verify the file remains in Drive (it should be visible after re-signing-in and restoring).
   5. Re-sign-in: the orphaned file is picked up by `BackupClient.latest()` → restore works.

- [ ] **Step 4: Path 3 — Cancel**

   1. Sign in. Tap Disconnect. Tap outside the dialog or hit back.
   2. Confirm we stay signed in, dialog dismissed.

- [ ] **Step 5: Path 4 — Delete-failure handling**

   Simulate by toggling airplane mode after the dialog opens but before tapping Delete. Confirm:
   - Token is still revoked (we don't leave a half-state).
   - Snackbar shows "Couldn't delete cloud backup…" message.

- [ ] **Step 6: UI inspector** for the AlertDialog rendering.

---

## Verification

```bash
./gradlew :zero-core:testDebugUnitTest 2>&1 | tail -10
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

All MUST pass. All four manual paths above MUST be verified before opening the PR.

## Out of Scope

- Account-switching (signing in with a different account) — Phase 3 already supports re-sign-in after disconnect; explicit switch is a v2 polish.
- "Forget device" — revoking on one device doesn't revoke on others (each device has its own refresh token). v2.
- Selectively deleting old backup snapshots — single-slot model means there's only one to delete.

---

## Roadmap Closeout

After this phase ships, update `2026-05-21-gdrive-backup-roadmap.md` status table to mark all phases ✅ Merged. The roadmap doc serves as the canonical history of the feature.

Consider opening tracking issues for v2 work mentioned across phase plans:
- `format: 2` encrypted envelope
- Multi-slot history
- Configurable cadence (Daily / Weekly / Manual)
- Notification deep-link with specific failure cause
- Account switching
- Non-Drive backends (Dropbox, iCloud)
