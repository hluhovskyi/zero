# SnapshotProvider Seam — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the single `SnapshotParser` interface with a sealed `SnapshotProvider` (`File` + `Remote`) so import/restore dispatches by source *type* instead of a `requiresFile` boolean, deleting the boolean and the Drive sentinel URI. Behavior-preserving.

**Architecture:** See [spec](../specs/2026-06-03-snapshot-provider-seam-design.md). File sources decode a picked URI (`parse(uri)`); remote sources fetch directly (`load()`). The import use case picks a provider by key, switches on its type, and feeds both into one shared post-load pipeline.

**Tech Stack:** Existing — Kotlin, Dagger, the import flow.

---

### Task 1: Introduce the `SnapshotProvider` abstraction

**Files:**
- Replace: `zero-api/src/main/java/com/hluhovskyi/zero/imports/SnapshotParser.kt`

- [ ] **Step 1: Replace the interface**

Rename the file's contents to a sealed interface (keep the filename `SnapshotParser.kt` → rename to `SnapshotProvider.kt`):

```kotlin
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncSnapshot

sealed interface SnapshotProvider {
    val source: Source

    /** Decodes a user-picked file into a snapshot. */
    interface File : SnapshotProvider {
        suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
    }

    /** Fetches a snapshot from a remote backend; may sign in / network. */
    interface Remote : SnapshotProvider {
        suspend fun load(): SyncSnapshot
    }
}
```

- [ ] **Step 2: Delete the old file** — `git rm zero-api/src/main/java/com/hluhovskyi/zero/imports/SnapshotParser.kt` and add `SnapshotProvider.kt` (or rename in place). Do not compile yet — consumers still reference the old type.

---

### Task 2: File parsers implement `SnapshotProvider.File`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ZeroBackupParser.kt:9`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ZenMoneySnapshotParser.kt:35`

- [ ] **Step 1:** In `ZeroBackupParser`, change `) : SnapshotParser {` → `) : SnapshotProvider.File {`. Body (`override val source`, `override suspend fun parse`) unchanged.
- [ ] **Step 2:** In `ZenMoneySnapshotParser`, change `) : SnapshotParser {` → `) : SnapshotProvider.File {`. Body unchanged.

---

### Task 3: Drive becomes a `Remote` loader

**Files:**
- Rename: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveSnapshotParser.kt` → `DriveSnapshotLoader.kt`
- Modify: `zero-backup/src/main/java/com/hluhovskyi/zero/backup/DriveComponent.kt`

- [ ] **Step 1: Rename class + interface + drop the uri**

`DriveSnapshotLoader` implements `SnapshotProvider.Remote`. Replace `override suspend fun parse(uri: Uri.NonEmpty)` with `override suspend fun load()`; drop the `uri` param and the `Uri` import. Keep `ensureSignedIn()`, `latest()`/`download()`, `toMessage()`, the `DriveSource` object, and `companion object { const val KEY = "drive" }` exactly. Class signature:

```kotlin
class DriveSnapshotLoader(
    private val backupClient: BackupClient,
    private val oauthTokenProvider: OAuthTokenProvider,
) : SnapshotProvider.Remote {

    override val source: Source = DriveSource

    override suspend fun load(): SyncSnapshot {
        ensureSignedIn()
        val metadata = when (val result = backupClient.latest()) { /* unchanged */ }
        return when (val result = backupClient.download(metadata.backupId)) { /* unchanged */ }
    }
    // ensureSignedIn(), toMessage(), DriveSource, companion unchanged
}
```

- [ ] **Step 2: Update `DriveComponent`** — rename property `driveSnapshotParser: SnapshotParser` → `driveSnapshotLoader: SnapshotProvider.Remote`, and in `DefaultDriveComponent` rename the `by lazy` and its `DriveSnapshotParser(...)` call to `DriveSnapshotLoader(...)`. Update the `import com.hluhovskyi.zero.imports.SnapshotParser` → `SnapshotProvider`.

---

### Task 4: Rename the Drive test

**Files:**
- Rename: `zero-backup/src/test/java/com/hluhovskyi/zero/backup/DriveSnapshotParserTest.kt` → `DriveSnapshotLoaderTest.kt`

- [ ] **Step 1:** Rename class to `DriveSnapshotLoaderTest`; construct `DriveSnapshotLoader(...)`; replace every `parser.parse(sentinelUri)` with `loader.load()`; delete the `sentinelUri` val and the `Uri` import. Test intents unchanged (signs-in-on-demand, no-backup, download-fail, signs-in-cancelled, source-key).
- [ ] **Step 2: Run** `./gradlew :zero-backup:test` — Expected: PASS.

---

### Task 5: Type-driven dispatch in `DefaultImportUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

