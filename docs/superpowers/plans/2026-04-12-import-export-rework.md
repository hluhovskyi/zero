# Import / Export Rework — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder import/export flow with a fully-designed, SyncEngine-powered implementation. Export saves a JSON backup. Import is a multi-step read-only review flow that commits on explicit confirmation.

**Architecture:** `SyncEngine.delta()` previews changes without writing; `SyncEngine.import()` commits them. `SnapshotParser` abstracts file-to-snapshot conversion. `ImportUseCase` is a state machine: SourceSelection → FilePicker → CategoriesReview → AccountsReview → TransactionsPreview. All review screens are read-only.

**Tech Stack:** Kotlin, Dagger 2, Jetpack Compose, kotlinx.coroutines, kotlinx.datetime, MediaStore API

---

## File Map

**Delete entirely:**
- `zero-api/…/imports/ImportAccount.kt`, `ImportCategory.kt`, `ImportTransaction.kt`, `ImportSourceUseCase.kt`
- `zero-core/…/imports/ImportUseCase.kt`, `DefaultImportUseCase.kt`, `ImportViewModel.kt`, `DefaultImportViewModel.kt`, `ImportComponent.kt`, `ImportViewProvider.kt`, `OnImportFinishedHandler.kt`
- `zero-core/…/imports/filepicker/*`, `accounts/*`, `categories/*`, `transactions/*`
- Entire `:zero-zenmoney` module

**Create in zero-api:**
- `imports/Source.kt` — `interface Source { val key, label, description }`
- `imports/SnapshotParser.kt` — `interface SnapshotParser { val source; suspend fun parse(uri): SyncSnapshot }`

**Modify in zero-api:**
- `sync/SyncEngine.kt` — add `loadSnapshot(uri: Uri.NonEmpty)` and `delta(snapshot, userId)`

**Create/modify in zero-sync:**
- `sync/DefaultSyncEngine.kt` — implement `loadSnapshot` (reads file via `ResourceResolver`, deserializes via `SyncSerializer`) and `delta`
- `sync/SyncComponent.kt` — `Dependencies` gains `resourceResolver: ResourceResolver` and `serializer: SyncSerializer`; `SyncSerializer` is provided as a scoped binding
- `sync/SyncEngineTest.kt` — add `delta()` tests

**Create/modify in zero-ui:**
- `ui/UiColorScheme.kt` — add `companion object { fun default() }`
- `ui/CategoryCard.kt` — new composable

**Create in zero-core/imports:**
- `KnownSource.kt`, `ImportDisplayModels.kt` (display data classes)
- `ImportUseCase.kt` (new interface), `DefaultImportUseCase.kt`, `OnImportFinishedHandler.kt`
- `ZeroBackupParser.kt`, `ZenMoneySnapshotParser.kt`
- `ImportComponent.kt`, `ImportViewProvider.kt`
- `sourceselection/` — Component, ViewModel, DefaultViewModel, ViewProvider
- `categoriesreview/` — Component, ViewModel, DefaultViewModel, ViewProvider
- `accountsreview/` — Component, ViewModel, DefaultViewModel, ViewProvider
- `transactionspreview/` — Component, ViewModel, DefaultViewModel, ViewProvider

**Modify in zero-core/settings:**
- `ExportWriter.kt` (new fun interface), `SettingsViewModel.kt`, `DefaultSettingsViewModel.kt`, `SettingsComponent.kt`, `SettingsViewProvider.kt`

**Modify in app:**
- `ActivityComponent.kt` — update Dependencies + Module
- `ApplicationComponent.kt` — remove `ImportModule`/`ZenMoneyImportComponent`, add `ExportWriter`
- `settings.gradle` — remove `:zero-zenmoney`

---

## Task 1: Delete old import infrastructure

**Files:** All old import files listed above

- [ ] **Step 1: Delete zero-api imports**

```bash
rm zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportAccount.kt
rm zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportCategory.kt
rm zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportTransaction.kt
rm zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportSourceUseCase.kt
```

- [ ] **Step 2: Delete zero-core imports**

```bash
rm -r zero-core/src/main/java/com/hluhovskyi/zero/imports/
```

- [ ] **Step 3: Remove zero-zenmoney from settings.gradle**

File: `settings.gradle`

Remove the line:
```groovy
include ':zero-zenmoney'
```

- [ ] **Step 4: Stub ApplicationComponent to unblock compilation**

Open `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt` and remove all references to `ImportModule`, `ZenMoneyImportComponent`, and `ImportSourceUseCase`. Remove the `ImportModule` object at the bottom. Remove the import lines for those classes. Remove `ZenMoneyImportComponent.Dependencies` from the class implements list.

The `@dagger.Module` annotation on `Module` should change from:
```kotlin
@dagger.Module(
    includes = [
        DatabaseModule::class,
        ImportModule::class,
    ],
)
```
to:
```kotlin
@dagger.Module(
    includes = [
        DatabaseModule::class,
    ],
)
```

- [ ] **Step 5: Stub ActivityComponent to unblock compilation**

Open `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`.

Remove from `interface Dependencies`:
```kotlin
val importSourceUseCase: ImportSourceUseCase
```

Remove from `object Module`:
```kotlin
@Provides
@ActivityScope
fun importComponentBuilder(
    component: ActivityComponent,
    importSourceUseCase: ImportSourceUseCase,
): ImportComponent.Builder = ImportComponent.builder(component)
    .importSourceUseCase(importSourceUseCase)
```

Also remove `ImportComponent.Dependencies` from the `abstract class ActivityComponent :` implements list, and remove `import com.hluhovskyi.zero.imports.ImportSourceUseCase`.

- [ ] **Step 6: Stub MainActivityScreenComponent**

Open `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`.

