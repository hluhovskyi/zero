# Import / Export Rework — Design Spec

## Goal

Replace the placeholder import/export flow with a fully designed, `SyncEngine`-powered implementation. Export saves a `SyncSnapshot` file. Import is a multi-step flow that loads a file through a `SnapshotParser`, computes a delta against the current DB, shows a read-only review, and only commits on explicit confirmation.

---

## What Gets Deleted

All existing import infrastructure is replaced:

**zero-api:**
- `imports/ImportAccount.kt`
- `imports/ImportCategory.kt`
- `imports/ImportTransaction.kt`
- `imports/ImportSourceUseCase.kt`

**zero-core:**
- `imports/ImportUseCase.kt` + `DefaultImportUseCase.kt`
- `imports/ImportViewModel.kt` + `DefaultImportViewModel.kt`
- `imports/ImportViewProvider.kt`
- `imports/ImportComponent.kt`
- `imports/OnImportFinishedHandler.kt`
- `imports/filepicker/*`
- `imports/accounts/*`
- `imports/categories/*`
- `imports/transactions/*`

**zero-zenmoney:** entire module deleted — contents moved to `zero-core`

**app:**
- `ImportModule` (wiring for `ZenMoneyImportComponent` / `ImportSourceUseCase`)

---

## Export

**Entry point:** Existing "Export Data" row in `SettingsViewProvider` (currently a `/* placeholder */` click). Wire it up — no new screen needed.

**Flow:**
1. User taps "Export Data" in Settings
2. `SettingsViewModel` dispatches `Action.Export`
3. `DefaultSettingsViewModel` calls `SyncEngine.export(currentUserId)` → `SyncSnapshot`
4. Serializes with `SyncSerializer.serialize(snapshot)` → JSON string
5. Writes to `Downloads/<app-name>-backup-<YYYY-MM-DD>.json` via `MediaStore` API
6. Emits a success/error state; `SettingsViewProvider` shows a snackbar

**New `SettingsComponent` dependencies:**
```kotlin
interface Dependencies {
    // existing...
    val syncEngine: SyncEngine
    val currentUserRepository: CurrentUserRepository
}
```

**New `SettingsViewModel` additions:**
```kotlin
sealed interface Action {
    // existing...
    object Export : Action
}

sealed interface State {
    // existing...
    object ExportSuccess : State
    data class ExportError(val message: String) : State
}
```

---

## Source & SnapshotParser

### Source

`Source` identifies who provides the data. It carries display metadata and a stable key for persistence/logging. `KnownSource` is a sealed class of first-party variants — lives in `app` so lower modules never depend on concrete sources.

```kotlin
// zero-api
interface Source {
    val key: String        // stable identifier, e.g. "zero_backup", "zenmoney"
    val label: String      // display name
    val description: String
}
```

```kotlin
// app
sealed class KnownSource(
    override val key: String,
    override val label: String,
    override val description: String,
) : Source {
    object ZeroBackup : KnownSource(
        key = "zero_backup",
        label = "Zero Backup",
        description = "Restore from a Zero backup file",
    )
    object ZenMoney : KnownSource(
        key = "zenmoney",
        label = "ZenMoney",
        description = "Import from a ZenMoney CSV export",
    )
}
```

### SnapshotParser

Parses a file URI into a `SyncSnapshot`. References its `Source` so the use case and UI know which source produced a given parser.

```kotlin
// zero-api
interface SnapshotParser {
    val source: Source
    suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
}
```

Two implementations:

**`ZeroBackupParser`** (in `app` module):
```kotlin
class ZeroBackupParser(
    private val resourceResolver: ResourceResolver,
    private val serializer: SyncSerializer,
) : SnapshotParser {
    override val source: Source = KnownSource.ZeroBackup

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot {
        val result = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull() ?: error("Could not read file: $uri")
        val json = result.result.inputStream.bufferedReader().use { it.readText() }
        return serializer.deserialize(json)
    }
}
```

