# Export File Picker Design

**Date:** 2026-04-22

## Summary

Replace the fixed-to-Downloads export with an `ACTION_CREATE_DOCUMENT` SAF picker so users choose where to save their backup. Extract the export pipeline out of the ViewModel into a dedicated `ExportUseCase`.

## Architecture

### New: `ExportUseCase` (zero-core)

```
zero-core/src/main/java/com/hluhovskyi/zero/export/ExportUseCase.kt
zero-core/src/main/java/com/hluhovskyi/zero/export/DefaultExportUseCase.kt
```

`ExportUseCase` is an interface with a single method that returns a custom sealed `Result`:

```kotlin
interface ExportUseCase {
    suspend fun export(uri: Uri.NonEmpty): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val message: String) : Result
    }
}
```

`DefaultExportUseCase` encapsulates the full pipeline:
1. Get user ID from `CurrentUserRepository`
2. Export snapshot via `SyncEngine`
3. Serialize via `SyncSerializer`
4. Write to the SAF URI via `ExportWriter`

### Changed: `ExportWriter` (zero-core)

Signature changes from `write(fileName: String, content: String)` to:

```kotlin
fun interface ExportWriter {
    suspend fun write(uri: Uri.NonEmpty, content: String)
}
```

Uses the project's own `Uri.NonEmpty` type, consistent with `ImportUseCase`.

### Changed: `DefaultExportWriter` (app)

Simplified — parses `uri.value` to `android.net.Uri`, opens an output stream via `contentResolver`, writes bytes. The entire MediaStore / API-29 branching is deleted; SAF handles all API levels.

### Changed: `SettingsViewModel`

`Action.Export` becomes a data class:

```kotlin
data class Export(val uri: Uri.NonEmpty) : Action
```

### Changed: `DefaultSettingsViewModel`

Drops `SyncEngine`, `CurrentUserRepository`, `SyncSerializer`, and `ExportWriter` constructor params. Takes only `ExportUseCase`. Export handling becomes:

```kotlin
is SettingsViewModel.Action.Export -> coroutineScope.launch {
    when (val result = exportUseCase.export(action.uri)) {
        ExportUseCase.Result.Success ->
            mutableState.update { it.copy(exportFeedback = ExportFeedback.Success) }
        is ExportUseCase.Result.Failure ->
            mutableState.update { it.copy(exportFeedback = ExportFeedback.Error(result.message)) }
    }
}
```

### Changed: `SettingsViewProvider`

Adds a `rememberLauncherForActivityResult(CreateDocument("application/json"))` following the same pattern as `ImportViewProvider`. The export button click launches the picker directly with a date-stamped filename suggestion (`zero-backup-YYYY-MM-DD.json`). When the URI is returned, it calls `viewModel.perform(Action.Export(uri))`. Snackbar on success changes to "Backup saved".

### Changed: `SettingsComponent`

`Module` adds a `@Provides fun exportUseCase(...)` binding and updates `viewModel()` to take `ExportUseCase` instead of raw sync dependencies.

`Dependencies` and `ApplicationComponent` are **unchanged** — the sync deps are still declared there (now consumed by `DefaultExportUseCase` inside the module, not by the ViewModel directly).

## Data Flow

```
Button click
  → exportLauncher.launch("zero-backup-2026-04-22.json")
  → System file picker (ACTION_CREATE_DOCUMENT)
  → User picks location
  → URI returned to ViewProvider
  → viewModel.perform(Action.Export(uri))
  → exportUseCase.export(uri) → ExportUseCase.Result
      (internally: get userId → export snapshot → serialize → write)
  → Result.Success → ExportFeedback.Success → snackbar "Backup saved"
  → Result.Failure(msg) → ExportFeedback.Error(msg) → snackbar "Export failed: …"
```

## Error Handling

`ExportUseCase.export()` returns `ExportUseCase.Result` — no exceptions escape the use case. `DefaultExportUseCase` wraps its pipeline in a try/catch and returns `Result.Failure(message)` on error. The ViewModel `when`-matches the result and maps it to `ExportFeedback`. Failures surface as `ExportFeedback.Error` in the snackbar.

If the user cancels the file picker (returns null URI), the launcher callback does nothing — no ViewModel action is dispatched, no state changes.

## Files

| Action | File |
|--------|------|
| New | `zero-core/.../export/ExportUseCase.kt` |
| New | `zero-core/.../export/DefaultExportUseCase.kt` |
| Modified | `zero-core/.../export/ExportWriter.kt` |
| Modified | `app/.../export/DefaultExportWriter.kt` |
| Modified | `zero-core/.../settings/SettingsViewModel.kt` |
| Modified | `zero-core/.../settings/DefaultSettingsViewModel.kt` |
| Modified | `zero-core/.../settings/SettingsComponent.kt` |
| Modified | `zero-core/.../settings/SettingsViewProvider.kt` |