Comment out the `importNavigationEntry` function temporarily (it references `ImportComponent` which doesn't exist yet). Add `// TODO: re-wire import` as placeholder.

- [ ] **Step 7: Verify the project compiles**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (or errors only related to missing `ImportComponent` references)

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: delete old import infrastructure and zero-zenmoney module"
```

---

## Task 2: Add Source and SnapshotParser to zero-api

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/imports/Source.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/imports/SnapshotParser.kt`

- [ ] **Step 1: Create Source.kt**

```kotlin
// zero-api/src/main/java/com/hluhovskyi/zero/imports/Source.kt
package com.hluhovskyi.zero.imports

interface Source {
    val key: String
    val label: String
    val description: String
}
```

- [ ] **Step 2: Create SnapshotParser.kt**

```kotlin
// zero-api/src/main/java/com/hluhovskyi/zero/imports/SnapshotParser.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncSnapshot

interface SnapshotParser {
    val source: Source
    suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :zero-api:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/imports/
git commit -m "feat: add Source and SnapshotParser interfaces to zero-api"
```

---

## Task 3: Extend SyncEngine with delta() and loadSnapshot()

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncEngine.kt`
- Modify: `zero-sync/src/main/java/com/hluhovskyi/zero/sync/DefaultSyncEngine.kt`
- Modify: `zero-sync/src/test/java/com/hluhovskyi/zero/sync/SyncEngineTest.kt`

- [ ] **Step 1: Write failing tests for delta()**

Add these tests to `SyncEngineTest.kt` after the existing `import` tests (before `// Helpers`):

```kotlin
// --- Delta ---

@Test
fun `delta returns empty snapshot when nothing is new`() = runTest {
    val cat = syncCategory("cat-1", "2024-01-02T00:00:00")
    val snapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
        categories = listOf(cat),
        accounts = emptyList(),
        transactions = emptyList(),
    )
    val engine = engineWith(categories = listOf(cat))
    val delta = engine.delta(snapshot, userId = Id.Known("user-1"))

    assertTrue("No new categories expected", delta.categories.isEmpty())
}

@Test
fun `delta returns only entities newer than stored`() = runTest {
    val localCat = syncCategory("cat-1", "2024-01-03T00:00:00")
    val incomingCat = syncCategory("cat-1", "2024-01-01T00:00:00")
    val newCat = syncCategory("cat-2", "2024-01-01T00:00:00")
    val snapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
        categories = listOf(incomingCat, newCat),
        accounts = emptyList(),
        transactions = emptyList(),
    )
    val engine = engineWith(categories = listOf(localCat))
    val delta = engine.delta(snapshot, userId = Id.Known("user-1"))

    assertEquals(listOf(newCat), delta.categories)
}

@Test
fun `delta returns incoming when it wins LWW`() = runTest {
    val localCat = syncCategory("cat-1", "2024-01-01T00:00:00")
    val incomingCat = syncCategory("cat-1", "2024-01-05T00:00:00")
    val snapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
        categories = listOf(incomingCat),
        accounts = emptyList(),
        transactions = emptyList(),
    )
    val engine = engineWith(categories = listOf(localCat))
    val delta = engine.delta(snapshot, userId = Id.Known("user-1"))

    assertEquals(listOf(incomingCat), delta.categories)
}

@Test
fun `delta preserves snapshot metadata`() = runTest {
    val snapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("original-user"),
        exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
    )
    val engine = engineWith()
    val delta = engine.delta(snapshot, userId = Id.Known("current-user"))

    assertEquals(snapshot.version, delta.version)
    assertEquals(snapshot.userId, delta.userId)
    assertEquals(snapshot.exportedAt, delta.exportedAt)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :zero-sync:test --tests "*.SyncEngineTest.delta*"
```

Expected: FAIL — `delta` not defined on `SyncEngine`

- [ ] **Step 3: Update SyncEngine interface**

Replace `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncEngine.kt`:

```kotlin
package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri

interface SyncEngine {
    suspend fun export(userId: Id.Known): SyncSnapshot
    suspend fun import(snapshot: SyncSnapshot, userId: Id.Known)
    suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot
    suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot
}
```

- [ ] **Step 4: Implement delta() and loadSnapshot() in DefaultSyncEngine**

Replace `zero-sync/src/main/java/com/hluhovskyi/zero/sync/DefaultSyncEngine.kt`:

```kotlin
package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val SNAPSHOT_VERSION = 1

internal class DefaultSyncEngine(
    private val categoryPipeline: SyncPipeline<SyncCategory>,
    private val accountPipeline: SyncPipeline<SyncAccount>,
    private val transactionPipeline: SyncPipeline<SyncTransaction>,
    private val resourceResolver: ResourceResolver,
    private val serializer: SyncSerializer,
) : SyncEngine {

    override suspend fun export(userId: Id.Known): SyncSnapshot = SyncSnapshot(
        version = SNAPSHOT_VERSION,
        userId = userId,
        exportedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        categories = categoryPipeline.source.exportAll(userId),
        accounts = accountPipeline.source.exportAll(userId),
        transactions = transactionPipeline.source.exportAll(userId),
    )

    override suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot {
        val result = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull() ?: error("Could not read file: $uri")
        val json = result.result.inputStream.bufferedReader().use { it.readText() }
        return serializer.deserialize(json)
    }

    override suspend fun import(snapshot: SyncSnapshot, userId: Id.Known) {
        mergePipeline(categoryPipeline, snapshot.categories, userId)
        mergePipeline(accountPipeline, snapshot.accounts, userId)
        mergePipeline(transactionPipeline, snapshot.transactions, userId)
    }

    override suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot {
        return snapshot.copy(
            categories = computeDelta(categoryPipeline, snapshot.categories, userId),
            accounts = computeDelta(accountPipeline, snapshot.accounts, userId),
            transactions = computeDelta(transactionPipeline, snapshot.transactions, userId),
        )
    }

    private suspend fun <T : SyncEntity> mergePipeline(
        pipeline: SyncPipeline<T>,
        incoming: List<T>,
        userId: Id.Known,
    ) {
        val stored = pipeline.source.exportAll(userId).associateBy { it.id }
        val toUpsert = incoming.flatMap { entity ->
            val storedEntity = stored[entity.id]
            pipeline.resolver.resolve(storedEntity, entity)
                .filter { winner -> winner != storedEntity }
        }
        if (toUpsert.isNotEmpty()) pipeline.sink.syncUpsert(toUpsert)
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
}
```

- [ ] **Step 4b: Update SyncComponent to provide ResourceResolver and SyncSerializer as bindings**

Replace `zero-sync/src/main/java/com/hluhovskyi/zero/sync/SyncComponent.kt`:

```kotlin
package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.resource.ResourceResolver

interface SyncComponent {

    interface Dependencies {
        val categorySyncSource: EntitySyncSource<SyncCategory>
        val categorySyncSink: EntitySyncSink<SyncCategory>
        val accountSyncSource: EntitySyncSource<SyncAccount>
        val accountSyncSink: EntitySyncSink<SyncAccount>
        val transactionSyncSource: EntitySyncSource<SyncTransaction>
        val transactionSyncSink: EntitySyncSink<SyncTransaction>
        val resourceResolver: ResourceResolver
    }

    val syncEngine: SyncEngine
    val serializer: SyncSerializer

    class Factory(private val dependencies: Dependencies) {
        fun create(): SyncComponent = DefaultSyncComponent(dependencies)
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}

internal class DefaultSyncComponent(dependencies: SyncComponent.Dependencies) : SyncComponent {

    override val serializer: SyncSerializer = SyncSerializer()

    override val syncEngine: SyncEngine by lazy {
        DefaultSyncEngine(
            categoryPipeline = SyncPipeline(
                source = dependencies.categorySyncSource,
                sink = dependencies.categorySyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            accountPipeline = SyncPipeline(
                source = dependencies.accountSyncSource,
                sink = dependencies.accountSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            transactionPipeline = SyncPipeline(
                source = dependencies.transactionSyncSource,
                sink = dependencies.transactionSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            resourceResolver = dependencies.resourceResolver,
            serializer = serializer,
        )
    }
}
```

- [ ] **Step 5: Update the `engineWith` helper in SyncEngineTest to pass the new constructor params**

The `engineWith` helper currently constructs `DefaultSyncEngine` directly. Add `resourceResolver` and `serializer` parameters:

```kotlin
private fun engineWith(
    categories: List<SyncCategory> = emptyList(),
    accounts: List<SyncAccount> = emptyList(),
    transactions: List<SyncTransaction> = emptyList(),
    categorySink: FakeCategorySink = FakeCategorySink(),
): SyncEngine = DefaultSyncEngine(
    categoryPipeline = SyncPipeline(
        source = FakeCategorySource(categories),
        sink = categorySink,
        resolver = LastWriteWinsResolver(),
    ),
    accountPipeline = SyncPipeline(
        source = FakeAccountSource(accounts),
        sink = FakeAccountSink(),
        resolver = LastWriteWinsResolver(),
    ),
    transactionPipeline = SyncPipeline(
        source = FakeTransactionSource(transactions),
        sink = FakeTransactionSink(),
        resolver = LastWriteWinsResolver(),
    ),
    resourceResolver = ResourceResolver.Noop,
    serializer = SyncSerializer(),
)
```

- [ ] **Step 6: Run all sync tests**

```bash
./gradlew :zero-sync:test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncEngine.kt
git add zero-sync/src/main/java/com/hluhovskyi/zero/sync/DefaultSyncEngine.kt
git add zero-sync/src/main/java/com/hluhovskyi/zero/sync/SyncComponent.kt
git add zero-sync/src/test/java/com/hluhovskyi/zero/sync/SyncEngineTest.kt
git commit -m "feat: add delta() and loadSnapshot(uri) to SyncEngine; SyncSerializer as component binding"
```

---

## Task 4: UiColorScheme.default() and CategoryCard in zero-ui

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/UiColorScheme.kt`
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryCard.kt`

- [ ] **Step 1: Add default() to UiColorScheme**

Replace `zero-ui/src/main/java/com/hluhovskyi/zero/ui/UiColorScheme.kt`:

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.ui.graphics.Color

data class UiColorScheme(
    val primary: Color,
    val background: Color,
) {
    companion object {
        fun default(): UiColorScheme = UiColorScheme(
            primary = Color(0xFF8E8E93),
            background = Color(0xFFE5E5EA),
        )
    }
}
```

- [ ] **Step 2: Create CategoryCard.kt**

```kotlin
// zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryCard.kt
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            color = Color(0xFF1B1B1F),
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **Step 3: Fix the Modifier.weight import — it's a RowScope extension**

The `Modifier.weight(1f)` in a `Row` is a `RowScope` extension. The correct usage inside a `Row` composable is fine as-is since `Row`'s content lambda is `@Composable RowScope.() -> Unit`. No change needed.

- [ ] **Step 4: Build to verify**

```bash
./gradlew :zero-ui:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/UiColorScheme.kt
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryCard.kt
git commit -m "feat: add CategoryCard composable and UiColorScheme.default() to zero-ui"
```

---

## Task 5: New ImportUseCase interface, display models, KnownSource, OnImportFinishedHandler

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/KnownSource.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/OnImportFinishedHandler.kt`

- [ ] **Step 1: Create KnownSource.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/KnownSource.kt
package com.hluhovskyi.zero.imports

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

- [ ] **Step 2: Create ImportDisplayModels.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

data class ImportCategory(
    val id: Id.Known,
    val name: String,
    val iconId: Id?,
    val colorId: Id?,
)

data class ImportAccount(
    val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
    val transactionCount: Int,
)

sealed interface ImportTransaction {
    val id: Id.Known
    val accountId: Id.Known
    val currencyId: Id.Known
    val amount: Amount
    val dateTime: LocalDateTime

    data class Expense(
        override val id: Id.Known,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val amount: Amount,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known?,
        val categoryName: String?,
    ) : ImportTransaction

    data class Income(
        override val id: Id.Known,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val amount: Amount,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known?,
        val categoryName: String?,
    ) : ImportTransaction

    data class Transfer(
        override val id: Id.Known,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val amount: Amount,
        override val dateTime: LocalDateTime,
        val targetAccountId: Id.Known,
        val targetAmount: Amount,
        val targetCurrencyId: Id.Known,
    ) : ImportTransaction
}
```

- [ ] **Step 3: Create new ImportUseCase.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface ImportUseCase : AttachableActionStateModel<ImportUseCase.Action, ImportUseCase.State> {

    sealed interface Action {
        data class SelectSource(val source: Source) : Action
        data class SelectFile(val uri: Uri.NonEmpty) : Action
        object ConfirmCategories : Action
        object ConfirmAccounts : Action
        object Confirm : Action
        object Back : Action
    }

    sealed interface State {
        data class SourceSelection(val sources: List<Source>) : State
        object FilePicker : State
        object Loading : State
        data class CategoriesReview(val categories: List<ImportCategory>) : State
        data class AccountsReview(val accounts: List<ImportAccount>) : State
        data class TransactionsPreview(
            val transactions: List<ImportTransaction>,
            val totalCount: Int,
        ) : State
    }

    object Noop : ImportUseCase {
        override fun perform(action: Action) = Unit
        override val state: Flow<State> = emptyFlow()
        override fun attach(): Closeable = Closeables.empty()
    }
}
```

- [ ] **Step 4: Create OnImportFinishedHandler.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/OnImportFinishedHandler.kt
package com.hluhovskyi.zero.imports

fun interface OnImportFinishedHandler {

    fun onFinished()

    object Noop : OnImportFinishedHandler {
        override fun onFinished() = Unit
    }
}
```

- [ ] **Step 5: Build zero-core to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/
git commit -m "feat: add ImportUseCase, display models, KnownSource, OnImportFinishedHandler"
```

---

## Task 6: ZeroBackupParser

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ZeroBackupParser.kt`

- [ ] **Step 1: Create ZeroBackupParser.kt**

`SyncEngine.loadSnapshot(uri)` already handles file reading and deserialization. The parser is a one-liner:

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ZeroBackupParser.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot

class ZeroBackupParser(
    private val syncEngine: SyncEngine,
) : SnapshotParser {

    override val source: Source = KnownSource.ZeroBackup

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot = syncEngine.loadSnapshot(uri)
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ZeroBackupParser.kt
git commit -m "feat: add ZeroBackupParser"
```

---

## Task 7: ZenMoneySnapshotParser

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ZenMoneySnapshotParser.kt`

This adapts `ZenMoneyImportSourceUseCase` (deleted) to produce `SyncSnapshot` instead of `ImportSourceUseCase.Result`.

- [ ] **Step 1: Create ZenMoneySnapshotParser.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ZenMoneySnapshotParser.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import com.hluhovskyi.zero.sync.SyncAccount
import com.hluhovskyi.zero.sync.SyncCategory
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.sync.SyncTransaction
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "ZenMoneySnapshotParser"
private val DATE_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val PLACEHOLDER_USER_ID = Id.Known("zenmoney-import")

class ZenMoneySnapshotParser(
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    logger: Logger,
) : SnapshotParser {

    private val logger = logger.withTag(TAG)
    override val source: Source = KnownSource.ZenMoney

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot {
        val resolveResult = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull() ?: return emptySnapshot()

        val reader = resolveResult.result.inputStream.bufferedReader()
        val rawData = try {
            val header = reader.readLine()
            if (header == null) {
                emptyList()
            } else {
                val indices = header.removePrefix("\uFEFF").parseIndices()
                reader.lineSequence()
                    .mapNotNull { line -> line.parseRawData(indices) }
                    .toList()
            }
        } finally {
            reader.close()
        }

        val now = clock.now().toLocalDateTime(TimeZone.UTC)
        val idToAccount = LinkedHashMap<String, SyncAccount>()
        val idToCategory = LinkedHashMap<String, SyncCategory>()
        val transactions = ArrayList<SyncTransaction>()

        fun getOrCreateAccount(name: String, currencyCode: String): SyncAccount {
            return idToAccount.getOrPut(name) {
                val id = idGenerator()
                SyncAccount(
                    id = id,
                    currencyId = Id.Known(currencyCode),
                    name = name,
                    iconId = Id.Known("account-default"),
                    initialBalance = "0",
                    category = "OTHER",
                    details = null,
                    creationDateTime = now,
                    updatedDateTime = now,
                    deletedAt = null,
                )
            }
        }

        fun getOrCreateCategory(name: String): SyncCategory {
            return idToCategory.getOrPut(name) {
                SyncCategory(
                    id = idGenerator(),
                    name = name,
                    iconId = null,
                    colorId = null,
                    parentCategoryId = null,
                    creationDateTime = now,
                    updatedDateTime = now,
                    deletedAt = null,
                )
            }
        }

        rawData.forEach { data ->
            val outcomeAccount = data.outcomeAccountName?.takeIf { it.isNotBlank() }
                ?.let { name -> data.outcomeCurrencyShortTitle?.let { getOrCreateAccount(name, it) } }
            val incomeAccount = data.incomeAccountName?.takeIf { it.isNotBlank() }
                ?.let { name -> data.incomeCurrencyShortTitle?.let { getOrCreateAccount(name, it) } }

            if (outcomeAccount == null && incomeAccount == null) {
                logger.d("parse: both accounts null, data=$data")
                return@forEach
            }

            val outcomeAmountValue = data.outcome?.toDoubleOrNull()
            val incomeAmountValue = data.income?.toDoubleOrNull()

            if (outcomeAmountValue == null && incomeAmountValue == null) {
                logger.d("parse: both amounts null, data=$data")
                return@forEach
            }

            val txDate: LocalDateTime = data.date?.let {
                runCatching {
                    JavaLocalDate.parse(it, DATE_PARSER).atStartOfDay().toKotlinLocalDateTime()
                }.getOrNull()
            } ?: run {
                logger.d("parse: invalid date, data=$data")
                return@forEach
            }

            val txId = idGenerator()

            if (incomeAccount != null && outcomeAccount != null && incomeAccount.id != outcomeAccount.id
                && incomeAmountValue != null && outcomeAmountValue != null) {
                transactions += SyncTransaction(
                    id = txId,
                    type = SyncTransaction.Type.TRANSFER,
                    accountId = outcomeAccount.id,
                    currencyId = outcomeAccount.currencyId,
                    categoryId = null,
                    amount = Amount(outcomeAmountValue).value,
                    rate = "1.0",
                    targetAccountId = incomeAccount.id.value,
                    targetAmount = Amount(incomeAmountValue).value,
                    enteredDateTime = txDate,
                    creationDateTime = txDate,
                    updatedDateTime = txDate,
                    deletedAt = null,
                )
                return@forEach
            }

            val category = data.categoryName?.takeIf { it.isNotBlank() }
                ?.let { getOrCreateCategory(it) }
            if (category == null) {
                logger.d("parse: no category, data=$data")
                return@forEach
            }

            if (outcomeAccount != null && outcomeAmountValue != null && outcomeAmountValue > 0) {
                transactions += SyncTransaction(
                    id = txId,
                    type = SyncTransaction.Type.EXPENSE,
                    accountId = outcomeAccount.id,
                    currencyId = outcomeAccount.currencyId,
                    categoryId = category.id.value,
                    amount = Amount(outcomeAmountValue).value,
                    rate = "1.0",
                    targetAccountId = null,
                    targetAmount = null,
                    enteredDateTime = txDate,
                    creationDateTime = txDate,
                    updatedDateTime = txDate,
                    deletedAt = null,
                )
                return@forEach
            }

            if (incomeAccount != null && incomeAmountValue != null && incomeAmountValue > 0) {
                transactions += SyncTransaction(
                    id = txId,
                    type = SyncTransaction.Type.INCOME,
                    accountId = incomeAccount.id,
                    currencyId = incomeAccount.currencyId,
                    categoryId = category.id.value,
                    amount = Amount(incomeAmountValue).value,
                    rate = "1.0",
                    targetAccountId = null,
                    targetAmount = null,
                    enteredDateTime = txDate,
                    creationDateTime = txDate,
                    updatedDateTime = txDate,
                    deletedAt = null,
                )
            }
        }

        return SyncSnapshot(
            version = 1,
            userId = PLACEHOLDER_USER_ID,
            exportedAt = now,
            categories = idToCategory.values.sortedBy { it.name },
            accounts = idToAccount.values.sortedBy { it.name },
            transactions = transactions,
        )
    }

    private fun emptySnapshot(): SyncSnapshot {
        val now = clock.now().toLocalDateTime(TimeZone.UTC)
        return SyncSnapshot(
            version = 1,
            userId = PLACEHOLDER_USER_ID,
            exportedAt = now,
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
        )
    }

    private fun String.parseRawData(indices: Indices): RawData {
        val cells = split(';')
        return RawData(
            categoryName = cells.getOrNull(indices.categoryName)?.withoutBrackets(),
            outcomeAccountName = cells.getOrNull(indices.outcomeAccountName)?.withoutBrackets(),
            outcome = cells.getOrNull(indices.outcome)?.withoutBrackets(),
            outcomeCurrencyShortTitle = cells.getOrNull(indices.outcomeCurrencyShortTitle),
            incomeAccountName = cells.getOrNull(indices.incomeAccountName)?.withoutBrackets(),
            income = cells.getOrNull(indices.income)?.withoutBrackets(),
            incomeCurrencyShortTitle = cells.getOrNull(indices.incomeCurrencyShortTitle),
            date = cells.getOrNull(indices.date),
        )
    }

    private data class RawData(
        val categoryName: String?,
        val outcomeAccountName: String?,
        val outcome: String?,
        val outcomeCurrencyShortTitle: String?,
        val incomeAccountName: String?,
        val income: String?,
        val incomeCurrencyShortTitle: String?,
        val date: String?,
    )

    private fun String.parseIndices(): Indices {
        val cells = split(';')
        return Indices(
            date = cells.indexOf("date"),
            categoryName = cells.indexOf("categoryName"),
            outcomeAccountName = cells.indexOf("outcomeAccountName"),
            outcome = cells.indexOf("outcome"),
            outcomeCurrencyShortTitle = cells.indexOf("outcomeCurrencyShortTitle"),
            incomeAccountName = cells.indexOf("incomeAccountName"),
            income = cells.indexOf("income"),
            incomeCurrencyShortTitle = cells.indexOf("incomeCurrencyShortTitle"),
        )
    }

    private data class Indices(
        val date: Int,
        val categoryName: Int,
        val outcomeAccountName: Int,
        val outcome: Int,
        val outcomeCurrencyShortTitle: Int,
        val incomeAccountName: Int,
        val income: Int,
        val incomeCurrencyShortTitle: Int,
    )

    private fun String.withoutBrackets(): String {
        if (!startsWith('"') || !endsWith('"')) return this
        return substring(1, length - 1)
    }
}
```

Note: `Amount(Double).value` — check the `Amount` class to confirm it exposes `.value: String`. If `Amount` is a value class wrapping a string representation, use `amount.toString()` or `"$outcomeAmountValue"` directly. Adjust as needed based on the actual `Amount` class.

- [ ] **Step 2: Check the Amount class to verify the constructor and value representation**

```bash
grep -r "class Amount\|data class Amount\|value class Amount" zero-api/src/main/java/
```

Adjust the `Amount(outcomeAmountValue).value` calls in `ZenMoneySnapshotParser` to match the actual `Amount` API. If `Amount` wraps a `Double`, use `Amount(outcomeAmountValue).value`. If `SyncTransaction.amount` is a raw `String`, use `outcomeAmountValue.toString()` directly without `Amount`.

- [ ] **Step 3: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Fix any compilation errors from the Amount API difference.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ZenMoneySnapshotParser.kt
git commit -m "feat: add ZenMoneySnapshotParser (adapted from deleted ZenMoneyImportSourceUseCase)"
```

---

## Task 8: DefaultImportUseCase state machine

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

- [ ] **Step 1: Create DefaultImportUseCase.kt**

All transitional data (`selectedSource`, `storedDelta`) lives inside an `InternalState` data class so every mutation goes through `mutableState.update {}`. No mutable fields outside of `mutableState`.

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.sync.SyncTransaction
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultImportUseCase(
    private val parsers: List<SnapshotParser>,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
    private val onImportFinishedHandler: OnImportFinishedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ImportUseCase {

    private data class InternalState(
        val selectedSource: Source? = null,
        val storedDelta: SyncSnapshot? = null,
        val screen: ImportUseCase.State,
    )

    private val mutableState = MutableStateFlow(
        InternalState(screen = ImportUseCase.State.SourceSelection(parsers.map { it.source }))
    )
    override val state: Flow<ImportUseCase.State> = mutableState.map { it.screen }

    override fun perform(action: ImportUseCase.Action) {
        when (action) {
            is ImportUseCase.Action.SelectSource -> mutableState.update { current ->
                current.copy(
                    selectedSource = action.source,
                    screen = ImportUseCase.State.FilePicker,
                )
            }
            is ImportUseCase.Action.SelectFile -> coroutineScope.launch {
                mutableState.update { it.copy(screen = ImportUseCase.State.Loading) }
                val source = mutableState.value.selectedSource ?: return@launch
                val parser = parsers.first { it.source.key == source.key }
                val userId = currentUserRepository.query().first().id
                val snapshot = parser.parse(action.uri)
                val delta = syncEngine.delta(snapshot, userId)
                mutableState.update { current ->
                    current.copy(
                        storedDelta = delta,
                        screen = ImportUseCase.State.CategoriesReview(
                            categories = delta.categories.map { syncCategory ->
                                ImportCategory(
                                    id = syncCategory.id,
                                    name = syncCategory.name,
                                    iconId = syncCategory.iconId?.let { Id.Known(it) },
                                    colorId = syncCategory.colorId?.let { Id.Known(it) },
                                )
                            },
                        ),
                    )
                }
            }
            is ImportUseCase.Action.ConfirmCategories -> mutableState.update { current ->
                val delta = current.storedDelta ?: return@update current
                val txByAccountId = delta.transactions.groupBy { it.accountId }
                current.copy(
                    screen = ImportUseCase.State.AccountsReview(
                        accounts = delta.accounts.map { syncAccount ->
                            ImportAccount(
                                id = syncAccount.id,
                                name = syncAccount.name,
                                currencyId = syncAccount.currencyId,
                                transactionCount = txByAccountId[syncAccount.id]?.size ?: 0,
                            )
                        },
                    ),
                )
            }
            is ImportUseCase.Action.ConfirmAccounts -> mutableState.update { current ->
                val delta = current.storedDelta ?: return@update current
                val categoryById = delta.categories.associateBy { it.id }
                val transactions = delta.transactions.map { syncTx ->
                    val categoryName = syncTx.categoryId?.let { categoryById[Id.Known(it)]?.name }
                    when (syncTx.type) {
                        SyncTransaction.Type.EXPENSE -> ImportTransaction.Expense(
                            id = syncTx.id,
                            accountId = syncTx.accountId,
                            currencyId = syncTx.currencyId,
                            amount = Amount(syncTx.amount.toDouble()),
                            dateTime = syncTx.enteredDateTime,
                            categoryId = syncTx.categoryId?.let { Id.Known(it) },
                            categoryName = categoryName,
                        )
                        SyncTransaction.Type.INCOME -> ImportTransaction.Income(
                            id = syncTx.id,
                            accountId = syncTx.accountId,
                            currencyId = syncTx.currencyId,
                            amount = Amount(syncTx.amount.toDouble()),
                            dateTime = syncTx.enteredDateTime,
                            categoryId = syncTx.categoryId?.let { Id.Known(it) },
                            categoryName = categoryName,
                        )
                        SyncTransaction.Type.TRANSFER -> ImportTransaction.Transfer(
                            id = syncTx.id,
                            accountId = syncTx.accountId,
                            currencyId = syncTx.currencyId,
                            amount = Amount(syncTx.amount.toDouble()),
                            dateTime = syncTx.enteredDateTime,
                            targetAccountId = Id.Known(syncTx.targetAccountId ?: syncTx.accountId.value),
                            targetAmount = Amount((syncTx.targetAmount ?: syncTx.amount).toDouble()),
                            targetCurrencyId = syncTx.currencyId,
                        )
                    }
                }
                current.copy(
                    screen = ImportUseCase.State.TransactionsPreview(
                        transactions = transactions,
                        totalCount = transactions.size,
                    ),
                )
            }
            is ImportUseCase.Action.Confirm -> {
                // Capture delta synchronously before launching — safe since Confirm is
                // only reachable from TransactionsPreview, where storedDelta is always set.
                val delta = mutableState.value.storedDelta ?: return
                coroutineScope.launch {
                    val userId = currentUserRepository.query().first().id
                    syncEngine.import(delta, userId)
                    coroutineScope.launch(Dispatchers.Main) {
                        onImportFinishedHandler.onFinished()
                    }
                }
            }
            is ImportUseCase.Action.Back -> mutableState.update { current ->
                when (current.screen) {
                    is ImportUseCase.State.FilePicker,
                    is ImportUseCase.State.SourceSelection,
                    is ImportUseCase.State.Loading,
                    is ImportUseCase.State.CategoriesReview -> InternalState(
                        screen = ImportUseCase.State.SourceSelection(parsers.map { it.source })
                    )
                    is ImportUseCase.State.AccountsReview -> {
                        val delta = current.storedDelta ?: return@update current
                        current.copy(
                            screen = ImportUseCase.State.CategoriesReview(
                                categories = delta.categories.map { syncCategory ->
                                    ImportCategory(
                                        id = syncCategory.id,
                                        name = syncCategory.name,
                                        iconId = syncCategory.iconId?.let { Id.Known(it) },
                                        colorId = syncCategory.colorId?.let { Id.Known(it) },
                                    )
                                },
                            ),
                        )
                    }
                    is ImportUseCase.State.TransactionsPreview -> {
                        val delta = current.storedDelta ?: return@update current
                        val txByAccountId = delta.transactions.groupBy { it.accountId }
                        current.copy(
                            screen = ImportUseCase.State.AccountsReview(
                                accounts = delta.accounts.map { syncAccount ->
                                    ImportAccount(
                                        id = syncAccount.id,
                                        name = syncAccount.name,
                                        currencyId = syncAccount.currencyId,
                                        transactionCount = txByAccountId[syncAccount.id]?.size ?: 0,
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.empty()
}
```

Note: `Amount(syncTx.amount.toDouble())` — adjust for the actual `Amount` constructor. If `Amount` takes a `String`, use `Amount(syncTx.amount)` instead. Check the actual `Amount` class.

- [ ] **Step 2: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Fix any Amount constructor issues by checking:
```bash
grep -r "class Amount\|data class Amount\|value class Amount" zero-api/src/main/java/
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
git commit -m "feat: implement DefaultImportUseCase state machine"
```

---

## Task 9: Source Selection screen

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionComponent.kt`

- [ ] **Step 1: Create SourceSelectionViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewModel.kt
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.Source

interface SourceSelectionViewModel : ActionStateModel<SourceSelectionViewModel.Action, SourceSelectionViewModel.State> {

    data class State(val sources: List<Source> = emptyList())

    sealed interface Action {
        data class SelectSource(val source: Source) : Action
        object Close : Action
    }
}
```

- [ ] **Step 2: Create DefaultSourceSelectionViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModel.kt
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.imports.ImportUseCase
import com.hluhovskyi.zero.imports.OnImportFinishedHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultSourceSelectionViewModel(
    private val importUseCase: ImportUseCase,
    private val onImportFinishedHandler: OnImportFinishedHandler,
) : SourceSelectionViewModel {

    override val state: Flow<SourceSelectionViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.SourceSelection>()
        .map { SourceSelectionViewModel.State(sources = it.sources) }

    override fun perform(action: SourceSelectionViewModel.Action) {
        when (action) {
            is SourceSelectionViewModel.Action.SelectSource ->
                importUseCase.perform(ImportUseCase.Action.SelectSource(action.source))
            is SourceSelectionViewModel.Action.Close ->
                onImportFinishedHandler.onFinished()
        }
    }
}
```

- [ ] **Step 3: Create SourceSelectionViewProvider.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt
package com.hluhovskyi.zero.imports.sourceselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.Source

internal class SourceSelectionViewProvider(
    private val viewModel: SourceSelectionViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        SourceSelectionView(viewModel = viewModel)
    }
}

@Composable
private fun SourceSelectionView(viewModel: SourceSelectionViewModel) {
    val state by viewModel.state.collectAsState(initial = SourceSelectionViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(SourceSelectionViewModel.Action.Close) }) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
        }
        Text(
            text = "Import Data",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "Choose how you'd like to import your financial data.",
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.sources) { source ->
                SourceRow(
                    source = source,
                    onClick = { viewModel.perform(SourceSelectionViewModel.Action.SelectSource(source)) },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = source.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(text = source.description, fontSize = 13.sp)
    }
}
```

- [ ] **Step 4: Create SourceSelectionComponent.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionComponent.kt
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import com.hluhovskyi.zero.imports.OnImportFinishedHandler
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class SourceSelectionScope

private const val TAG = "SourceSelectionComponent"

@SourceSelectionScope
@dagger.Component(
    modules = [SourceSelectionComponent.Module::class],
    dependencies = [SourceSelectionComponent.Dependencies::class],
)
internal abstract class SourceSelectionComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies

    companion object {
        fun builder(dependencies: Dependencies): Builder =
            DaggerSourceSelectionComponent.builder()
                .dependencies(dependencies)
                .importUseCase(ImportUseCase.Noop)
                .onImportFinishedHandler(OnImportFinishedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<SourceSelectionComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder

        @BindsInstance
        fun onImportFinishedHandler(handler: OnImportFinishedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @SourceSelectionScope
        fun viewModel(
            importUseCase: ImportUseCase,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): SourceSelectionViewModel = DefaultSourceSelectionViewModel(
            importUseCase = importUseCase,
            onImportFinishedHandler = onImportFinishedHandler,
        )

        @Provides
        @SourceSelectionScope
        fun viewProvider(viewModel: SourceSelectionViewModel): ViewProvider =
            SourceSelectionViewProvider(viewModel = viewModel)
    }
}
```

- [ ] **Step 5: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/
git commit -m "feat: add Source Selection screen"
```

---

## Task 10: Categories Review screen

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/DefaultCategoriesReviewViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewComponent.kt`

- [ ] **Step 1: Create CategoriesReviewViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewModel.kt
package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.ImportCategory

interface CategoriesReviewViewModel : ActionStateModel<CategoriesReviewViewModel.Action, CategoriesReviewViewModel.State> {

    data class State(val categories: List<ImportCategory> = emptyList())

    sealed interface Action {
        object Next : Action
        object Back : Action
    }
}
```

- [ ] **Step 2: Create DefaultCategoriesReviewViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/DefaultCategoriesReviewViewModel.kt
package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultCategoriesReviewViewModel(
    private val importUseCase: ImportUseCase,
) : CategoriesReviewViewModel {

    override val state: Flow<CategoriesReviewViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.CategoriesReview>()
        .map { CategoriesReviewViewModel.State(categories = it.categories) }

    override fun perform(action: CategoriesReviewViewModel.Action) {
        when (action) {
            is CategoriesReviewViewModel.Action.Next ->
                importUseCase.perform(ImportUseCase.Action.ConfirmCategories)
            is CategoriesReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
        }
    }
}
```

- [ ] **Step 3: Create CategoriesReviewViewProvider.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt
package com.hluhovskyi.zero.imports.categoriesreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryCard

internal class CategoriesReviewViewProvider(
    private val viewModel: CategoriesReviewViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoriesReviewView(viewModel = viewModel)
    }
}

@Composable
private fun CategoriesReviewView(viewModel: CategoriesReviewViewModel) {
    val state by viewModel.state.collectAsState(initial = CategoriesReviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Categories",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "MANAGEMENT",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.categories, key = { it.id.value }) { category ->
                CategoryCard(name = category.name)
            }
        }
        Button(
            onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Next) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(text = "Next →")
        }
    }
}
```

- [ ] **Step 4: Create CategoriesReviewComponent.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewComponent.kt
package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoriesReviewScope

private const val TAG = "CategoriesReviewComponent"

@CategoriesReviewScope
@dagger.Component(
    modules = [CategoriesReviewComponent.Module::class],
    dependencies = [CategoriesReviewComponent.Dependencies::class],
)
internal abstract class CategoriesReviewComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies

    companion object {
        fun builder(dependencies: Dependencies): Builder =
            DaggerCategoriesReviewComponent.builder()
                .dependencies(dependencies)
                .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoriesReviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoriesReviewScope
        fun viewModel(importUseCase: ImportUseCase): CategoriesReviewViewModel =
            DefaultCategoriesReviewViewModel(importUseCase = importUseCase)

        @Provides
        @CategoriesReviewScope
        fun viewProvider(viewModel: CategoriesReviewViewModel): ViewProvider =
            CategoriesReviewViewProvider(viewModel = viewModel)
    }
}
```

- [ ] **Step 5: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/
git commit -m "feat: add Categories Review screen"
```

---

## Task 11: Accounts Review screen

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/DefaultAccountsReviewViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewComponent.kt`

- [ ] **Step 1: Create AccountsReviewViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewModel.kt
package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.ImportAccount

interface AccountsReviewViewModel : ActionStateModel<AccountsReviewViewModel.Action, AccountsReviewViewModel.State> {

    data class State(val accounts: List<ImportAccount> = emptyList())

    sealed interface Action {
        object Next : Action
        object Back : Action
    }
}
```

- [ ] **Step 2: Create DefaultAccountsReviewViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/DefaultAccountsReviewViewModel.kt
package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultAccountsReviewViewModel(
    private val importUseCase: ImportUseCase,
) : AccountsReviewViewModel {

    override val state: Flow<AccountsReviewViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.AccountsReview>()
        .map { AccountsReviewViewModel.State(accounts = it.accounts) }

    override fun perform(action: AccountsReviewViewModel.Action) {
        when (action) {
            is AccountsReviewViewModel.Action.Next ->
                importUseCase.perform(ImportUseCase.Action.ConfirmAccounts)
            is AccountsReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
        }
    }
}
```

- [ ] **Step 3: Create AccountsReviewViewProvider.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt
package com.hluhovskyi.zero.imports.accountsreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportAccount

internal class AccountsReviewViewProvider(
    private val viewModel: AccountsReviewViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountsReviewView(viewModel = viewModel)
    }
}

@Composable
private fun AccountsReviewView(viewModel: AccountsReviewViewModel) {
    val state by viewModel.state.collectAsState(initial = AccountsReviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(AccountsReviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Review Accounts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "STEP 3 OF 4",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.accounts, key = { it.id.value }) { account ->
                AccountRow(account = account)
            }
        }
        Button(
            onClick = { viewModel.perform(AccountsReviewViewModel.Action.Next) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(text = "Next: Review Transactions →")
        }
    }
}

@Composable
private fun AccountRow(account: ImportAccount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${account.currencyId.value} • ${account.transactionCount} transactions",
                fontSize = 13.sp,
            )
        }
    }
}
```

- [ ] **Step 4: Create AccountsReviewComponent.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewComponent.kt
package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountsReviewScope

private const val TAG = "AccountsReviewComponent"

@AccountsReviewScope
@dagger.Component(
    modules = [AccountsReviewComponent.Module::class],
    dependencies = [AccountsReviewComponent.Dependencies::class],
)
internal abstract class AccountsReviewComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies

    companion object {
        fun builder(dependencies: Dependencies): Builder =
            DaggerAccountsReviewComponent.builder()
                .dependencies(dependencies)
                .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountsReviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountsReviewScope
        fun viewModel(importUseCase: ImportUseCase): AccountsReviewViewModel =
            DefaultAccountsReviewViewModel(importUseCase = importUseCase)

        @Provides
        @AccountsReviewScope
        fun viewProvider(viewModel: AccountsReviewViewModel): ViewProvider =
            AccountsReviewViewProvider(viewModel = viewModel)
    }
}
```

- [ ] **Step 5: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/
git commit -m "feat: add Accounts Review screen"
```

---

## Task 12: Transactions Preview screen

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/DefaultTransactionsPreviewViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewProvider.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewComponent.kt`

- [ ] **Step 1: Create TransactionsPreviewViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewModel.kt
package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.ImportTransaction

interface TransactionsPreviewViewModel : ActionStateModel<TransactionsPreviewViewModel.Action, TransactionsPreviewViewModel.State> {

    data class DisplayTransaction(
        val primaryText: String,
        val amount: String,
        val accountName: String,
        val date: String,
        val type: Type,
    ) {
        enum class Type { EXPENSE, INCOME, TRANSFER }
    }

    data class State(
        val transactions: List<DisplayTransaction> = emptyList(),
        val totalCount: Int = 0,
    )

    sealed interface Action {
        object Confirm : Action
        object Back : Action
    }
}
```

- [ ] **Step 2: Create DefaultTransactionsPreviewViewModel.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/DefaultTransactionsPreviewViewModel.kt
package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.imports.ImportTransaction
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionsPreviewViewModel(
    private val importUseCase: ImportUseCase,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
) : TransactionsPreviewViewModel {

    override val state: Flow<TransactionsPreviewViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.TransactionsPreview>()
        .map { previewState ->
            TransactionsPreviewViewModel.State(
                transactions = previewState.transactions.map { tx -> tx.toDisplay() },
                totalCount = previewState.totalCount,
            )
        }

    private fun ImportTransaction.toDisplay(): TransactionsPreviewViewModel.DisplayTransaction {
        return when (this) {
            is ImportTransaction.Expense -> TransactionsPreviewViewModel.DisplayTransaction(
                primaryText = categoryName ?: "Expense",
                amount = amountFormatter.format(amount),
                accountName = accountId.value,
                date = dateFormatter.formatDate(dateTime),
                type = TransactionsPreviewViewModel.DisplayTransaction.Type.EXPENSE,
            )
            is ImportTransaction.Income -> TransactionsPreviewViewModel.DisplayTransaction(
                primaryText = categoryName ?: "Income",
                amount = amountFormatter.format(amount),
                accountName = accountId.value,
                date = dateFormatter.formatDate(dateTime),
                type = TransactionsPreviewViewModel.DisplayTransaction.Type.INCOME,
            )
            is ImportTransaction.Transfer -> TransactionsPreviewViewModel.DisplayTransaction(
                primaryText = targetAccountId.value,
                amount = amountFormatter.format(amount),
                accountName = accountId.value,
                date = dateFormatter.formatDate(dateTime),
                type = TransactionsPreviewViewModel.DisplayTransaction.Type.TRANSFER,
            )
        }
    }

    override fun perform(action: TransactionsPreviewViewModel.Action) {
        when (action) {
            is TransactionsPreviewViewModel.Action.Confirm ->
                importUseCase.perform(ImportUseCase.Action.Confirm)
            is TransactionsPreviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
        }
    }
}
```

Note: `amountFormatter.format(amount)` and `dateFormatter.formatDate(dateTime)` — check the actual API signatures on `AmountFormatter` and `DateFormatter`. Look at how they're used elsewhere in zero-core (e.g., transaction screens) and use the same calling convention.

- [ ] **Step 3: Check AmountFormatter and DateFormatter API**

```bash
grep -r "amountFormatter\.\|dateFormatter\." zero-core/src/main/java/ | head -20
```

Adjust the `.format()` and `.formatDate()` calls in `DefaultTransactionsPreviewViewModel` to match the actual method signatures.

- [ ] **Step 4: Create TransactionsPreviewViewProvider.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewProvider.kt
package com.hluhovskyi.zero.imports.transactionspreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider

internal class TransactionsPreviewViewProvider(
    private val viewModel: TransactionsPreviewViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionsPreviewView(viewModel = viewModel)
    }
}

@Composable
private fun TransactionsPreviewView(viewModel: TransactionsPreviewViewModel) {
    val state by viewModel.state.collectAsState(initial = TransactionsPreviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(TransactionsPreviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Review & Finalize",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "STEP 4 OF 4",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.transactions, key = { it.primaryText + it.date + it.amount }) { tx ->
                TransactionRow(transaction = tx)
            }
        }
        Text(
            text = "${state.totalCount} Transactions ready to import",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Button(
            onClick = { viewModel.perform(TransactionsPreviewViewModel.Action.Confirm) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = "Confirm & Import")
        }
    }
}

@Composable
private fun TransactionRow(transaction: TransactionsPreviewViewModel.DisplayTransaction) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = transaction.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${transaction.amount} · ${transaction.accountName} · ${transaction.date}",
            fontSize = 12.sp,
        )
    }
}
```

- [ ] **Step 5: Create TransactionsPreviewComponent.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewComponent.kt
package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionsPreviewScope

private const val TAG = "TransactionsPreviewComponent"

@TransactionsPreviewScope
@dagger.Component(
    modules = [TransactionsPreviewComponent.Module::class],
    dependencies = [TransactionsPreviewComponent.Dependencies::class],
)
internal abstract class TransactionsPreviewComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder =
            DaggerTransactionsPreviewComponent.builder()
                .dependencies(dependencies)
                .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionsPreviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionsPreviewScope
        fun viewModel(
            importUseCase: ImportUseCase,
            amountFormatter: AmountFormatter,
            dateFormatter: DateFormatter,
        ): TransactionsPreviewViewModel = DefaultTransactionsPreviewViewModel(
            importUseCase = importUseCase,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
        )

        @Provides
        @TransactionsPreviewScope
        fun viewProvider(viewModel: TransactionsPreviewViewModel): ViewProvider =
            TransactionsPreviewViewProvider(viewModel = viewModel)
    }
}
```