- [ ] **Step 1: Rename the field** — constructor param `parsers: List<SnapshotParser>` → `providers: List<SnapshotProvider>`. Update `mutableState` init `SourceSelection(parsers.map { it.source })` → `providers.map { it.source }`, and the same expression in the `DismissError`/`Back` branches.

- [ ] **Step 2: Replace the `SelectSource` branch** (currently lines ~67-76):

```kotlin
is ImportUseCase.Action.SelectSource -> {
    mutableState.update { current -> current.copy(selectedSource = action.source) }
    when (providers.firstOrNull { it.source.key == action.source.key }) {
        is SnapshotProvider.File ->
            mutableState.update { it.copy(screen = ImportUseCase.State.FilePicker) }
        is SnapshotProvider.Remote ->
            coroutineScope.launch { loadAndProcess(action.source) { (it as SnapshotProvider.Remote).load() } }
        null -> Unit
    }
}
```

To keep the lookup single, use a helper instead of the cast-in-lambda. Final form:

```kotlin
is ImportUseCase.Action.SelectSource -> {
    mutableState.update { current -> current.copy(selectedSource = action.source) }
    val provider = providers.firstOrNull { it.source.key == action.source.key }
    when (provider) {
        is SnapshotProvider.File -> mutableState.update { it.copy(screen = ImportUseCase.State.FilePicker) }
        is SnapshotProvider.Remote -> coroutineScope.launch { loadAndProcess { provider.load() } }
        null -> Unit
    }
}
```

- [ ] **Step 3: Replace the `SelectFile` branch** (currently lines ~77-…, up to the closing of its `try`). Keep the whole post-fetch body but move it into `loadAndProcess`:

```kotlin
is ImportUseCase.Action.SelectFile -> {
    val source = mutableState.value.selectedSource ?: return
    val provider = providers.firstOrNull { it.source.key == source.key }
    if (provider is SnapshotProvider.File) {
        coroutineScope.launch { loadAndProcess { provider.parse(action.uri) } }
    }
}
```

- [ ] **Step 4: Extract `loadAndProcess`** — wrap the existing load+delta+match+review body. The fetch lambda replaces `parser.parse(action.uri)`; everything from `val delta = syncEngine.delta(...)` through the `CategoriesReview` state update stays verbatim, and the existing `catch (e: Exception)` stays:

```kotlin
private suspend fun loadAndProcess(fetch: suspend () -> SyncSnapshot) {
    mutableState.update { it.copy(screen = ImportUseCase.State.Loading) }
    val userId = currentUserRepository.query().first().id
    try {
        val snapshot = fetch()
        val delta = syncEngine.delta(snapshot, userId)
        // ... existing body verbatim: allIcons, matchedCategoryByImportId, matchedAccountByImportId,
        //     existingTransactionSignatures, duplicateTxIds, buildCategories, UpToDate check,
        //     allNew fast-path (syncEngine.import + RestoreSuccess), defaults, CategoriesReview ...
    } catch (e: Exception) {
        mutableState.update { current ->
            InternalState(
                selectedSource = current.selectedSource,
                screen = ImportUseCase.State.SourceSelection(
                    sources = providers.map { it.source },
                    error = "Couldn't read file. Check the format and try again.",
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Fix `attach()` pre-select** — drop the `requiresFile` argument; it is now type-derived:

```kotlin
override fun attach(): Closeable {
    if (!initialSourceSelected && initialSourceKey != null) {
        initialSourceSelected = true
        providers.firstOrNull { it.source.key == initialSourceKey }?.let { provider ->
            perform(ImportUseCase.Action.SelectSource(provider.source))
        }
    }
    return Closeables.empty()
}
```

- [ ] **Step 6: Delete** the `FILELESS_SOURCE_URI` const and the now-unused `Uri` import if no other reference remains (grep first).

---

### Task 6: Drop `requiresFile` from the action + source-selection UI

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt:14`
- Modify: `.../imports/sourceselection/SourceSelectionViewModel.kt`
- Modify: `.../imports/sourceselection/DefaultSourceSelectionViewModel.kt`
- Modify: `.../imports/sourceselection/SourceSelectionViewProvider.kt`

