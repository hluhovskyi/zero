# Phase 4 — Auto Schedule: WorkManager + Wi-Fi Constraint + Failure Notifications

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backup runs automatically every 24 hours on Wi-Fi. User can flip a toggle to allow mobile data. Three consecutive failed auto-backups produce a system notification. Manual `Back up now` ignores the network constraint and coalesces with any in-flight run.

**Architecture:** WorkManager `PeriodicWorkRequest` calls `BackupUseCase.perform(BackupNow)` via a `CoroutineWorker`. The work is enqueued/cancelled by `DefaultBackupViewModel` when sign-in toggles. Notification is posted by an Android-side `BackupNotificationPresenter` observing `backupUseCase.state.consecutiveFailures`. Coalescing already lives in `DefaultBackupUseCase` (Phase 1).

**Tech Stack:** `androidx.work` (existing in zero ecosystem? — confirm in Task 1), `NotificationManagerCompat`.

**Spec:** [Spec §Backup Lifecycle](../specs/2026-05-21-gdrive-backup-design.md#backup-lifecycle)

**Structural analogs:**
- No existing WorkManager precedent in this project at the time of writing — this phase adds the first one. Document the pattern in `app/AGENTS.md` so future workers follow it.

---

### Task 1: Add WorkManager dependency

**Files:**
- Modify: `build.gradle` (root, deps map)
- Modify: `app/build.gradle`

- [ ] **Step 1: Verify WorkManager isn't already present**

Run: `grep -r "androidx.work" build.gradle app/build.gradle 2>&1 | head -5`

If present: skip Step 2 + 3. If absent:

- [ ] **Step 2: Append to root `deps` map**

```groovy
workManager: "androidx.work:work-runtime-ktx:2.10.0",
```

- [ ] **Step 3: Add to `app/build.gradle`**

```
implementation deps.workManager
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add build.gradle app/build.gradle
git commit -m "backup(app): add WorkManager dep"
```

---

### Task 2: `DriveBackupSchedulerWorker`

**Files:**
- Create: `app/src/main/java/com/hluhovskyi/zero/backup/DriveBackupSchedulerWorker.kt`
- Create: `app/src/main/java/com/hluhovskyi/zero/backup/DriveBackupScheduler.kt`

A `CoroutineWorker` that:
1. Pulls `BackupUseCase` from `MainApplication.applicationComponent.backupUseCase` (no Hilt; manual lookup mirrors how existing app classes wire up).
2. Calls `backupUseCase.perform(BackupNow)`.
3. Observes `backupUseCase.state` until `phase != Uploading`.
4. Returns `Result.success()` on `Idle` (successful) or `Result.retry()` on `Failed` (so WorkManager schedules a backoff retry — but inside the same 24h window; we don't want infinite retries).
5. **No-op skip:** before invoking the use case, compare the current `max(updatedDateTime)` (cheap query via a new `SyncEngine.lastModifiedAt(userId): LocalDateTime` helper) with the last successful backup's stored timestamp. If unchanged → `Result.success()` without uploading.

`DriveBackupScheduler` is a small facade with `enable(wifiOnly: Boolean)`, `disable()`, `runOnce()`:
- `enable(wifiOnly)` enqueues `PeriodicWorkRequest`:
  - Constraints: `NetworkType.UNMETERED` if wifiOnly else `NetworkType.CONNECTED`.
  - Initial delay: random 0-6h (avoid thundering herd if a user enables it on many devices at once).
  - Repeat interval: 24h.
  - Backoff: linear, 1h, max 1 retry inside the period.
  - Unique work name: `"drive-backup-periodic"`.
- `disable()` cancels the unique work.
- `runOnce()` enqueues `OneTimeWorkRequest` with no constraints — used by "Back up now" if you wanted to push the orchestration into WM (NOT recommended for v1; we still call `BackupUseCase` directly from the ViewModel on manual tap; `runOnce` is only used by the "force a retry now after notification" deep link).

- [ ] **Step 1: Add `SyncEngine.lastModifiedAt(userId)` helper**

In `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncEngine.kt`, add:

```kotlin
suspend fun lastModifiedAt(userId: Id.Known): kotlinx.datetime.LocalDateTime?
```

Implement in `DefaultSyncEngine` by taking the max `updatedDateTime` across all four pipelines' `exportAll(userId)`. (For a faster variant, add a SQL `MAX(updatedDateTime)` query to each sync DAO — but for v1, in-memory max over the already-needed export is fine since export happens immediately after anyway.)

- [ ] **Step 2: Implement `DriveBackupSchedulerWorker`** per spec above.

- [ ] **Step 3: Implement `DriveBackupScheduler`** per spec above.

- [ ] **Step 4: Register `DriveBackupScheduler` in `ApplicationComponent`** as `@ApplicationScope`. Depends on `WorkManager.getInstance(context)`.

- [ ] **Step 5: Build**

- [ ] **Step 6: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncEngine.kt \
        zero-sync/src/main/java/com/hluhovskyi/zero/sync/DefaultSyncEngine.kt \
        app/src/main/java/com/hluhovskyi/zero/backup/DriveBackupSchedulerWorker.kt \
        app/src/main/java/com/hluhovskyi/zero/backup/DriveBackupScheduler.kt \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "backup(app): DriveBackupScheduler + Worker with no-op skip"
```

---

### Task 3: Wire scheduler to sign-in toggle

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/backup/DefaultBackupViewModel.kt`

`DefaultBackupViewModel` observes `oauthTokenProvider.isSignedIn` and `configurationRepository`-stored `backup.wifiOnly` config. On `isSignedIn` true → `scheduler.enable(wifiOnly)`. On false → `scheduler.disable()`. On `wifiOnly` change while signed-in → re-enqueue.

`DriveBackupScheduler` is not in `zero-core`'s dependency tree directly (it's Android-side / `app`). To keep the ViewModel platform-agnostic-ish, introduce a small interface `BackupScheduler` in `zero-api`:

```kotlin
package com.hluhovskyi.zero.backup
interface BackupScheduler {
    fun enable(wifiOnly: Boolean)
    fun disable()
}
```

And rename `DriveBackupScheduler` → `WorkManagerBackupScheduler : BackupScheduler` in `app`. The ViewModel takes `BackupScheduler`.

- [ ] **Step 1: Add `BackupScheduler` interface in zero-api**

- [ ] **Step 2: Rename in app + wire as `@Provides BackupScheduler` in `ApplicationComponent`**

- [ ] **Step 3: Inject `BackupScheduler` into `BackupComponent.Dependencies` and through to `DefaultBackupViewModel`**

- [ ] **Step 4: Add `backup.wifiOnly` config key**

Add `ScopedConfigurationKey<Boolean>` to wherever existing backup-ish keys would live (search for `ConfigurationKey` usages first). Default `true`.

- [ ] **Step 5: Subscribe in ViewModel**

`attach()` launches a coroutine collecting `combine(isSignedIn, wifiOnly)` and calls `scheduler.enable(...)` / `scheduler.disable()` accordingly. Use existing `combine` patterns from `SettingsViewModel`.

- [ ] **Step 6: Add the "Back up over mobile data" toggle**

Extend `BackupViewModel.Action` with `SetWifiOnly(Boolean)`. Toggle writes via `configurationRepository`. The `BackupViewProvider` renders it as a Material `Switch` inside the existing detail screen (next to "Back up now").

- [ ] **Step 7: Build + lint**

- [ ] **Step 8: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/backup/BackupScheduler.kt \
        zero-api/src/main/java/com/hluhovskyi/zero/config/ \
        zero-core/src/main/java/com/hluhovskyi/zero/backup/ \
        app/src/main/java/com/hluhovskyi/zero/backup/ \
        app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "backup(schedule): wire WorkManager scheduler to sign-in + wifiOnly toggle"
```

---

### Task 4: `BackupNotificationPresenter` (3-strike failure)

**Files:**
- Create: `app/src/main/java/com/hluhovskyi/zero/backup/BackupNotificationPresenter.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/MainApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `POST_NOTIFICATIONS` permission for SDK 33+)
- Add strings to `app/src/main/res/values/strings.xml`