- [ ] **Step 6: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/
git commit -m "feat: add Transactions Preview screen"
```

---

## Task 13: ImportComponent and ImportViewProvider (orchestrator)

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt`

- [ ] **Step 1: Create new ImportComponent.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.accountsreview.AccountsReviewComponent
import com.hluhovskyi.zero.imports.categoriesreview.CategoriesReviewComponent
import com.hluhovskyi.zero.imports.sourceselection.SourceSelectionComponent
import com.hluhovskyi.zero.imports.transactionspreview.TransactionsPreviewComponent
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ImportScope

private const val TAG = "ImportComponent"

@ImportScope
@dagger.Component(
    modules = [ImportComponent.Module::class],
    dependencies = [ImportComponent.Dependencies::class],
)
abstract class ImportComponent :
    AttachableViewComponent,
    SourceSelectionComponent.Dependencies,
    CategoriesReviewComponent.Dependencies,
    AccountsReviewComponent.Dependencies,
    TransactionsPreviewComponent.Dependencies {

    override val tag: String = TAG

    internal abstract val useCase: ImportUseCase
    override fun attach(): Closeable = useCase.attach()

    interface Dependencies {
        val syncEngine: SyncEngine
        val currentUserRepository: CurrentUserRepository
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerImportComponent.builder()
            .dependencies(dependencies)
            .parsers(emptyList())
            .onImportFinishedHandler(OnImportFinishedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun parsers(parsers: List<@JvmSuppressWildcards SnapshotParser>): Builder

        @BindsInstance
        fun onImportFinishedHandler(handler: OnImportFinishedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportScope
        fun useCase(
            parsers: List<@JvmSuppressWildcards SnapshotParser>,
            syncEngine: SyncEngine,
            currentUserRepository: CurrentUserRepository,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): ImportUseCase = DefaultImportUseCase(
            parsers = parsers,
            syncEngine = syncEngine,
            currentUserRepository = currentUserRepository,
            onImportFinishedHandler = onImportFinishedHandler,
        )

        @Provides
        @ImportScope
        internal fun sourceSelectionComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): SourceSelectionComponent.Builder = SourceSelectionComponent.builder(component)
            .importUseCase(importUseCase)
            .onImportFinishedHandler(onImportFinishedHandler)

        @Provides
        @ImportScope
        internal fun categoriesReviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): CategoriesReviewComponent.Builder = CategoriesReviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun accountsReviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): AccountsReviewComponent.Builder = AccountsReviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun transactionsPreviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): TransactionsPreviewComponent.Builder = TransactionsPreviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun viewProvider(
            importUseCase: ImportUseCase,
            sourceSelectionBuilder: SourceSelectionComponent.Builder,
            categoriesReviewBuilder: CategoriesReviewComponent.Builder,
            accountsReviewBuilder: AccountsReviewComponent.Builder,
            transactionsPreviewBuilder: TransactionsPreviewComponent.Builder,
        ): ViewProvider = ImportViewProvider(
            useCase = importUseCase,
            sourceSelection = sourceSelectionBuilder,
            categoriesReview = categoriesReviewBuilder,
            accountsReview = accountsReviewBuilder,
            transactionsPreview = transactionsPreviewBuilder,
        )
    }
}
```

- [ ] **Step 2: Create new ImportViewProvider.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt
package com.hluhovskyi.zero.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.ViewProvider

internal class ImportViewProvider(
    private val useCase: ImportUseCase,
    private val sourceSelection: Buildable<out AttachableViewComponent>,
    private val categoriesReview: Buildable<out AttachableViewComponent>,
    private val accountsReview: Buildable<out AttachableViewComponent>,
    private val transactionsPreview: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportView(
            useCase = useCase,
            sourceSelection = sourceSelection,
            categoriesReview = categoriesReview,
            accountsReview = accountsReview,
            transactionsPreview = transactionsPreview,
        )
    }
}

@Composable
private fun ImportView(
    useCase: ImportUseCase,
    sourceSelection: Buildable<out AttachableViewComponent>,
    categoriesReview: Buildable<out AttachableViewComponent>,
    accountsReview: Buildable<out AttachableViewComponent>,
    transactionsPreview: Buildable<out AttachableViewComponent>,
) {
    val state by useCase.state.collectAsState(
        initial = ImportUseCase.State.SourceSelection(emptyList())
    )

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { androidUri ->
        if (androidUri != null) {
            val uri = Uri(androidUri.toString())
            if (uri is Uri.NonEmpty) {
                useCase.perform(ImportUseCase.Action.SelectFile(uri))
            }
        } else {
            useCase.perform(ImportUseCase.Action.Back)
        }
    }

    when (state) {
        is ImportUseCase.State.SourceSelection -> sourceSelection.AttachWithView()
        is ImportUseCase.State.FilePicker -> {
            LaunchedEffect(state) {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
        is ImportUseCase.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ImportUseCase.State.CategoriesReview -> categoriesReview.AttachWithView()
        is ImportUseCase.State.AccountsReview -> accountsReview.AttachWithView()
        is ImportUseCase.State.TransactionsPreview -> transactionsPreview.AttachWithView()
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Fix any compilation errors. Common issues: `TransactionsPreviewComponent.Dependencies` requires `amountFormatter` and `dateFormatter` — `ImportComponent` must implement these from `ImportComponent.Dependencies`.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt
git commit -m "feat: add new ImportComponent and ImportViewProvider orchestrator"
```

