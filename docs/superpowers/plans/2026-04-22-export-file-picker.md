# Export File Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fixed-to-Downloads export with an `ACTION_CREATE_DOCUMENT` SAF file picker, and extract the export pipeline into a dedicated `ExportUseCase`.

**Architecture:** The ViewProvider launches `ActivityResultContracts.CreateDocument` on button click; when the user picks a destination the URI is passed to the ViewModel as `Action.Export(uri)`. `DefaultExportUseCase` owns the get-user → export-snapshot → serialize → write pipeline and returns a sealed `ExportUseCase.Result`. The ViewModel maps that result to `ExportFeedback` state.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.activity:activity-compose` (already a dep), Dagger, JUnit 4, Mockito / mockito-kotlin.

---

## File Map

| Action | Path |
|--------|------|
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/export/ExportWriter.kt` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/export/DefaultExportWriter.kt` |
| Create | `zero-core/src/main/java/com/hluhovskyi/zero/export/ExportUseCase.kt` |
| Create | `zero-core/src/main/java/com/hluhovskyi/zero/export/DefaultExportUseCase.kt` |
| Create | `zero-core/src/test/java/com/hluhovskyi/zero/export/DefaultExportUseCaseTest.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/settings/DefaultSettingsViewModel.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsComponent.kt` |
| Modify | `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt` |

---

### Task 1: Update `ExportWriter` and `DefaultExportWriter`

Change the interface to accept a `Uri.NonEmpty` destination instead of a filename string. `DefaultExportWriter` simplifies to writing bytes to a SAF URI via `contentResolver` — all MediaStore / API-29 branching is removed.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/export/ExportWriter.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/export/DefaultExportWriter.kt`

- [ ] **Step 1: Replace `ExportWriter.kt`**

```kotlin
package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Uri

fun interface ExportWriter {
    suspend fun write(uri: Uri.NonEmpty, content: String)
}
```

- [ ] **Step 2: Replace `DefaultExportWriter.kt`**

```kotlin
package com.hluhovskyi.zero.export

import android.content.Context
import android.net.Uri as AndroidUri
import com.hluhovskyi.zero.common.Uri

internal class DefaultExportWriter(
    private val context: Context,
) : ExportWriter {

    override suspend fun write(uri: Uri.NonEmpty, content: String) {
        val androidUri = AndroidUri.parse(uri.value)
        context.contentResolver.openOutputStream(androidUri)?.use { output ->
            output.write(content.toByteArray())
        } ?: error("Could not open output stream for uri: ${uri.value}")
    }
}
```

- [ ] **Step 3: Compile to verify no errors**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL (the only callers of `ExportWriter.write` will be fixed in later tasks — if Dagger generated code fails, that is expected and will be resolved in Task 7).

- [ ] **Step 4: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/export/ExportWriter.kt
git add app/src/main/java/com/hluhovskyi/zero/export/DefaultExportWriter.kt
git commit -m "refactor: change ExportWriter to accept Uri.NonEmpty instead of filename"
```

---

### Task 2: Create `ExportUseCase` interface

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/export/ExportUseCase.kt`

- [ ] **Step 1: Create `ExportUseCase.kt`**

```kotlin
package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Uri

interface ExportUseCase {
    suspend fun export(uri: Uri.NonEmpty): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val message: String) : Result
    }
}
```

---

