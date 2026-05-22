# Phase 6 — Welcome Screen Restore Prompt

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a fresh install, the Welcome flow asks the user whether they want to restore from Google Drive before the presets step. If yes and a backup exists, restore it. Otherwise fall through to the existing presets step.

**Architecture:** New step in the existing Welcome flow. The step composes Phase 5's restore path: sign in → call `DriveSnapshotParser` → run through `DefaultImportUseCase` (fast path when DB is empty, which is by definition true here).

**Tech Stack:** Existing — Compose, the existing Welcome component, the existing Import flow.

**Spec:** [Spec §Restore Lifecycle](../specs/2026-05-21-gdrive-backup-design.md#restore-lifecycle) — "First-launch prompt".

**Structural analogs:**
- `WelcomeComponent` (or whatever the existing first-install component is — find it via `grep -r "welcome" zero-core/src/main`).
- Phase 3's `BackupViewProvider` Connect CTA — same sign-in flow.

---

### Task 1: Discover the existing Welcome flow

The plan can't be exact without knowing the existing structure. **The executing agent MUST do this discovery before writing code.**

- [ ] **Step 1: Locate the Welcome component**

```bash
grep -r "class WelcomeComponent\|class WelcomeViewModel\|welcome" \
    zero-core/src/main app/src/main --include="*.kt" | head -10
```

- [ ] **Step 2: Read it**

Read the relevant `WelcomeComponent.kt`, `WelcomeViewModel.kt`, `WelcomeViewProvider.kt` files.

- [ ] **Step 3: Identify the right insertion point**

There's likely a state machine (e.g. `State.Greeting → State.Presets → State.Done`). The restore step goes between `Greeting` and `Presets`.

- [ ] **Step 4: Document the analog for this phase plan**

In a `## Welcome flow as of <date>` comment at the top of any code you add, note the existing structure you found and how the new step plugs in.

---

### Task 2: Add `State.RestorePrompt` to the Welcome flow

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/welcome/...`

New state values (names depend on what exists):
- `State.RestorePrompt` — the screen asking "Restore from Google Drive?" with `Connect` + `Skip` buttons.
- `State.Restoring` — shown while the snapshot is downloading + applying.
- `State.RestoreSuccess` — brief confirmation, then transitions to `State.Done`.
- `State.RestoreNotFound` — "No backup found — let's set you up." Tap `Continue` → transitions to `State.Presets`.

Transitions:
- `Greeting → RestorePrompt`
- `RestorePrompt.Connect → Restoring` (kicks off sign-in)
- `RestorePrompt.Skip → Presets`
- `Restoring → RestoreSuccess` (on success) or `RestoreNotFound` (no backup) or back to `RestorePrompt` with an inline error (failure)
- `RestoreSuccess → Done` (auto-advances after ~1.5s or on tap)
- `RestoreNotFound → Presets`

- [ ] **Step 1: Add the new state values + transitions in the welcome ViewModel/UseCase.**

- [ ] **Step 2: Render the new screens** in `WelcomeViewProvider`. Reuse existing CTA + skip-button styles from the current welcome flow.

- [ ] **Step 3: Hook `OAuthTokenProvider.signIn()` and the restore call**

The restore call is *the same as Phase 5's flow*. The cleanest reuse: invoke the existing `DriveSnapshotParser` + `SyncEngine.import` directly from the welcome ViewModel rather than re-using `DefaultImportUseCase` (which has more orchestration than we need here — we know DB is empty).

```kotlin
// Inside Welcome ViewModel
val token = oauthTokenProvider.signIn()
if (token !is Success) { showError(); return }
val snapshot = try { driveSnapshotParser.parse(Uri.empty-sentinel) } catch (NotFound) { transitionToNotFound(); return }
syncEngine.import(snapshot, userId)
transitionToRestoreSuccess()
```

Per `feedback_viewmodel_no_derivation`: keep this orchestration in a use case (`WelcomeRestoreUseCase` or extend the existing welcome use case) rather than in the ViewModel.

- [ ] **Step 4: Strings**

Add: welcome_restore_prompt_title, welcome_restore_prompt_body, welcome_restore_connect, welcome_restore_skip, welcome_restore_in_progress, welcome_restore_success, welcome_restore_not_found, welcome_restore_failed.

- [ ] **Step 5: Build + lint**

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/welcome/ \
        zero-core/src/main/res/values/strings.xml
git commit -m "backup(welcome): RestorePrompt step before presets"
```

---

### Task 3: Wire Drive dependencies into Welcome component

**Files:**
- Modify: welcome component's `Dependencies` interface
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt` (so `Welcome` can see `BackupClient`, `OAuthTokenProvider`, `SyncEngine`)

- [ ] **Step 1: Extend Welcome `Dependencies`** with `oauthTokenProvider`, `driveSnapshotParser`, `syncEngine`, `currentUserRepository`.

- [ ] **Step 2: `ApplicationComponent` is already implementing most of these**. Add the missing ones.

- [ ] **Step 3: Build**

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/welcome/ \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "backup(welcome): wire Drive deps into Welcome component"
```

---

### Task 4: Manual UI verification

- [ ] **Step 1: Acquire emulator + clear app data**

```bash
./scripts/emulator/acquire
./scripts/ui/adb.sh shell pm clear com.hluhovskyi.zero
./scripts/install-app.sh
```

- [ ] **Step 2: Launch and walk through:**

   1. Greeting screen appears.
   2. **New screen**: "Restore from Google Drive?" with Connect and Skip buttons.
   3. **Path A: Skip** → presets screen appears (existing behaviour) → finish setup.
   4. Clear data, restart.
   5. **Path B: Connect (no backup in Drive)** → sign-in → "No backup found" message → Continue → presets.
   6. Clear data, restart. (Use a Google account that has an existing backup from earlier testing.)
   7. **Path C: Connect with existing backup** → sign-in → spinner → "Restored N items" → app opens with the data populated. No presets shown (we already have categories/accounts).

- [ ] **Step 3: Verify with inspector**

Invoke `zero-project:android-ui-inspector` for each state. Confirm copy, button placement, no overflow.

---

## Verification

```bash
./gradlew :zero-core:testDebugUnitTest 2>&1 | tail -10
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

All MUST pass. The three manual paths (Skip, NotFound, Success) MUST all be verified before opening the PR.

## Out of Scope

- Restoring a *specific* backup version — single-slot model means there's only one to restore.
- Restore progress indicator beyond a spinner — backup is small, restore is fast.
- Account-switching during welcome — user chooses one account; switching means starting over.