---

## Task 14: Settings export wiring

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/settings/ExportWriter.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/DefaultSettingsViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt`

- [ ] **Step 1: Create ExportWriter.kt**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/settings/ExportWriter.kt
package com.hluhovskyi.zero.settings

fun interface ExportWriter {
    suspend fun write(fileName: String, content: String)
}
```

- [ ] **Step 2: Update SettingsViewModel.kt**

Replace `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableActionStateModel

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
        object Export : Action
        object OpenCurrencyPicker : Action
    }

    sealed interface ExportFeedback {
        object Success : ExportFeedback
        data class Error(val message: String) : ExportFeedback
    }

    data class State(
        val selectedCurrencyName: String = "",
        val exportFeedback: ExportFeedback? = null,
    )
}
```

- [ ] **Step 3: Update DefaultSettingsViewModel.kt**

`SyncSerializer` is injected — it comes from `SettingsComponent.Dependencies` (provided by `SyncComponent.serializer`), not instantiated inline.

Replace `zero-core/src/main/java/com/hluhovskyi/zero/settings/DefaultSettingsViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val settingsCurrencyUseCase: SettingsCurrencyUseCase,
    private val onImportSelected: OnImportSelectedHandler,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
    private val serializer: SyncSerializer,
    private val exportWriter: ExportWriter,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : SettingsViewModel {

    private val mutableState = MutableStateFlow(SettingsViewModel.State())
    override val state: Flow<SettingsViewModel.State> = mutableState

    override fun perform(action: SettingsViewModel.Action) {
        when (action) {
            is SettingsViewModel.Action.Import -> coroutineScope.launch(Dispatchers.Main) {
                onImportSelected.onSelected()
            }
            is SettingsViewModel.Action.Export -> coroutineScope.launch {
                try {
                    val userId = currentUserRepository.query().first().id
                    val snapshot = syncEngine.export(userId)
                    val json = serializer.serialize(snapshot)
                    val date = snapshot.exportedAt.date.toString()
                    exportWriter.write("zero-backup-$date.json", json)
                    mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Success) }
                } catch (e: Exception) {
                    mutableState.update {
                        it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Error(e.message ?: "Unknown error"))
                    }
                }
            }
            is SettingsViewModel.Action.OpenCurrencyPicker -> {
                settingsCurrencyUseCase.perform(SettingsCurrencyUseCase.Action.Request)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            mutableState.update { state ->
                state.copy(selectedCurrencyName = currencyPrimaryUseCase.getPrimaryCurrency().name)
            }
        }
        coroutineScope.launch(Dispatchers.Main) {
            settingsCurrencyUseCase.state
                .filterIsInstance<SettingsCurrencyUseCase.State.Picked>()
                .collect { picked ->
                    coroutineScope.launch {
                        currencyPrimaryUseCase.setPrimaryCurrency(picked.currency.id)
                        mutableState.update { state ->
                            state.copy(selectedCurrencyName = picked.currency.name)
                        }
                    }
                }
        }
    }
}
```

