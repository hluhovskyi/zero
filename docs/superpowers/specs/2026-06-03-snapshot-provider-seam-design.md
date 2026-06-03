# Snapshot Provider Seam — Design Spec

**Date:** 2026-06-03
**Modules touched:** `zero-api`, `zero-backup`, `zero-core`, `app`
**Status:** Approved for implementation (decided under --no-questions)

---

## Problem

Import/restore funnels every source through one abstraction:

```kotlin
interface SnapshotParser {
    val source: Source
    suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
}
```

This fits **file** sources (`ZeroBackupParser`, `ZenMoneySnapshotParser`) — a pure `bytes → SyncSnapshot` decode of a user-picked file. It does **not** fit Google Drive, which authenticates, hits the network, and has no file. The mismatch leaks as:

- **A sentinel URI.** `DriveSnapshotParser` ignores its `uri` param; callers pass `Uri("drive://latest")` / `Uri("drive://welcome-restore")` purely to satisfy the signature.
- **A `requiresFile` boolean duplicated across layers.** Whether a source skips the file picker is decided in `SourceSelectionViewProvider.sourceCardConfig()` (a UI composable), threaded through `ImportUseCase.Action.SelectSource(source, requiresFile)`, and hardcoded again in `DefaultImportUseCase.attach()`'s pre-select. A UI layer owns a domain fact.
- **Auth hidden inside `parse()`.** A "parser" now triggers an interactive sign-in Activity as a side effect of decoding.

The boolean and the sentinel are symptoms; the cause is a missing type distinction.

## Goal

Replace the single `SnapshotParser` with a sealed **`SnapshotProvider`** that names the two real kinds of source. The flow becomes **type-driven** instead of boolean-driven, the sentinel URI disappears, and `requiresFile` is deleted everywhere. **Behavior-preserving** — no UX or data-flow change.

## Design

### New abstraction (`zero-api`)

```kotlin
sealed interface SnapshotProvider {
    val source: Source

    /** Decodes a user-picked file into a snapshot. Pure: no I/O beyond the given uri. */
    interface File : SnapshotProvider {
        suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
    }

    /** Fetches a snapshot from a remote backend. May sign in / network; owns its own session. */
    interface Remote : SnapshotProvider {
        suspend fun load(): SyncSnapshot
    }
}
```

`Source` (the `{ key }` descriptor) is unchanged. The old `SnapshotParser` interface is removed.

### Impl changes

- `ZeroBackupParser`, `ZenMoneySnapshotParser` implement `SnapshotProvider.File` (rename `parse(uri)` only by interface; bodies unchanged).
- `DriveSnapshotParser` → **`DriveSnapshotLoader`** implementing `SnapshotProvider.Remote`. `load()` keeps the current body minus the `uri` param: ensure sign-in → `backupClient.latest()` → `download()`. `DriveComponent.driveSnapshotParser` → `driveSnapshotLoader` (type `SnapshotProvider.Remote`).

### Flow (`DefaultImportUseCase`)

`SelectSource` dispatches on provider **type**, not a flag:

- `is SnapshotProvider.File` → show `FilePicker`; the subsequent `SelectFile(uri)` calls `provider.parse(uri)`.
- `is SnapshotProvider.Remote` → call `provider.load()` immediately (no picker, no sentinel uri).

Both paths feed the **same** existing post-load pipeline (`syncEngine.delta` → match → all-new fast path **or** review screens). That shared body is extracted into one private `processSnapshot(snapshot)` function so File and Remote converge.

`Action.SelectSource` loses its `requiresFile` param. `attach()`'s pre-select (welcome single-tap) just performs `SelectSource(source)` — the use case sees a `Remote` provider and loads directly. The pre-select still lives in `attach()` (per the construction-vs-attach review on PR #300).

### UI (`SourceSelectionViewProvider` / `SourceSelectionViewModel`)

`SourceCardConfig.requiresFile` and the `SelectSource(source, requiresFile)` plumbing are removed. The card dispatches `SelectSource(source)`; the use case decides picker-vs-load. Card icon/title/description lookup by key is unchanged.

### Wiring (`app`)

`ApplicationComponent.importComponentBuilder` builds `List<SnapshotProvider>` (was `List<SnapshotParser>`) = `[ZeroBackupParser, ZenMoneySnapshotParser, driveComponent.driveSnapshotLoader]`. `ImportComponent.Builder.parsers(List<SnapshotProvider>)` and the single `SnapshotProvider` binding for the welcome restore deep-link update by type only. `MainActivityScreenComponent` is unchanged (it dispatches the same `SelectSource`-by-key behavior via the `InitialSource` arg).

## Error handling

Unchanged. `Remote.load()` throws the same `IllegalStateException`s (`DriveSnapshotLoader`); `DefaultImportUseCase`'s existing `catch` around the load/parse path still routes failures to `SourceSelection(error=…)`.

## Testing

- `DriveSnapshotParserTest` → `DriveSnapshotLoaderTest`: `load()` instead of `parse(sentinel)`; existing cases (signs in on demand, no-backup, download-fail) unchanged in intent.
- `DefaultImportUseCaseTest`: existing fast-path/review cases pass through `processSnapshot` unchanged; add/adjust a case asserting a `Remote` provider skips `FilePicker` and a `File` provider shows it (replacing the old `requiresFile` assertions).
- E2E: the import-from-file and welcome→Drive single-tap flows must still behave identically (manual + `run-android-tests`).

## Out of scope (deferred — documented, not built)

- **Restore-as-replace (`RestoreUseCase`).** Modeling "restore my own backup" as a distinct *replace* operation (vs the foreign-import merge-review pipeline) is the larger win, but it changes data semantics for an existing user (overwrite vs merge) — a product decision, not shippable silently. Captured as a known tension.
- **A first-class session/connection gate.** `SnapshotProvider.Remote` owning its own sign-in is the contained slice of this; a shared "ensure connected" boundary across Settings + Welcome entry points is deferred.
