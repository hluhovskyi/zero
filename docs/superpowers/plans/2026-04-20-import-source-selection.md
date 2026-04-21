# Import Source Selection Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the import source selection screen (step 0) to match the Claude Design spec, including a polished card layout and inline error banner for parse failures.

**Architecture:** Error state is carried in `ImportUseCase.State.SourceSelection` and flows through `DefaultSourceSelectionViewModel` to `SourceSelectionViewProvider`. `ImportViewModel` and `ImportViewProvider` are untouched. `ImportErrorBanner` is a generic reusable composable in `zero-ui`.

**Tech Stack:** Kotlin, Jetpack Compose Material, Dagger, JUnit 4 + Mockito + kotlinx-coroutines-test.

---

## File Map

| File | Status | Role |
|---|---|---|
| `zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt` | Modify | Add `error: String?` to `SourceSelection` state; add `DismissError` / `Retry` actions |
| `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt` | Modify | try/catch in `SelectFile`; handle `DismissError` and `Retry` |
| `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt` | Create | Unit tests for error state transitions |
| `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewModel.kt` | Modify | Add `error: String?` to `State`; add `DismissError` / `Retry` actions |
| `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModel.kt` | Modify | Map `error` field; forward new actions to use case |
| `zero-core/src/test/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModelTest.kt` | Create | Unit tests for error propagation and action forwarding |
| `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportErrorBanner.kt` | Create | Generic reusable error banner composable |
| `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt` | Modify | Full visual redesign: cards, hint row, error banner |

---

### Task 1: Extend `ImportUseCase` with error state and new actions

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt`

- [ ] **Step 1: Replace `SourceSelection` object with data class, add new actions**

Open `zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt`. Replace the entire file with:

```kotlin
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
        object DismissError : Action
        object Retry : Action
    }

    sealed interface State {
        data class SourceSelection(
            val sources: List<Source>,
            val error: String? = null,
        ) : State
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

- [ ] **Step 2: Build `zero-api` to confirm no compile errors**

```bash
./gradlew :zero-api:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt
git commit -m "feat: add error state and DismissError/Retry actions to ImportUseCase"
```

---

### Task 2: Write failing tests for `DefaultImportUseCase` error handling

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultImportUseCaseTest {

    @Mock private lateinit var parser: SnapshotParser
    @Mock private lateinit var syncEngine: SyncEngine
    @Mock private lateinit var currentUserRepository: CurrentUserRepository

    private val source = KnownSource.ZeroBackup
    private val userId = Id.Known("user-1")
    private val testUri = Uri("file://test.zero") as Uri.NonEmpty

    @Before
    fun setUp() {
        whenever(parser.source).thenReturn(source)
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
    }

    private fun createUseCase(scope: CoroutineScope) = DefaultImportUseCase(
        parsers = listOf(parser),
        syncEngine = syncEngine,
        currentUserRepository = currentUserRepository,
        onImportFinishedHandler = OnImportFinishedHandler.Noop,
        coroutineScope = scope,
    )

    @Test
    fun `SelectFile sets error state when parser throws`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNotNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `DismissError clears the error`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.DismissError)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `Retry transitions to FilePicker`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.Retry)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.FilePicker) { "Expected FilePicker but got $state" }
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

```bash
./gradlew :zero-core:test --tests "com.hluhovskyi.zero.imports.DefaultImportUseCaseTest"
```

Expected: 3 test failures (compile error or assertion failures — `DismissError`/`Retry` not yet handled).

---

### Task 3: Implement error handling in `DefaultImportUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

- [ ] **Step 1: Add try/catch to `SelectFile` and handle `DismissError`/`Retry`**

Replace the entire file `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`:

```kotlin
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
        InternalState(screen = ImportUseCase.State.SourceSelection(parsers.map { it.source })),
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
                try {
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
                } catch (e: Exception) {
                    mutableState.update { current ->
                        InternalState(
                            selectedSource = current.selectedSource,
                            screen = ImportUseCase.State.SourceSelection(
                                sources = parsers.map { it.source },
                                error = "Couldn't read file. Check the format and try again.",
                            ),
                        )
                    }
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
                            amount = Amount(syncTx.amount.toBigDecimalOrNull()),
                            dateTime = syncTx.enteredDateTime,
                            categoryId = syncTx.categoryId?.let { Id.Known(it) },
                            categoryName = categoryName,
                        )
                        SyncTransaction.Type.INCOME -> ImportTransaction.Income(
                            id = syncTx.id,
                            accountId = syncTx.accountId,
                            currencyId = syncTx.currencyId,
                            amount = Amount(syncTx.amount.toBigDecimalOrNull()),
                            dateTime = syncTx.enteredDateTime,
                            categoryId = syncTx.categoryId?.let { Id.Known(it) },
                            categoryName = categoryName,
                        )
                        SyncTransaction.Type.TRANSFER -> ImportTransaction.Transfer(
                            id = syncTx.id,
                            accountId = syncTx.accountId,
                            currencyId = syncTx.currencyId,
                            amount = Amount(syncTx.amount.toBigDecimalOrNull()),
                            dateTime = syncTx.enteredDateTime,
                            targetAccountId = Id.Known(syncTx.targetAccountId ?: syncTx.accountId.value),
                            targetAmount = Amount(syncTx.targetAmount?.toBigDecimalOrNull()),
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
                val delta = mutableState.value.storedDelta ?: return
                coroutineScope.launch {
                    val userId = currentUserRepository.query().first().id
                    syncEngine.import(delta, userId)
                    coroutineScope.launch(Dispatchers.Main) {
                        onImportFinishedHandler.onFinished()
                    }
                }
            }
            is ImportUseCase.Action.DismissError -> mutableState.update {
                InternalState(screen = ImportUseCase.State.SourceSelection(parsers.map { it.source }))
            }
            is ImportUseCase.Action.Retry -> mutableState.update { current ->
                current.copy(screen = ImportUseCase.State.FilePicker)
            }
            is ImportUseCase.Action.Back -> mutableState.update { current ->
                when (current.screen) {
                    is ImportUseCase.State.FilePicker,
                    is ImportUseCase.State.SourceSelection,
                    is ImportUseCase.State.Loading,
                    is ImportUseCase.State.CategoriesReview,
                    -> InternalState(
                        screen = ImportUseCase.State.SourceSelection(parsers.map { it.source }),
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

- [ ] **Step 2: Run the tests — expect all 3 to pass**

```bash
./gradlew :zero-core:test --tests "com.hluhovskyi.zero.imports.DefaultImportUseCaseTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt
git commit -m "feat: handle parse errors in DefaultImportUseCase with inline error state"
```

---

### Task 4: Extend `SourceSelectionViewModel` with error state and new actions

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewModel.kt`

- [ ] **Step 1: Add `error` to `State`, add `DismissError` and `Retry` actions**

Replace the file:

```kotlin
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.Source

interface SourceSelectionViewModel : ActionStateModel<SourceSelectionViewModel.Action, SourceSelectionViewModel.State> {

    data class State(
        val sources: List<Source> = emptyList(),
        val error: String? = null,
    )

    sealed interface Action {
        data class SelectSource(val source: Source) : Action
        object Close : Action
        object DismissError : Action
        object Retry : Action
    }
}
```

- [ ] **Step 2: Build `zero-core` to surface compile errors in `DefaultSourceSelectionViewModel`**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: compile error in `DefaultSourceSelectionViewModel` (unhandled `when` branches).

---