`BackupNotificationPresenter`:
- Constructor takes `Context`, `BackupUseCase`.
- `attach(): Closeable` launches a long-lived collector on `backupUseCase.state` (use a `SupervisorJob` scope from `MainApplication`).
- When `state.consecutiveFailures >= 3`, post (or update) a notification with id `BACKUP_FAILURE_NOTIFICATION_ID`. When `consecutiveFailures < 3` after a Success, cancel the notification.
- Notification text: `"Zero couldn't back up your data"`, with subtext from `BackupError`.
- ContentIntent: deep-link to MainActivity with extra `OPEN_SETTINGS_BACKUP=true`. MainActivity reads the extra and navigates to the Backup screen.
- Channel id `"backup-status"`, importance `IMPORTANCE_DEFAULT`. Channel is created on `MainApplication.onCreate`.

- [ ] **Step 1: Add `POST_NOTIFICATIONS` permission** (SDK 33+)

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Implement `BackupNotificationPresenter`**

- [ ] **Step 3: Wire in `MainApplication`** — create channel in `onCreate`, instantiate `BackupNotificationPresenter` from `ApplicationComponent`, call `attach()` against an app-scoped `CoroutineScope` and ignore the returned `Closeable` (lifetime = app lifetime).

- [ ] **Step 4: Add strings** — backup_notification_title, backup_notification_body_auth, backup_notification_body_network, etc.