### Task 3: Write failing test for `DefaultExportUseCase`

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/export/DefaultExportUseCaseTest.kt`

- [ ] **Step 1: Create test file**

```kotlin
package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DefaultExportUseCaseTest {

    @Mock private lateinit var currentUserRepository: CurrentUserRepository
    @Mock private lateinit var syncEngine: SyncEngine
    @Mock private lateinit var exportWriter: ExportWriter

    private val serializer = SyncSerializer()

    private val userId = Id.Known("user-1")
    private val testUri = Uri("content://test/backup.json") as Uri.NonEmpty
    private val snapshot = SyncSnapshot(
        version = 1,
        userId = userId,
        exportedAt = LocalDateTime(2026, 4, 22, 0, 0),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
    )

    private lateinit var useCase: DefaultExportUseCase

    @Before
    fun setUp() {
        useCase = DefaultExportUseCase(
            currentUserRepository = currentUserRepository,
            syncEngine = syncEngine,
            serializer = serializer,
            exportWriter = exportWriter,
        )
    }

    @Test
    fun `export returns Success when all steps succeed`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
        whenever(syncEngine.export(userId)).thenReturn(snapshot)

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Success, result)
        verify(exportWriter).write(testUri, serializer.serialize(snapshot))
    }

    @Test
    fun `export returns Failure when repository emits error`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(
            flow { throw RuntimeException("db error") }
        )

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Failure("db error"), result)
    }

    @Test
    fun `export returns Failure when sync engine throws`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
        whenever(syncEngine.export(userId)).thenThrow(RuntimeException("sync failed"))

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Failure("sync failed"), result)
    }

    @Test
    fun `export returns Failure with unknown error when exception has no message`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(
            flow { throw RuntimeException() }
        )

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Failure("Unknown error"), result)
    }
}
```

- [ ] **Step 2: Run the tests — expect failure because `DefaultExportUseCase` doesn't exist yet**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.export.DefaultExportUseCaseTest"`

Expected: BUILD FAILED — unresolved reference `DefaultExportUseCase`

---

### Task 4: Implement `DefaultExportUseCase`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/export/DefaultExportUseCase.kt`

- [ ] **Step 1: Create `DefaultExportUseCase.kt`**

```kotlin
package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.first

internal class DefaultExportUseCase(
    private val currentUserRepository: CurrentUserRepository,
    private val syncEngine: SyncEngine,
    private val serializer: SyncSerializer,
    private val exportWriter: ExportWriter,
) : ExportUseCase {

    override suspend fun export(uri: Uri.NonEmpty): ExportUseCase.Result {
        return try {
            val userId = currentUserRepository.query().first().id
            val snapshot = syncEngine.export(userId)
            val json = serializer.serialize(snapshot)
            exportWriter.write(uri, json)
            ExportUseCase.Result.Success
        } catch (e: Exception) {
            ExportUseCase.Result.Failure(e.message ?: "Unknown error")
        }
    }
}
```

- [ ] **Step 2: Run tests — expect all pass**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.export.DefaultExportUseCaseTest"`

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/export/ExportUseCase.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/export/DefaultExportUseCase.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/export/DefaultExportUseCaseTest.kt
git commit -m "feat: add ExportUseCase with sealed Result and DefaultExportUseCase"
```

---

### Task 5: Update `SettingsViewModel` and `DefaultSettingsViewModel`

Change `Action.Export` to carry a `Uri.NonEmpty`, and slim down the ViewModel to only depend on `ExportUseCase`.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/DefaultSettingsViewModel.kt`

- [ ] **Step 1: Replace `SettingsViewModel.kt`**

```kotlin
package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Uri

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
        data class Export(val uri: Uri.NonEmpty) : Action
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

- [ ] **Step 2: Replace `DefaultSettingsViewModel.kt`**

```kotlin
package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.export.ExportUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val settingsCurrencyUseCase: SettingsCurrencyUseCase,
    private val onImportSelected: OnImportSelectedHandler,
    private val exportUseCase: ExportUseCase,
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
                when (val result = exportUseCase.export(action.uri)) {
                    ExportUseCase.Result.Success ->
                        mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Success) }
                    is ExportUseCase.Result.Failure ->
                        mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Error(result.message)) }
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

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: BUILD SUCCESSFUL (Dagger wiring in `SettingsComponent` will break — fixed in Task 6)

- [ ] **Step 4: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/DefaultSettingsViewModel.kt
git commit -m "refactor: SettingsViewModel uses ExportUseCase, Action.Export carries Uri"
```

---

### Task 6: Update `SettingsComponent` DI wiring

Add a `@Provides` binding for `ExportUseCase` and update the `viewModel()` binding to use it.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsComponent.kt`

- [ ] **Step 1: Replace `SettingsComponent.kt`**