### Task 5: Write failing tests for `DefaultSourceSelectionViewModel`

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModelTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.imports.ImportUseCase
import com.hluhovskyi.zero.imports.OnImportFinishedHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSourceSelectionViewModelTest {

    private val mutableUseCaseState = MutableStateFlow<ImportUseCase.State>(
        ImportUseCase.State.SourceSelection(sources = emptyList()),
    )
    private val capturedActions = mutableListOf<ImportUseCase.Action>()

    private val fakeUseCase = object : ImportUseCase {
        override val state: Flow<ImportUseCase.State> = mutableUseCaseState
        override fun perform(action: ImportUseCase.Action) { capturedActions.add(action) }
        override fun attach(): Closeable = Closeables.empty()
    }

    private val viewModel = DefaultSourceSelectionViewModel(
        importUseCase = fakeUseCase,
        onImportFinishedHandler = OnImportFinishedHandler.Noop,
    )

    @Test
    fun `state maps error from use case SourceSelection state`() = runTest {
        mutableUseCaseState.value = ImportUseCase.State.SourceSelection(
            sources = emptyList(),
            error = "Parse failed",
        )

        val state = viewModel.state.first()
        assertEquals("Parse failed", state.error)
    }

    @Test
    fun `state maps null error when use case has no error`() = runTest {
        mutableUseCaseState.value = ImportUseCase.State.SourceSelection(
            sources = emptyList(),
            error = null,
        )

        val state = viewModel.state.first()
        assertNull(state.error)
    }

    @Test
    fun `DismissError action forwards to ImportUseCase`() = runTest {
        viewModel.perform(SourceSelectionViewModel.Action.DismissError)

        assert(capturedActions.contains(ImportUseCase.Action.DismissError)) {
            "Expected DismissError in captured actions: $capturedActions"
        }
    }

    @Test
    fun `Retry action forwards to ImportUseCase`() = runTest {
        viewModel.perform(SourceSelectionViewModel.Action.Retry)

        assert(capturedActions.contains(ImportUseCase.Action.Retry)) {
            "Expected Retry in captured actions: $capturedActions"
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (compile error expected at this point)**

```bash
./gradlew :zero-core:test --tests "com.hluhovskyi.zero.imports.sourceselection.DefaultSourceSelectionViewModelTest"
```

Expected: compile error or test failures.

---

### Task 6: Implement changes in `DefaultSourceSelectionViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModel.kt`

- [ ] **Step 1: Map `error` field and handle new actions**

Replace the file:

```kotlin
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
        .map { SourceSelectionViewModel.State(sources = it.sources, error = it.error) }

    override fun perform(action: SourceSelectionViewModel.Action) {
        when (action) {
            is SourceSelectionViewModel.Action.SelectSource ->
                importUseCase.perform(ImportUseCase.Action.SelectSource(action.source))
            is SourceSelectionViewModel.Action.Close ->
                onImportFinishedHandler.onFinished()
            is SourceSelectionViewModel.Action.DismissError ->
                importUseCase.perform(ImportUseCase.Action.DismissError)
            is SourceSelectionViewModel.Action.Retry ->
                importUseCase.perform(ImportUseCase.Action.Retry)
        }
    }
}
```

- [ ] **Step 2: Run all tests — expect all to pass**

```bash
./gradlew :zero-core:test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModel.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModelTest.kt
git commit -m "feat: propagate error state through SourceSelectionViewModel"
```

---

### Task 7: Create `ImportErrorBanner` in `zero-ui`

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportErrorBanner.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.ErrorContainer

@Composable
fun ImportErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ErrorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Couldn't import file",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Error,
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color(0xFF93000A),
                lineHeight = 19.sp,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(Error, RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Try Again",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Dismiss",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Error,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build `zero-ui` to confirm no compile errors**

```bash
./gradlew :zero-ui:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportErrorBanner.kt
git commit -m "feat: add ImportErrorBanner reusable composable to zero-ui"
```

---

### Task 8: Redesign `SourceSelectionViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt`

- [ ] **Step 1: Replace with polished card-based design**

```kotlin
package com.hluhovskyi.zero.imports.sourceselection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.KnownSource
import com.hluhovskyi.zero.imports.Source
import com.hluhovskyi.zero.ui.ImportErrorBanner
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        ModalHeader(
            title = "Import Data",
            onClose = { viewModel.perform(SourceSelectionViewModel.Action.Close) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val error = state.error
            if (error != null) {
                item(key = "error_banner") {
                    ImportErrorBanner(
                        message = error,
                        onRetry = { viewModel.perform(SourceSelectionViewModel.Action.Retry) },
                        onDismiss = { viewModel.perform(SourceSelectionViewModel.Action.DismissError) },
                    )
                }
            }
            item(key = "subtitle") {
                Text(
                    text = "Choose a data source. Zero will preview what will be imported before anything is saved.",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(state.sources, key = { it.key }) { source ->
                SourceCard(
                    source = source,
                    onClick = { viewModel.perform(SourceSelectionViewModel.Action.SelectSource(source)) },
                )
            }
            item(key = "hint") {
                MoreSourcesHint()
            }
        }
    }
}

private data class SourceCardConfig(
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val title: String,
    val description: String,
)

private fun sourceCardConfig(source: Source): SourceCardConfig? = when (source.key) {
    KnownSource.ZeroBackup.key -> SourceCardConfig(
        icon = Icons.Filled.Backup,
        iconBg = Color(0xFFE8EEFF),
        iconTint = PrimaryContainer,
        title = "Zero Backup",
        description = "Restore from a .zero backup file",
    )
    KnownSource.ZenMoney.key -> SourceCardConfig(
        icon = Icons.Filled.Description,
        iconBg = Color(0xFFE8F5E9),
        iconTint = Color(0xFF1B5E20),
        title = "ZenMoney CSV",
        description = "Import transactions from a ZenMoney export",
    )
    else -> null
}

@Composable
private fun SourceCard(source: Source, onClick: () -> Unit) {
    val config = sourceCardConfig(source) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLowest, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(config.iconBg, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                tint = config.iconTint,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = config.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            )
            Text(
                text = config.description,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = OutlineVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MoreSourcesHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(SurfaceContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Outline,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "More sources coming soon",
            fontSize = 13.sp,
            color = OnSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: Build the full project**

```bash
./gradlew :zero-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run all tests**

```bash
./gradlew :zero-core:test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt
git commit -m "feat: redesign import source selection screen to match design spec"
```

---

### Task 9: UI verification

- [ ] **Step 1: Install the app on device/emulator**

```bash
./gradlew :app:installDebug
```

Expected: app installs successfully.

- [ ] **Step 2: Navigate to the import screen and verify visually**

Open the app → Settings → Import (or wherever the import entry point is). Confirm:
- Header shows "Import Data" with × button
- Two source cards ("Zero Backup" and "ZenMoney CSV") with correct icons, backgrounds, and chevrons
- "More sources coming soon" hint row at the bottom

- [ ] **Step 3: Verify error banner**

Pick a source, then select a corrupted or wrong-format file. Confirm:
- Error banner appears at the top with "Couldn't import file" title
- "Try Again" button re-opens the file picker
- "Dismiss" button hides the banner

- [ ] **Step 4: Use `dump-ui.sh` to verify layout bounds if anything looks off**

```bash
./scripts/dump-ui.sh
```

- [ ] **Step 5: Final commit (if any tweaks were needed)**

```bash
git add -p
git commit -m "fix: address UI verification findings on import source selection"
```