- [ ] **Step 5: Deep-link intent extra**

In `MainActivity`, on `onCreate` / `onNewIntent` check for `OPEN_SETTINGS_BACKUP=true` and navigate to `BackupDestination`. Use the existing `Navigator` (read how `MainActivity` handles startup navigation today; replicate). Cancel the notification on tap by adding `setAutoCancel(true)`.

- [ ] **Step 6: Build + lint**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
        app/src/main/java/com/hluhovskyi/zero/backup/BackupNotificationPresenter.kt \
        app/src/main/java/com/hluhovskyi/zero/MainApplication.kt \
        app/src/main/java/com/hluhovskyi/zero/activity/MainActivity.kt \
        app/src/main/res/values/strings.xml
git commit -m "backup(app): 3-strike failure notification + deep link"
```

---

### Task 5: Coalescing verification (test)

`DefaultBackupUseCase` already implements coalescing (Phase 1). This task adds a regression test that **exercises it from the WorkManager + manual tap angle** to catch any future regressions where someone introduces a parallel "is backup running" flag.

**Files:**
- Modify: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/DefaultBackupUseCaseTest.kt`

- [ ] **Step 1: Add coalescing test** — call `perform(BackupNow)` then call it again before the first completes (use a `FakeBackupClient` that suspends until a signal). Assert: exactly one `client.upload` invocation; both observers see the same state transitions.

- [ ] **Step 2: Run**

- [ ] **Step 3: Commit**

```bash
git add zero-backup/src/test/java/com/hluhovskyi/zero/backup/DefaultBackupUseCaseTest.kt
git commit -m "backup: regression test for coalescing concurrent backup requests"
```

---

### Task 6: Manual UI + scheduling verification

- [ ] **Step 1: Acquire emulator** (`./scripts/emulator/acquire`)

- [ ] **Step 2: Install + launch + sign in + verify "Back up over mobile data" toggle exists**

- [ ] **Step 3: Trigger immediate auto-backup**

```bash
./scripts/ui/adb.sh shell cmd jobscheduler run -f com.hluhovskyi.zero <jobId>
# Or use the WorkManager testing hook:
./scripts/ui/adb.sh shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS
```

Confirm the worker runs (`logcat | grep DriveBackupSchedulerWorker`) and the row in Settings reflects a successful backup.

- [ ] **Step 4: Force 3 consecutive failures**

Simulate by toggling airplane mode and tapping `Back up now` 3 times. Confirm a system notification appears after the 3rd failure.

- [ ] **Step 5: Tap notification → app opens directly on Backup screen.**

- [ ] **Step 6: Toggle "Back up over mobile data" off and on → confirm `cmd workmanager` shows the constraint changes.**

```bash
./scripts/ui/adb.sh shell dumpsys jobscheduler | grep -i zero
```

- [ ] **Step 7: Use `zero-project:android-ui-inspector` to verify layout.**

---

## Verification

```bash
./gradlew :zero-backup:test 2>&1 | tail -10
./gradlew :app:lintDebug 2>&1 | grep -E "error:|Error" | head -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

All MUST pass. Manual verification above MUST be performed before opening the PR.

## Out of Scope

- Configurable cadence (Daily / Weekly / Manual) — v2.
- Notification deep-link payload to the specific failure cause — v2 polish.
- Restore — Phase 5.
- Welcome restore prompt — Phase 6.
- Confirm dialog on disconnect — Phase 7.