```kotlin
package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.export.DefaultExportUseCase
import com.hluhovskyi.zero.export.ExportUseCase
import com.hluhovskyi.zero.export.ExportWriter
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
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
        fun exportUseCase(
            currentUserRepository: CurrentUserRepository,
            syncEngine: SyncEngine,
            serializer: SyncSerializer,
            exportWriter: ExportWriter,
        ): ExportUseCase = DefaultExportUseCase(
            currentUserRepository = currentUserRepository,
            syncEngine = syncEngine,
            serializer = serializer,
            exportWriter = exportWriter,
        )

        @Provides
        @SettingsScope
        fun viewModel(
            onImportSelected: OnImportSelectedHandler,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            settingsCurrencyUseCase: SettingsCurrencyUseCase,
            exportUseCase: ExportUseCase,
        ): SettingsViewModel = DefaultSettingsViewModel(
            onImportSelected = onImportSelected,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            settingsCurrencyUseCase = settingsCurrencyUseCase,
            exportUseCase = exportUseCase,
        )

        @Provides
        @SettingsScope
        fun viewProvider(viewModel: SettingsViewModel): ViewProvider = SettingsViewProvider(viewModel = viewModel)
    }
}
```

- [ ] **Step 2: Compile to verify Dagger wiring**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsComponent.kt
git commit -m "refactor: wire ExportUseCase in SettingsComponent"
```

---

### Task 7: Update `SettingsViewProvider` with SAF file picker

Replace the snackbar-on-success export trigger with a `CreateDocument` launcher. The export button launches the system file picker; the returned URI is passed to the ViewModel.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt`

- [ ] **Step 1: Replace `SettingsViewProvider.kt`**

```kotlin
package com.hluhovskyi.zero.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class SettingsViewProvider(
    private val viewModel: SettingsViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        MoreView(viewModel = viewModel)
    }
}

@Composable
private fun MoreView(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState(initial = SettingsViewModel.State())
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { androidUri ->
        if (androidUri != null) {
            val uri = Uri(androidUri.toString())
            if (uri is Uri.NonEmpty) {
                viewModel.perform(SettingsViewModel.Action.Export(uri))
            }
        }
    }

    LaunchedEffect(state.exportFeedback) {
        when (val feedback = state.exportFeedback) {
            SettingsViewModel.ExportFeedback.Success ->
                snackbarHostState.showSnackbar("Backup saved")
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
                        onClick = {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            exportLauncher.launch("zero-backup-$date.json")
                        },
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

@Composable
private fun MoreHeader() {
    Text(
        text = "More",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
        ),
    )
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLowest, RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    primaryText: String,
    secondaryText: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = OnSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                ),
            )
            Text(
                text = secondaryText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                ),
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :zero-core:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add zero-core/src/main/java/com/hluhovskyi/zero/settings/SettingsViewProvider.kt
git commit -m "feat: export button opens SAF file picker (ACTION_CREATE_DOCUMENT)"
```

---

### Task 8: Full build and lint verification

- [ ] **Step 1: Full debug build**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Lint**

Run: `./gradlew lintDebug`

Expected: BUILD SUCCESSFUL with no new errors

---

### Task 9: Push and open PR

- [ ] **Step 1: Push branch**

Run: `git push -u origin feature/export-file-picker`

- [ ] **Step 2: Open PR**

Run:
```
gh pr create --title "feat: export button opens file picker (SAF) + ExportUseCase" --body "$(cat <<'EOF'
## Summary
- Export button now opens the system file picker (`ACTION_CREATE_DOCUMENT`) with a pre-filled filename (`zero-backup-YYYY-MM-DD.json`); user chooses the save location
- Extracted export pipeline (get user → snapshot → serialize → write) into `ExportUseCase` / `DefaultExportUseCase` with a sealed `Result` type — no more raw sync deps in the ViewModel
- `ExportWriter` now accepts `Uri.NonEmpty` instead of a filename; `DefaultExportWriter` simplified to a single `contentResolver.openOutputStream()` call (MediaStore boilerplate removed)

## Test plan
- [ ] Tap Export Data → system file picker appears with `zero-backup-<today>.json` pre-filled
- [ ] Pick a location → snackbar shows "Backup saved"
- [ ] Cancel the picker → nothing happens, no crash
- [ ] Verify `DefaultExportUseCaseTest` (4 tests) passes: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.export.DefaultExportUseCaseTest"`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