**`ZenMoneySnapshotParser`** (in `zero-core` — see ZenMoney Rework section):
- Replaces `ZenMoneyImportSourceUseCase`
- Same CSV parsing logic as today
- Output: constructs `SyncSnapshot` with synthetic `SyncCategory`, `SyncAccount`, `SyncTransaction` entries
- Unmapped fields default: `iconId = null`, `colorId = null`, `parentCategoryId = null`, `initialBalance = "0"`, `deletedAt = null`
- `creationDateTime` / `updatedDateTime` = parsed transaction date or `now()`
- `userId` in snapshot = placeholder `Id.Known("zenmoney-import")` — overridden by the import use case when calling `SyncEngine.delta(snapshot, currentUserId)`

---

## SyncEngine.delta()

Two new methods on the `SyncEngine` interface:

```kotlin
interface SyncEngine {
    suspend fun export(userId: Id.Known): SyncSnapshot
    suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot   // new — reads + deserializes
    suspend fun import(snapshot: SyncSnapshot, userId: Id.Known)
    suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot  // new
}
```

`loadSnapshot` is the reverse of `export`: reads the file at `uri` and deserializes it via `SyncSerializer`. `SyncComponent.Dependencies` gains `resourceResolver: ResourceResolver` to support this.

`ZeroBackupParser` becomes trivially thin:
```kotlin
class ZeroBackupParser(
    private val syncEngine: SyncEngine,
) : SnapshotParser {
    override val source: Source = KnownSource.ZeroBackup
    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot = syncEngine.loadSnapshot(uri)
}
```

**Semantics:** Returns a `SyncSnapshot` containing only the entities from `snapshot` that would actually be upserted into the DB if `import()` were called — i.e. entities that are new or win LWW against what's currently stored. The returned snapshot has the same `version`, `userId`, and `exportedAt` as the input.

**`DefaultSyncEngine` implementation:**
```kotlin
override suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot {
    return snapshot.copy(
        categories = computeDelta(categoryPipeline, snapshot.categories, userId),
        accounts = computeDelta(accountPipeline, snapshot.accounts, userId),
        transactions = computeDelta(transactionPipeline, snapshot.transactions, userId),
    )
}

private suspend fun <T : SyncEntity> computeDelta(
    pipeline: SyncPipeline<T>,
    incoming: List<T>,
    userId: Id.Known,
): List<T> {
    val stored = pipeline.source.exportAll(userId).associateBy { it.id }
    return incoming.flatMap { entity ->
        pipeline.resolver.resolve(stored[entity.id], entity)
            .filter { it != stored[entity.id] }
    }
}
```

This reuses `mergePipeline` logic exactly — just without the `sink.syncUpsert()` call.

---

## Import Flow

### State Machine

```
SourceSelection ←→ FilePicker* ←→ CategoriesReview ←→ AccountsReview ←→ TransactionsPreview
```

All state lives in `ImportUseCase`. Each step transitions forward and backward. `FilePicker` is an Android system dialog — it has no back button of its own; dismissing it without picking a file returns to `SourceSelection`. All other steps show a back arrow `←`.

`SourceSelection` has a close button (✕) that exits the import flow entirely via `onImportFinishedHandler`.

### ImportUseCase

```kotlin
// zero-core
interface ImportUseCase : AttachableActionStateModel<ImportUseCase.Action, ImportUseCase.State> {

    sealed interface Action {
        data class SelectSource(val source: Source) : Action   // parser resolved internally by key
        data class SelectFile(val uri: Uri.NonEmpty) : Action
        object ConfirmCategories : Action   // "Next" on Categories Review screen
        object ConfirmAccounts : Action     // "Next" on Accounts Review screen
        object Confirm : Action             // "Confirm & Import" on Transactions Preview
        object Back : Action
    }

    sealed interface State {
        data class SourceSelection(val sources: List<Source>) : State
        object FilePicker : State
        data class CategoriesReview(
            val categories: List<ImportCategory>,
        ) : State
        data class AccountsReview(
            val accounts: List<ImportAccount>,
        ) : State
        data class TransactionsPreview(
            val transactions: List<ImportTransaction>,
            val totalCount: Int,
        ) : State
    }
}
```

