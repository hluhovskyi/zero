# Phase 5 — Restore: Drive as Import Source + All-New Fast Path

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the "restore" half of backup. Drive becomes a third import source alongside Zero file + ZenMoney. The existing Import flow handles the restore — including all conflict UI. A new fast-path branch in `DefaultImportUseCase` skips the review screens when the incoming snapshot has no overlap with local DB (typical fresh-install case).

**Architecture:** New `DriveSnapshotParser : SnapshotParser` in `zero-core`. Source-selection UI gains a third source. `DefaultImportUseCase` after computing `delta` checks for the no-overlap case and skips review. The "Restore" button in the Backup detail screen launches the existing Import flow with the Drive source pre-selected.

**Tech Stack:** Existing — `SnapshotParser`, `DefaultImportUseCase`, navigation.

**Spec:** [Spec §Restore Lifecycle](../specs/2026-05-21-gdrive-backup-design.md#restore-lifecycle)

**Structural analogs:**
- `ZeroBackupParser.kt` — closest analog for `DriveSnapshotParser`.
- `ImportComponent.kt` and `ImportUseCase.kt` — the flow this hooks into.

---

### Task 1: `DriveSnapshotParser`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DriveSnapshotParser.kt`

Read `ZeroBackupParser.kt` first to understand the parser contract.

`DriveSnapshotParser`:
- Constructor takes `BackupClient`, `OAuthTokenProvider`, `BackupEnvelopeSerializer`.
- `source.key = "drive"`, label `"Google Drive"`, icon `Icons.Outlined.Cloud` (or similar).
- `parse(uri)`: the `uri` is ignored (Drive doesn't have a local URI). Instead:
  1. If `!oauthTokenProvider.isSignedIn.first()` → throw `IllegalStateException("Not signed in")` with a clear message.
  2. Call `backupClient.latest()`. If `NotFound` → throw with message "No backup found in your Google Drive".
  3. Call `backupClient.download(metadata.backupId)`. If `Failure` → throw with the error message.
  4. Validate `envelope.format == 1` (deserializer already enforces this).
  5. Return `envelope.snapshot`.

The `Source` shape used by `ImportComponent` does not currently encode "this source does not need a file URI." Two options:

**Option A** — `Source` gains a `requiresFile: Boolean = true` field. Source-selection skips the file picker for sources where this is false. Cleaner, but cross-cutting change.

**Option B** — Drive source uses a sentinel `Uri.NonEmpty` value (e.g. `Uri("drive://latest")`). Parser ignores the URI content. Less clean.

Pick **Option A**. It's one small field on an existing data class, no semantics regression for existing parsers.

- [ ] **Step 1: Add `requiresFile: Boolean = true` to `Source`** in `zero-api/.../imports/SnapshotParser.kt`. (Read that file first.)

- [ ] **Step 2: Implement `DriveSnapshotParser`**

- [ ] **Step 3: Unit tests** for the parser against `FakeBackupClient` + `FakeOAuthTokenProvider`.

- [ ] **Step 4: Wire into `ApplicationComponent.importComponentBuilder`** — add `DriveSnapshotParser(...)` to the parsers list.

- [ ] **Step 5: Build**

- [ ] **Step 6: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/imports/SnapshotParser.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/imports/DriveSnapshotParser.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/imports/DriveSnapshotParserTest.kt \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "backup(import): DriveSnapshotParser + Source.requiresFile"
```

---

### Task 2: Source-selection skips file picker for `requiresFile=false`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModel.kt` *(if applicable — read first)*

Today `ImportUseCase.Action.SelectSource` transitions to `State.FilePicker`. We need it to skip directly to `Action.SelectFile` with a sentinel URI when the chosen source doesn't require a file.

- [ ] **Step 1: In `DefaultImportUseCase.perform(SelectSource)`**, after setting `selectedSource`, branch:
  - If `source.requiresFile == true` → existing behaviour (`State.FilePicker`).
  - Else → directly call `perform(SelectFile(Uri.empty-sentinel))`.

Use the existing `Uri` types. The parser ignores the URI when `requiresFile=false`, so any value works; pick a clearly-labelled constant like `Uri.NonEmpty("drive://latest")` for traceability in logs.

- [ ] **Step 2: Source-selection UI gets a Drive entry** automatically — `parsers.map { it.source }` already drives it. No layout change.

- [ ] **Step 3: Compile**

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
git commit -m "backup(import): skip file picker for fileless sources (Drive)"
```

---

### Task 3: All-new fast path in `DefaultImportUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

After computing `delta = syncEngine.delta(snapshot, userId)` and the four match maps (`matchedCategoryByImportId`, `matchedAccountByImportId`, `existingTransactionSignatures`, `existingTransactions` for tx dedup), the use case currently transitions to `State.CategoriesReview` unless everything is empty (then `State.UpToDate`).

Add a third branch: **all-new** — `delta` is non-empty, but every entity in it is new to the local DB.

Condition:
```kotlin
val allNew =
    matchedCategoryByImportId.isEmpty() &&
    matchedAccountByImportId.isEmpty() &&
    duplicateTxIds.isEmpty() &&
    (delta.categories.isNotEmpty() || delta.accounts.isNotEmpty() || delta.transactions.isNotEmpty())
```

When true: call `syncEngine.import(delta, userId)` directly and transition to a new `State.RestoreSuccess(itemCount: Int)` terminal state. The Import screen renders a "Restored X items" success view.

- [ ] **Step 1: Write a unit test first** in `DefaultImportUseCaseTest` (read it to find the existing test style). Build a fresh-DB scenario, feed in a snapshot, assert one `syncEngine.import` call + `State.RestoreSuccess` final.

- [ ] **Step 2: Implement the branch**

- [ ] **Step 3: Add `State.RestoreSuccess`** to `ImportUseCase.State`.

- [ ] **Step 4: Render success view** in `ImportViewProvider` — single composable with a checkmark icon, "Restored X items", and a "Done" button that dispatches `Action.DismissError` (which already resets state).

- [ ] **Step 5: Existing conflict path** still works — write a test for a half-overlap snapshot to confirm we fall through to `CategoriesReview` correctly.

- [ ] **Step 6: Build + run all import tests**

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ \
        zero-core/src/test/java/com/hluhovskyi/zero/imports/
git commit -m "backup(import): all-new fast path skips review when no overlap"
```

---

### Task 4: Wire "Restore" button from Backup settings screen

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/DefaultBackupViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/BackupComponent.kt`

The `Restore` Action currently logs `Timber.w` (Phase 3 placeholder). Now it should:
1. Navigate to the Import destination.
2. Pre-select the Drive source.

Two routes:

**Route A** — `OnRestoreSelectedHandler` injected at `BackupComponent.Builder` level. The `app` layer provides the default handler that:
  - Pulls the global `ImportUseCase` (or the `ImportComponent.Builder`).
  - Calls `importUseCase.perform(SelectSource(driveSource))`.
  - Navigates to the Import destination.

Per `feedback_default_handlers_not_per_screen`: default this handler at the Builder `@Provides` layer with Navigator access in `app`. Don't drop to Noop.

**Route B** — Direct `navigator.navigate(ImportDestination.withSource("drive"))`. Requires adding a query arg to `ImportDestination`.

Pick **Route A** — it matches the existing `OnImportSelectedHandler` pattern in `SettingsComponent`. Less invasive on the navigation surface.

- [ ] **Step 1: Add `OnRestoreSelectedHandler` interface** in `zero-core/.../backup/`.

- [ ] **Step 2: Bind via `BackupComponent.Builder.@BindsInstance`**.

- [ ] **Step 3: Default handler in `app`** at the `BackupComponent.Builder` `@Provides` layer (read how `OnImportSelectedHandler` is defaulted — replicate).

- [ ] **Step 4: `DefaultBackupViewModel.perform(Restore)` calls handler.onRestoreSelected()`.

- [ ] **Step 5: Build + lint + UI inspector verify**

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/backup/ \
        app/src/main/java/com/hluhovskyi/zero/
git commit -m "backup(ui): wire Restore button to Import flow with Drive pre-selected"
```

---

### Task 5: Manual UI verification

- [ ] **Step 1: Acquire emulator + install**

- [ ] **Step 2: Test the full restore flow:**

   1. On a signed-in device, perform a backup (Phase 4 verifies this works).
   2. Clear app data (`./scripts/ui/adb.sh shell pm clear com.hluhovskyi.zero`).
   3. Launch app, navigate to Settings → Backup (will need to re-sign-in).
   4. Tap Restore. Confirm we land on the Import source-selection screen with the Drive source visible.
   5. Tap Google Drive. Confirm we skip the file picker (no SAF dialog).
   6. Verify the fast-path takes us straight to `RestoreSuccess` showing "Restored N items" (since DB is empty).
   7. Verify the categories/accounts/transactions are present in the app.

   3'. **Also verify the conflict path:**
   - Don't clear data; instead, manually add a transaction first.
   - Tap Restore → Drive. Confirm we land on Categories Review (because some entities pre-exist).
   - Walk through the existing review screens. Confirm they work unchanged.

- [ ] **Step 3: Inspector for layout correctness**

Invoke `zero-project:android-ui-inspector` to verify the `RestoreSuccess` view renders correctly.

---

## Verification

```bash
./gradlew :zero-core:testDebugUnitTest 2>&1 | tail -15
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

All MUST pass. Manual restore flow MUST be verified before opening the PR.

## Out of Scope

- Welcome-screen restore prompt — Phase 6.
- Restore from a *specific* backup (multi-slot) — v2.
- Selective restore (categories only, etc.) — not planned.
- Disconnect confirm — Phase 7.