Note: `SyncSerializer` is in `zero-sync`. Check that `zero-core` has a dependency on `zero-sync` in its `build.gradle`. If not, add it.

- [ ] **Step 4: Check zero-core/build.gradle for zero-sync dependency**

```bash
grep "zero-sync" zero-core/build.gradle
```

If not present, add to the dependencies block:
```groovy
implementation project(':zero-sync')
```

- [ ] **Step 5: Update SettingsComponent.kt**

Replace `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsComponent.kt`:

```kotlin
package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class SettingsScope

private const val TAG = "SettingsComponent"

@SettingsScope
@dagger.Component(
    modules = [SettingsComponent.Module::class],
    dependencies = [SettingsComponent.Dependencies::class],
)
abstract class SettingsComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: SettingsViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val syncEngine: SyncEngine
        val currentUserRepository: CurrentUserRepository
        val serializer: SyncSerializer
        val exportWriter: ExportWriter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerSettingsComponent.builder()
            .dependencies(dependencies)
            .onImportSelectedHandler(OnImportSelectedHandler.Noop)
            .settingsCurrencyUseCase(SettingsCurrencyUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<SettingsComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onImportSelectedHandler(handler: OnImportSelectedHandler): Builder

        @BindsInstance
        fun settingsCurrencyUseCase(useCase: SettingsCurrencyUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @SettingsScope
        fun viewModel(
            onImportSelected: OnImportSelectedHandler,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            settingsCurrencyUseCase: SettingsCurrencyUseCase,
            syncEngine: SyncEngine,
            currentUserRepository: CurrentUserRepository,
            serializer: SyncSerializer,
            exportWriter: ExportWriter,
        ): SettingsViewModel = DefaultSettingsViewModel(
            onImportSelected = onImportSelected,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            settingsCurrencyUseCase = settingsCurrencyUseCase,
            syncEngine = syncEngine,
            currentUserRepository = currentUserRepository,
            serializer = serializer,
            exportWriter = exportWriter,
        )

        @Provides
        @SettingsScope
        fun viewProvider(viewModel: SettingsViewModel): ViewProvider =
            SettingsViewProvider(viewModel = viewModel)
    }
}
```