Where `ImportCategory`, `ImportAccount`, `ImportTransaction` are new lightweight display models in **zero-core** (not zero-api — they're UI-only):

```kotlin
// zero-core — display models for import review screens
data class ImportCategory(val id: Id.Known, val name: String, val iconId: Id?, val colorId: Id?)
data class ImportAccount(val id: Id.Known, val name: String, val currencyId: Id.Known, val transactionCount: Int)
sealed interface ImportTransaction {
    val id: Id.Known
    val accountId: Id.Known
    val currencyId: Id.Known
    val amount: Amount
    val dateTime: LocalDateTime

    data class Expense(
        override val id: Id.Known, override val accountId: Id.Known,
        override val currencyId: Id.Known, override val amount: Amount,
        override val dateTime: LocalDateTime, val categoryId: Id.Known?,
    ) : ImportTransaction

    data class Income(
        override val id: Id.Known, override val accountId: Id.Known,
        override val currencyId: Id.Known, override val amount: Amount,
        override val dateTime: LocalDateTime, val categoryId: Id.Known?,
    ) : ImportTransaction

    data class Transfer(
        override val id: Id.Known, override val accountId: Id.Known,
        override val currencyId: Id.Known, override val amount: Amount,
        override val dateTime: LocalDateTime,
        val targetAccountId: Id.Known, val targetAmount: Amount, val targetCurrencyId: Id.Known,
    ) : ImportTransaction
}
```

### DefaultImportUseCase flow

```
SelectSource → resolves parser from injected `List<SnapshotParser>` by `source.key`, stores it, emits FilePicker
SelectFile(uri) →
    1. converter.convert(uri) → SyncSnapshot
    2. syncEngine.delta(snapshot, currentUserId) → delta (stored internally)
    3. maps delta.categories → List<ImportCategory>
    4. emits CategoriesReview
ConfirmCategories →
    1. maps delta.accounts → List<ImportAccount>
    2. emits AccountsReview
ConfirmAccounts →
    1. maps delta.transactions → List<ImportTransaction>
    2. emits TransactionsPreview
Confirm →
    1. calls syncEngine.import(delta, currentUserId)  // full delta, no filtering
    2. calls onImportFinishedHandler.onFinished()
Back (from CategoriesReview)    → clears delta + stored parser, emits SourceSelection
Back (from AccountsReview)      → emits CategoriesReview (reuses stored delta.categories)
Back (from TransactionsPreview) → emits AccountsReview (reuses stored delta.accounts)
```

`FilePicker` dismissal without a file selection is handled by the file picker component calling `Back` on the use case, which returns to `SourceSelection`.

Categories are **display-only** — there is no category selection step. All categories from the delta are imported as part of `Confirm`.

### ImportComponent

Dependencies simplified — no more `AccountRepository`, `CategoryRepository`, `TransactionRepository` (insertion now happens via `SyncEngine`):

```kotlin
interface Dependencies {
    val syncEngine: SyncEngine
    val currentUserRepository: CurrentUserRepository
    val iconRepository: IconRepository
    val colorRepository: ColorRepository
    val amountFormatter: AmountFormatter
    val dateFormatter: DateFormatter
    val imageLoader: ImageLoader
}
```

Converters are injected via `@BindsInstance` on the builder (list of `SnapshotParser`).

---

## Import Screen Designs

### Step 1: Source Selection

Full-screen list. Header: "Import Data" / "Choose how you'd like to import your financial data." Each source is a card: icon placeholder + label + description. Tapping a source transitions to FilePicker.

### Step 2: File Picker

Existing Android file picker intent (unchanged from current implementation). On file selected → triggers delta computation and transitions to CategoriesReview.

A loading state is shown while `converter.convert()` + `delta()` runs.

### Step 3: Categories Review

**Screen title:** "Categories" with a section header `MANAGEMENT`.

Read-only list. No checkboxes — display only. Each row is a `CategoryCard`:

```
[ CategoryIconView (40dp) ]   [ Category Name ]
```

Icon and color are resolved from `importCategory.iconId`/`colorId` using `IconRepository` / `ColorRepository`. If null (e.g. ZenMoney import), a default neutral grey scheme is used.

"Next →" button at the bottom.

### Step 4: Review Accounts

**Screen title:** "Review Accounts"
**Step indicator:** e.g. "STEP 3 OF 4"

Read-only list. Each row mirrors the `CategoryCard` pattern:
```
[ Account icon ]  Account Name
                  Currency • N Transactions
```

Transaction count comes from the delta. No toggle, no selection — display only.

"Next: Review Transactions →" button at the bottom.

### Step 5: Transactions Preview (Final Review)

**Screen title:** "Review & Finalize"
**Step indicator:** "STEP 4 OF 4"

Grouped-by-date read-only transaction list. Uses existing `TransactionExpenseView`, `TransactionIncomeView`, `TransactionTransferView` from `zero-ui`. Only transactions belonging to selected accounts are shown.

Bottom bar: "N Transactions ready to import" summary + "Confirm & Import" button.

---

## CategoryCard (zero-ui)

New reusable composable alongside `CategoryIconView`:

```kotlin
// zero-ui
@Composable
fun CategoryCard(
    name: String,
    modifier: Modifier = Modifier,
    colorScheme: UiColorScheme? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CategoryIconView(
            colorScheme = colorScheme ?: UiColorScheme.default(),
            size = 40.dp,
            contentPadding = 8.dp,
        ) { tint ->
            icon?.invoke(tint)
        }
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
```

No selection state, no click handler. A `UiColorScheme.default()` factory provides a neutral fallback (grey background, grey icon tint).

---

## ZenMoney Rework

`zero-zenmoney` is deleted. `KnownSource` and all parser implementations move into `zero-core` alongside `ImportComponent` — same module, same package (`imports`). This removes the dependency gap: the import UI can pattern-match on `KnownSource` directly for source-specific icon rendering, and no new module is needed.

```kotlin
// zero-core — imports package
sealed class KnownSource(
    override val key: String,
    override val label: String,
    override val description: String,
) : Source {
    object ZeroBackup : KnownSource("zero_backup", "Zero Backup", "Restore from a Zero backup file")
    object ZenMoney   : KnownSource("zenmoney",    "ZenMoney",    "Import from a ZenMoney CSV export")
}
```

```kotlin
// zero-core — imports package
class ZeroBackupParser(
    private val syncEngine: SyncEngine,
) : SnapshotParser {
    override val source: Source = KnownSource.ZeroBackup
    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot = syncEngine.loadSnapshot(uri)
}
```

```kotlin
// zero-core — imports package
class ZenMoneySnapshotParser(
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    logger: Logger,
) : SnapshotParser {
    override val source: Source = KnownSource.ZenMoney

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot {
        // Same CSV parsing as ZenMoneyImportSourceUseCase today,
        // but output mapped to SyncCategory / SyncAccount / SyncTransaction
        ...
    }
}
```

The placeholder `userId` in the ZenMoney snapshot is irrelevant — `delta(snapshot, currentUserId)` uses `currentUserId` when querying existing DB state.

`zero-core` already depends on `zero-api` (which has `SyncEngine`, `SnapshotParser`, `Source`) so no new module dependency is needed.

---

## App Wiring Changes

`ApplicationComponent` changes:
- Remove old `ImportModule` wiring and `zero-zenmoney` from `settings.gradle`
- Remove `ZenMoneyImportComponent.Dependencies` implementation
- Add `ZeroBackupParser` and `ZenMoneySnapshotParser` provision (from `zero-core`)
- Provide `List<SnapshotParser>` to `ImportComponent.Builder`
- Add `SyncEngine` + `CurrentUserRepository` to `SettingsComponent.Dependencies`

`ImportComponent.Builder` gains:
```kotlin
@BindsInstance
fun parsers(parsers: List<SnapshotParser>): Builder
```

The use case's `SourceSelection` state is populated from `parsers.map { it.source }`. `SelectSource` resolves the parser by `parsers.first { it.source.key == action.source.key }`.

---

## Module Boundaries

| What | Where |
|------|-------|
| `SnapshotParser` interface | `zero-api` |
| `SyncEngine.delta()` | `zero-api` (interface) + `zero-sync` (impl) |
| `ImportUseCase`, display models, all components | `zero-core` |
| `CategoryCard` | `zero-ui` |
| `ZeroBackupParser`, `ZenMoneySnapshotParser`, `KnownSource` | `zero-core` (`imports` package) |
| Export logic in `SettingsViewModel` | `zero-core` |