- [ ] **Step 1:** `ImportUseCase.Action.SelectSource` → `data class SelectSource(val source: Source) : Action` (drop `requiresFile`).
- [ ] **Step 2:** `SourceSelectionViewModel.Action.SelectSource` → `data class SelectSource(val source: Source) : Action`.
- [ ] **Step 3:** `DefaultSourceSelectionViewModel` → `importUseCase.perform(ImportUseCase.Action.SelectSource(action.source))`.
- [ ] **Step 4:** In `SourceSelectionViewProvider`: remove `SourceCardConfig.requiresFile`, the `requiresFile = false` on the Drive config, and change `SourceCard`'s `onClick: (requiresFile: Boolean) -> Unit` to `onClick: () -> Unit`; the card calls `onClick()` and the caller dispatches `SelectSource(source)`. Keep icon/title/description lookup unchanged.

---

### Task 7: Update DI wiring

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`

- [ ] **Step 1:** `ImportComponent`: `parsers(parsers: List<SnapshotParser>)` → `providers(providers: List<SnapshotProvider>)` (rename `@BindsInstance` method + the `useCase` provider param + the companion default `.providers(emptyList())`); update imports. Pass `providers = providers` to `DefaultImportUseCase`.
- [ ] **Step 2:** `ApplicationComponent.importComponentBuilder`: the `List<SnapshotParser>` local → `List<SnapshotProvider>`, last element `driveComponent.driveSnapshotParser` → `driveComponent.driveSnapshotLoader`, and `.parsers(parsers)` → `.providers(parsers)`. The standalone `@Provides fun driveSnapshotParser(...): SnapshotParser` (added in PR #300 for the welcome deep-link) → `driveSnapshotLoader(...): SnapshotProvider` returning `driveComponent.driveSnapshotLoader`. Update imports `SnapshotParser` → `SnapshotProvider`.

---

### Task 8: Build, test, verify

- [ ] **Step 1:** `./gradlew :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL (Dagger codegen passes).
- [ ] **Step 2:** Adjust `DefaultImportUseCaseTest`: any test asserting `SelectSource(..., requiresFile=...)` drops the arg; if a test fed a fake `SnapshotParser`, make the fake implement `SnapshotProvider.File` (file fakes) or `SnapshotProvider.Remote` (a remote fake). Add one case: selecting a `Remote` provider transitions straight to `Loading`/terminal without `FilePicker`; selecting a `File` provider shows `FilePicker`.
- [ ] **Step 3:** `./gradlew :zero-core:testDebugUnitTest :zero-backup:test :app:lintDebug` — Expected: PASS, no `ZeroThemeBypass`.
- [ ] **Step 4: Device verify** (acquire emulator, install, clear data): import-from-file flow still shows the picker and imports; welcome "Restore from Google Drive" still single-taps straight to Drive sign-in. Inspector-confirm the source-selection screen renders unchanged.
- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: split SnapshotParser into SnapshotProvider File/Remote seam"
```

## Verification

```bash
./gradlew :zero-core:testDebugUnitTest :zero-backup:test :app:lintDebug :app:assembleDebug 2>&1 | tail -15
```

All MUST pass. File-import and welcome→Drive single-tap MUST behave identically to pre-refactor.