- [ ] **Step 6: Update SettingsViewProvider.kt — wire Export click and snackbar**

Replace the relevant parts of `SettingsViewProvider.kt`. The `MoreView` function changes to:

```kotlin
@Composable
private fun MoreView(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState(initial = SettingsViewModel.State())
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exportFeedback) {
        when (val feedback = state.exportFeedback) {
            SettingsViewModel.ExportFeedback.Success ->
                snackbarHostState.showSnackbar("Backup saved to Downloads")
            is SettingsViewModel.ExportFeedback.Error ->
                snackbarHostState.showSnackbar("Export failed: ${feedback.message}")
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            item { MoreHeader() }
            item {
                MoreSection(title = "PREFERENCES") {
                    MoreRow(
                        icon = Icons.Outlined.Payments,
                        primaryText = "Primary Currency",
                        secondaryText = state.selectedCurrencyName.ifEmpty { "Loading…" },
                        onClick = { viewModel.perform(SettingsViewModel.Action.OpenCurrencyPicker) },
                    )
                }
            }
            item {
                MoreSection(title = "DATA") {
                    MoreRow(
                        icon = Icons.Outlined.MoveToInbox,
                        primaryText = "Import Data",
                        secondaryText = "Migrate history from other apps",
                        onClick = { viewModel.perform(SettingsViewModel.Action.Import) },
                    )
                    MoreRow(
                        icon = Icons.Outlined.Download,
                        primaryText = "Export Data",
                        secondaryText = "Save a backup of your data",
                        onClick = { viewModel.perform(SettingsViewModel.Action.Export) },
                    )
                }
            }
            item {
                MoreSection(title = "SECURITY") {
                    MoreRow(
                        icon = Icons.Outlined.Fingerprint,
                        primaryText = "Biometric Lock",
                        secondaryText = "Face ID or Fingerprint required on open",
                        onClick = { /* placeholder */ },
                        showChevron = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
```

Add the new imports to `SettingsViewProvider.kt`:
```kotlin
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.remember
```

- [ ] **Step 7: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/
git commit -m "feat: add export wiring to Settings (ExportWriter, snackbar feedback)"
```

---

## Task 15: App wiring — ActivityComponent, ApplicationComponent, MainActivityScreenComponent

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Update ActivityComponent.kt**

`ActivityComponent` must implement the new `SettingsComponent.Dependencies` and `ImportComponent.Dependencies`. Update `ActivityComponent.Dependencies` — remove `importSourceUseCase`, add new fields:

```kotlin
interface Dependencies {
    val dispatcherProvider: DispatcherProvider
    val clock: Clock
    val zoneProvider: ZoneProvider
    val imageLoader: ImageLoader
    val amountFormatter: AmountFormatter
    val dateFormatter: DateFormatter
    val androidUriResourceFactory: AndroidUriResourceFactory
    val incorrectStateDetector: IncorrectStateDetector

    val categoriesQueryUseCase: CategoriesQueryUseCase
    val currencyPrimaryUseCase: CurrencyPrimaryUseCase
    val currencyConvertUseCase: CurrencyConvertUseCase

    val accountRepository: AccountRepository
    val currencyRepository: CurrencyRepository
    val categoryRepository: CategoryRepository
    val transactionRepository: TransactionRepository
    val iconRepository: IconRepository
    val colorRepository: ColorRepository

    // New
    val syncEngine: SyncEngine
    val currentUserRepository: CurrentUserRepository
    val serializer: SyncSerializer   // from SyncComponent.serializer
    val exportWriter: ExportWriter
}
```

`ZeroBackupParser` only needs `syncEngine` now (file reading moved into `SyncEngine.loadSnapshot`). Update `importComponentBuilder()`:

```kotlin
@Provides
@ActivityScope
fun importComponentBuilder(
    component: ActivityComponent,
    syncEngine: SyncEngine,
    clock: Clock,
    idGenerator: IdGenerator,
    logger: Logger,
): ImportComponent.Builder {
    val parsers: List<SnapshotParser> = listOf(
        ZeroBackupParser(syncEngine = syncEngine),
        ZenMoneySnapshotParser(
            resourceResolver = component.resourceResolver,
            idGenerator = idGenerator,
            clock = clock,
            logger = logger,
        ),
    )
    return ImportComponent.builder(component).parsers(parsers)
}
```

`ZenMoneySnapshotParser` still needs `ResourceResolver` since it reads CSV files directly. Ensure `abstract val resourceResolver: ResourceResolver` is on `ActivityComponent` (add it if missing).

Remove the old `ImportComponent.Dependencies` from the `abstract class ActivityComponent :` implements list and re-add `ImportComponent.Dependencies` (now with the new interface shape). Add all necessary imports.

- [ ] **Step 2: Update ApplicationComponent.kt**

Add `ExportWriter` provision, wire `resourceResolver` into `SyncComponent`, and expose `serializer` from `SyncComponent` for the dependency chain. Remove the old `ZenMoneyImportComponent.Dependencies` from the `abstract class ApplicationComponent :` implements list.

Update `syncComponent()` in `ApplicationComponent.Module`:

```kotlin
@Provides
@ApplicationScope
fun syncComponent(
    databaseComponent: DatabaseComponent,
    resourceResolver: ResourceResolver,
): SyncComponent = SyncComponent.factory(
    object : SyncComponent.Dependencies {
        override val categorySyncSource = databaseComponent.categorySyncSource()
        override val categorySyncSink = databaseComponent.categorySyncSink()
        override val accountSyncSource = databaseComponent.accountSyncSource()
        override val accountSyncSink = databaseComponent.accountSyncSink()
        override val transactionSyncSource = databaseComponent.transactionSyncSource()
        override val transactionSyncSink = databaseComponent.transactionSyncSink()
        override val resourceResolver = resourceResolver
    },
).create()

@Provides
fun syncEngine(syncComponent: SyncComponent): SyncEngine = syncComponent.syncEngine

@Provides
@ApplicationScope
fun syncSerializer(syncComponent: SyncComponent): SyncSerializer = syncComponent.serializer
```

Add `ExportWriter` provision in `ApplicationComponent.Module`:

```kotlin
@Provides
@ApplicationScope
fun exportWriter(context: Context): ExportWriter = ExportWriter { fileName, content ->
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
    }
    val insertUri = context.contentResolver.insert(
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        contentValues,
    ) ?: error("Could not create file in Downloads")
    context.contentResolver.openOutputStream(insertUri)?.use { output ->
        output.write(content.toByteArray())
    }
    contentValues.clear()
    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
    context.contentResolver.update(insertUri, contentValues, null, null)
}
```

Also ensure `syncEngine` and `currentUserRepository` are exposed as abstract members (so `ApplicationComponent` satisfies `ActivityComponent.Dependencies`). Check the current file — `syncEngine` has a `@Provides` method in Module but may not be an abstract member. Dagger will wire it automatically through the component. No abstract member needed — `ApplicationComponent` as a `@dagger.Component` will satisfy `ActivityComponent.Dependencies` fields from Module `@Provides` methods.

- [ ] **Step 3: Re-wire MainActivityScreenComponent importNavigationEntry**

In `MainActivityScreenComponent.kt`, restore the import navigation entry (previously commented out in Task 1):

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun importNavigationEntry(
    componentBuilder: ImportComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(Destinations.Import) {
    componentBuilder
        .onImportFinishedHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 4: Full build**

```bash
./gradlew assembleDebug
```

Fix any remaining compilation errors. Common issues:
- Missing imports
- `ResourceResolver` not exposed on `ActivityComponent` — add `abstract val resourceResolver: ResourceResolver`
- Dagger component graph errors — read error messages carefully and add missing `@Provides` methods

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt
git add app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: wire new import/export into app — update ActivityComponent and ApplicationComponent"
```

---

## Task 16: Final build verification

- [ ] **Step 1: Run all tests**

```bash
./gradlew test
```

Expected: PASS (especially `:zero-sync:test`)

- [ ] **Step 2: Full debug build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual smoke test checklist**

Install on device/emulator and verify:
1. Settings → Export Data → file appears in Downloads folder
2. Settings → Import Data → Source Selection screen shows "Zero Backup" and "ZenMoney"
3. Select Zero Backup → file picker opens
4. Select a previously exported JSON → Categories Review shows categories read-only
5. Tap "Next →" → Accounts Review shows accounts read-only
6. Tap "Next: Review Transactions →" → Transactions Preview shows transactions
7. Tap "Confirm & Import" → returns to app
8. Back navigation: from Categories Review → Source Selection; from Accounts Review → Categories Review; from Transactions Preview → Accounts Review
9. Dismiss file picker without selecting → returns to Source Selection

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete import/export rework with SyncEngine-powered flow"
```
