# Import Back Button & Name-Based Merge — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix hardware back navigation in the import flow, and use existing category/account icon+color when an import item matches by name.

**Architecture:** The import flow lives in a single navigation route rendered by `ImportViewProvider`. A `BackHandler` intercepts hardware back on steps 1–3. Name matching runs during file parsing in `DefaultImportUseCase` by comparing against `CategoryRepository`/`AccountRepository`, reusing the icon lookup already done for categories and extending it to accounts.

**Tech Stack:** Kotlin, Jetpack Compose `BackHandler`, Dagger 2, Coroutines/Flow, Mockito.

---

## File Map

| File | Change |
|------|--------|
| `zero-core/…/imports/ImportViewProvider.kt` | Add `BackHandler` |
| `zero-core/…/imports/ImportDisplayModels.kt` | Add `icon: Image?` to `ImportAccount` |
| `zero-core/…/imports/DefaultImportUseCase.kt` | Add repos, pre-fetch lookups, name-match logic |
| `zero-core/…/imports/ImportComponent.kt` | Add `CategoryRepository`, `AccountRepository` to `Dependencies`; pass `imageLoader` to AccountsReview |
| `zero-core/…/imports/accountsreview/AccountsReviewComponent.kt` | Add `imageLoader` dep + binding |
| `zero-core/…/imports/accountsreview/AccountsReviewViewProvider.kt` | Accept `imageLoader`, render icon |
| `zero-core/…/imports/DefaultImportUseCaseTest.kt` | Update `createUseCase` to pass mock repos; add name-merge tests |

---

## Task 1: Create feature branch

- [ ] **Step 1: Create and checkout branch**

```bash
git -C /Users/google-mac/Projects/zero checkout -b feat/import-back-and-name-merge
```

Expected: `Switched to a new branch 'feat/import-back-and-name-merge'`

---

## Task 2: Hardware back button in ImportViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt`

- [ ] **Step 1: Add `BackHandler` to `ImportView` composable**

Open `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt`.

Add the import and the handler. The full updated `ImportView` function:

```kotlin
import androidx.activity.compose.BackHandler

@Composable
private fun ImportView(
    viewModel: ImportViewModel,
    sourceSelection: Buildable<out AttachableViewComponent>,
    categoriesReview: Buildable<out AttachableViewComponent>,
    accountsReview: Buildable<out AttachableViewComponent>,
    transactionsPreview: Buildable<out AttachableViewComponent>,
) {
    val state by viewModel.state.collectAsState(
        initial = ImportViewModel.State.SourceSelection,
    )

    BackHandler(
        enabled = state !is ImportViewModel.State.SourceSelection &&
                  state !is ImportViewModel.State.FilePicker,
    ) {
        viewModel.perform(ImportViewModel.Action.Back)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { androidUri ->
        if (androidUri != null) {
            val uri = Uri(androidUri.toString())
            if (uri is Uri.NonEmpty) {
                viewModel.perform(ImportViewModel.Action.SelectFile(uri))
            }
        } else {
            viewModel.perform(ImportViewModel.Action.Back)
        }
    }

    when (state) {
        ImportViewModel.State.SourceSelection -> sourceSelection.AttachWithView()
        ImportViewModel.State.FilePicker -> {
            LaunchedEffect(state) {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
        ImportViewModel.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        ImportViewModel.State.CategoriesReview -> categoriesReview.AttachWithView()
        ImportViewModel.State.AccountsReview -> accountsReview.AttachWithView()
        ImportViewModel.State.TransactionsPreview -> transactionsPreview.AttachWithView()
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt
git -C /Users/google-mac/Projects/zero commit -m "fix: intercept hardware back in import flow"
```

---

## Task 3: Add `icon: Image?` to `ImportAccount`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt`

- [ ] **Step 1: Add `icon` field**

```kotlin
data class ImportAccount(
    val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
    val transactionCount: Int,
    val icon: Image? = null,
)
```

The field is nullable with a default of `null` so existing callsites compile without changes — they get `null` (generic icon) until the use case starts passing a real value.

Add the import at the top:

```kotlin
import com.hluhovskyi.zero.common.Image
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt
git -C /Users/google-mac/Projects/zero commit -m "feat: add optional icon field to ImportAccount"
```

---

## Task 4: Name-merge logic in `DefaultImportUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

This is the core change. The plan:
1. Add `CategoryRepository` and `AccountRepository` constructor params.
2. Add lookup maps to `InternalState`.
3. Pre-fetch all three maps (existing categories, existing accounts, all icons) inside the `SelectFile` coroutine; icons are already fetched inside `buildCategories()` — move that fetch one level up so accounts can share it.
4. Update `buildCategories()` to accept and use the pre-fetched maps.
5. Add `buildAccountIcon()` that resolves the icon for an account using the lookup maps.
6. Update all three `ImportAccount` construction sites to call `buildAccountIcon()`.
7. Make `ConfirmCategories` a coroutine launch (like `SelectFile`) so it can await the accounts icon lookup. Store the resulting `List<ImportAccount>` in `InternalState` so `ConfirmAccounts` and `Back` from `TransactionsPreview` can reuse it without re-querying.

- [ ] **Step 1: Add new constructor params and update `InternalState`**

Replace the top of the class through the `mutableState` declaration:

```kotlin
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository

internal class DefaultImportUseCase(
    private val parsers: List<SnapshotParser>,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val onImportFinishedHandler: OnImportFinishedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ImportUseCase {

    private data class InternalState(
        val selectedSource: Source? = null,
        val storedDelta: SyncSnapshot? = null,
        val storedCategories: List<ImportCategory>? = null,
        val storedAccounts: List<ImportAccount>? = null,
        val excludedCategoryIds: Set<Id.Known> = emptySet(),
        val existingCategoryByName: Map<String, CategoryRepository.Category> = emptyMap(),
        val existingAccountByName: Map<String, AccountRepository.Account> = emptyMap(),
        val allIconsById: Map<Id.Known, com.hluhovskyi.zero.icons.Icon> = emptyMap(),
        val screen: ImportUseCase.State,
    )

    private val mutableState = MutableStateFlow(
        InternalState(screen = ImportUseCase.State.SourceSelection(parsers.map { it.source })),
    )
    override val state: Flow<ImportUseCase.State> = mutableState.map { it.screen }
```

- [ ] **Step 2: Update `SelectFile` coroutine to pre-fetch lookups**

Replace the `SelectFile` action handler:

```kotlin
is ImportUseCase.Action.SelectFile -> coroutineScope.launch {
    mutableState.update { it.copy(screen = ImportUseCase.State.Loading) }
    val source = mutableState.value.selectedSource ?: return@launch
    val parser = parsers.first { it.source.key == source.key }
    val userId = currentUserRepository.query().first().id
    try {
        val snapshot = parser.parse(action.uri)
        val delta = syncEngine.delta(snapshot, userId)

        val allIcons = iconRepository.query(IconRepository.Criteria.All()).first()
        val allIconsById = allIcons.associateBy { it.id }

        val existingCategories = categoryRepository.query(CategoryRepository.Criteria.All()).first()
        val existingCategoryByName = existingCategories.associateBy { it.name.lowercase() }

        val existingAccounts = accountRepository.query(AccountRepository.Criteria.All()).first()
        val existingAccountByName = existingAccounts.associateBy { it.name.lowercase() }

        val categories = buildCategories(delta, existingCategoryByName, allIconsById)
        mutableState.update { current ->
            current.copy(
                storedDelta = delta,
                storedCategories = categories,
                excludedCategoryIds = emptySet(),
                existingCategoryByName = existingCategoryByName,
                existingAccountByName = existingAccountByName,
                allIconsById = allIconsById,
                screen = ImportUseCase.State.CategoriesReview(categories = categories),
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
```

- [ ] **Step 3: Make `ConfirmCategories` a coroutine; build and store `ImportAccount` list**

Replace the `ConfirmCategories` handler:

```kotlin
is ImportUseCase.Action.ConfirmCategories -> coroutineScope.launch {
    val current = mutableState.value
    val delta = current.storedDelta ?: return@launch
    val excluded = current.excludedCategoryIds
    val filteredDelta = if (excluded.isEmpty()) {
        delta
    } else {
        delta.copy(
            categories = delta.categories.filter { it.id !in excluded },
            transactions = delta.transactions.filter { transaction ->
                Id(transaction.categoryId) !in excluded
            },
        )
    }
    val accounts = buildAccounts(
        syncAccounts = filteredDelta.accounts,
        transactions = filteredDelta.transactions,
        existingAccountByName = current.existingAccountByName,
        allIconsById = current.allIconsById,
    )
    mutableState.update { cur ->
        cur.copy(
            storedDelta = filteredDelta,
            storedAccounts = accounts,
            screen = ImportUseCase.State.AccountsReview(accounts = accounts),
        )
    }
}
```

- [ ] **Step 4: Update `ConfirmAccounts` to reuse stored accounts**

Replace the `ConfirmAccounts` handler:

```kotlin
is ImportUseCase.Action.ConfirmAccounts -> mutableState.update { current ->
    val delta = current.storedDelta ?: return@update current
    val accounts = current.storedAccounts ?: return@update current
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
            accounts = accounts,
            categories = current.storedCategories ?: emptyList(),
        ),
    )
}
```

- [ ] **Step 5: Update `Back` from TransactionsPreview to reuse stored accounts**

In the `Back` handler, replace the `TransactionsPreview` branch:

```kotlin
is ImportUseCase.State.TransactionsPreview -> {
    val accounts = current.storedAccounts ?: return@update current
    current.copy(
        screen = ImportUseCase.State.AccountsReview(accounts = accounts),
    )
}
```

- [ ] **Step 6: Update `buildCategories()` to accept lookups and do name-matching**

Replace the `buildCategories` function:

```kotlin
private suspend fun buildCategories(
    delta: SyncSnapshot,
    existingCategoryByName: Map<String, CategoryRepository.Category>,
    allIconsById: Map<Id.Known, com.hluhovskyi.zero.icons.Icon>,
): List<ImportCategory> {
    val txCountByCategoryId = delta.transactions
        .mapNotNull { transaction -> transaction.categoryId?.let { Id.Known(it) } }
        .groupBy { it }
        .mapValues { it.value.size }
    return delta.categories.map { syncCategory ->
        val existingMatch = existingCategoryByName[syncCategory.name.lowercase()]
        val colorId = (existingMatch?.colorId as? Id.Known)
            ?: syncCategory.colorId?.let { Id.Known(it) }
            ?: ColorRepository.unknownCategoryColorId()
        val iconId = (existingMatch?.iconId as? Id.Known)
            ?: syncCategory.iconId?.let { Id.Known(it) }
            ?: IconRepository.unknownCategoryIconId()
        val icon = allIconsById[iconId] ?: com.hluhovskyi.zero.icons.Icon.empty()
        ImportCategory(
            id = syncCategory.id,
            name = syncCategory.name,
            colorScheme = colorRepository.schemeFor(colorId),
            icon = icon.image,
            transactionCount = txCountByCategoryId[syncCategory.id] ?: 0,
        )
    }
}
```

- [ ] **Step 7: Add `buildAccounts()` helper**

Add this private function below `buildCategories`:

```kotlin
private fun buildAccounts(
    syncAccounts: List<SyncAccount>,
    transactions: List<SyncTransaction>,
    existingAccountByName: Map<String, AccountRepository.Account>,
    allIconsById: Map<Id.Known, com.hluhovskyi.zero.icons.Icon>,
): List<ImportAccount> {
    val txByAccountId = transactions.groupBy { it.accountId }
    return syncAccounts.map { syncAccount ->
        val existingMatch = existingAccountByName[syncAccount.name.lowercase()]
        val icon = existingMatch?.iconId?.let { allIconsById[it]?.image }
        ImportAccount(
            id = syncAccount.id,
            name = syncAccount.name,
            currencyId = syncAccount.currencyId,
            transactionCount = txByAccountId[syncAccount.id]?.size ?: 0,
            icon = icon,
        )
    }
}
```

- [ ] **Step 8: Verify it compiles**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
git -C /Users/google-mac/Projects/zero commit -m "feat: name-match categories and accounts against existing data during import"
```

---

## Task 5: Wire new dependencies in `ImportComponent`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt`

- [ ] **Step 1: Add repositories to `Dependencies` and pass them to the use case**

Add to imports:

```kotlin
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
```

Update `Dependencies` interface:

```kotlin
interface Dependencies {
    val syncEngine: SyncEngine
    val currentUserRepository: CurrentUserRepository
    val iconRepository: IconRepository
    val colorRepository: ColorRepository
    val categoryRepository: CategoryRepository
    val accountRepository: AccountRepository
    val imageLoader: ImageLoader
    val amountFormatter: AmountFormatter
    val dateFormatter: DateFormatter
}
```

Update `Module.useCase()` to pass the new repos:

```kotlin
@Provides
@ImportScope
fun useCase(
    parsers: List<SnapshotParser>,
    syncEngine: SyncEngine,
    currentUserRepository: CurrentUserRepository,
    iconRepository: IconRepository,
    colorRepository: ColorRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    onImportFinishedHandler: OnImportFinishedHandler,
): ImportUseCase = DefaultImportUseCase(
    parsers = parsers,
    syncEngine = syncEngine,
    currentUserRepository = currentUserRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    categoryRepository = categoryRepository,
    accountRepository = accountRepository,
    onImportFinishedHandler = onImportFinishedHandler,
)
```

Update `Module.accountsReviewComponentBuilder()` to pass `imageLoader`:

```kotlin
@Provides
@ImportScope
internal fun accountsReviewComponentBuilder(
    component: ImportComponent,
    importUseCase: ImportUseCase,
    imageLoader: ImageLoader,
): AccountsReviewComponent.Builder = AccountsReviewComponent.builder(component)
    .importUseCase(importUseCase)
    .imageLoader(imageLoader)
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt
git -C /Users/google-mac/Projects/zero commit -m "feat: expose CategoryRepository and AccountRepository in ImportComponent.Dependencies"
```

---

## Task 6: Add `imageLoader` to `AccountsReviewComponent`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewComponent.kt`

- [ ] **Step 1: Update component to wire `imageLoader`**

Add import:

```kotlin
import com.hluhovskyi.zero.ImageLoader
```

Replace the full file content:

```kotlin
package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.ImageLoader
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
        fun builder(dependencies: Dependencies): Builder = DaggerAccountsReviewComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
            .imageLoader(ImageLoader.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountsReviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder

        @BindsInstance
        fun imageLoader(imageLoader: ImageLoader): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountsReviewScope
        fun viewModel(importUseCase: ImportUseCase): AccountsReviewViewModel =
            DefaultAccountsReviewViewModel(importUseCase = importUseCase)

        @Provides
        @AccountsReviewScope
        fun viewProvider(
            viewModel: AccountsReviewViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = AccountsReviewViewProvider(viewModel = viewModel, imageLoader = imageLoader)
    }
}
```

> Note: `ImageLoader.Noop` — check if this exists. If not, use a lambda `imageLoader = ImageLoader { _, _, _ -> }` or check what other components use as a no-op. Look at `CategoriesReviewComponent` for the pattern.

- [ ] **Step 2: Check `ImageLoader.Noop` exists**

```bash
grep -rn "Noop\|object Noop" /Users/google-mac/Projects/zero/zero-api/src/main/java/com/hluhovskyi/zero/ImageLoader.kt 2>/dev/null || grep -rn "interface ImageLoader" /Users/google-mac/Projects/zero --include="*.kt" | grep -v ".worktrees" | grep -v "build/"
```

If `ImageLoader.Noop` doesn't exist, use the default passed from `ImportComponent` (which always has a real one) — the builder default in `companion object` is only for test convenience. Use `ImageLoader { _, _, _ -> }` as the default if needed.

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewComponent.kt
git -C /Users/google-mac/Projects/zero commit -m "feat: wire imageLoader into AccountsReviewComponent"
```

---

## Task 7: Render account icon in `AccountsReviewViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt`

- [ ] **Step 1: Update `AccountsReviewViewProvider` and `AccountRow`**

Replace the full file:

```kotlin
package com.hluhovskyi.zero.imports.accountsreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportAccount
import com.hluhovskyi.zero.ui.ImportStepHeader
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class AccountsReviewViewProvider(
    private val viewModel: AccountsReviewViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountsReviewView(viewModel = viewModel, imageLoader = imageLoader)
    }
}

@Composable
private fun AccountsReviewView(viewModel: AccountsReviewViewModel, imageLoader: ImageLoader) {
    val state by viewModel.state.collectAsState(initial = AccountsReviewViewModel.State())

    val totalTransactions = state.accounts.sumOf { it.transactionCount }
    Column(modifier = Modifier.fillMaxSize()) {
        ImportStepHeader(
            title = "Review Accounts",
            step = 2,
            totalSteps = 4,
            onBack = { viewModel.perform(AccountsReviewViewModel.Action.Back) },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = "${state.accounts.size} ACCOUNTS · $totalTransactions TRANSACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            items(state.accounts, key = { it.id.value }) { account ->
                AccountRow(account = account, imageLoader = imageLoader)
            }
            item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer)
                    .clickable { viewModel.perform(AccountsReviewViewModel.Action.Next) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun AccountRow(account: ImportAccount, imageLoader: ImageLoader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SurfaceContainer, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = account.icon
            if (icon != null) {
                imageLoader.View(
                    image = icon,
                    modifier = Modifier.size(24.dp),
                    tint = OnSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
            Text(text = account.currencyId.value, fontSize = 12.sp, color = OnSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "${account.transactionCount} tx",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt
git -C /Users/google-mac/Projects/zero commit -m "feat: render matched account icon in accounts review screen"
```

---

## Task 8: Update tests for `DefaultImportUseCase`

**Files:**
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt`

- [ ] **Step 1: Add mock repos and update `createUseCase`**

Add imports and mock fields:

```kotlin
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import kotlinx.coroutines.flow.flowOf

// in class body:
@Mock private lateinit var categoryRepository: CategoryRepository
@Mock private lateinit var accountRepository: AccountRepository
```

In `setUp()`, add stubs so the lookups don't crash on empty results:

```kotlin
@Before
fun setUp() {
    whenever(parser.source).thenReturn(source)
    whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
    whenever(categoryRepository.query(CategoryRepository.Criteria.All())).thenReturn(flowOf(emptyList()))
    whenever(accountRepository.query(AccountRepository.Criteria.All())).thenReturn(flowOf(emptyList()))
}
```

Update `createUseCase`:

```kotlin
private fun createUseCase(scope: CoroutineScope) = DefaultImportUseCase(
    parsers = listOf(parser),
    syncEngine = syncEngine,
    currentUserRepository = currentUserRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    categoryRepository = categoryRepository,
    accountRepository = accountRepository,
    onImportFinishedHandler = OnImportFinishedHandler.Noop,
    coroutineScope = scope,
)
```

- [ ] **Step 2: Run existing tests to confirm they still pass**

```bash
./gradlew :zero-core:test --tests "com.hluhovskyi.zero.imports.DefaultImportUseCaseTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 3: Add name-merge tests**

Add these tests after the existing ones:

```kotlin
@Test
fun `buildCategories uses existing category icon and color when name matches`() = runTest {
    val existingCategoryId = Id.Known("existing-cat-1")
    val existingIconId = Id.Known("icon-999")
    val existingColorId = Id.Known("color-999")

    val existingCategory = CategoryRepository.Category(
        id = existingCategoryId,
        parentCategoryId = Id.Unknown,
        name = "Food",
        iconId = existingIconId,
        colorId = existingColorId,
    )
    whenever(categoryRepository.query(CategoryRepository.Criteria.All()))
        .thenReturn(flowOf(listOf(existingCategory)))

    val importedIcon = com.hluhovskyi.zero.icons.Icon(
        id = existingIconId,
        image = com.hluhovskyi.zero.common.Image(
            uri = com.hluhovskyi.zero.common.Uri("file://icon.png") as com.hluhovskyi.zero.common.Uri.NonEmpty,
            description = "food icon",
        ),
    )
    whenever(iconRepository.query(IconRepository.Criteria.All()))
        .thenReturn(flowOf(listOf(importedIcon)))

    val syncCategory = com.hluhovskyi.zero.sync.SyncCategory(
        id = Id.Known("import-cat-1"),
        name = "food",
        iconId = "icon-000",
        colorId = "color-000",
        parentCategoryId = null,
        creationDateTime = kotlinx.datetime.LocalDateTime(2024, 1, 1, 0, 0),
        updatedDateTime = kotlinx.datetime.LocalDateTime(2024, 1, 1, 0, 0),
        deletedAt = null,
    )
    val snapshot = com.hluhovskyi.zero.sync.SyncSnapshot(
        version = 1,
        userId = userId,
        exportedAt = kotlinx.datetime.LocalDateTime(2024, 1, 1, 0, 0),
        categories = listOf(syncCategory),
        accounts = emptyList(),
        transactions = emptyList(),
    )
    val delta = snapshot
    whenever(syncEngine.delta(snapshot, userId)).thenReturn(delta)
    whenever(parser.parse(testUri)).thenReturn(snapshot)
    whenever(colorRepository.schemeFor(existingColorId))
        .thenReturn(com.hluhovskyi.zero.colors.ColorScheme.default())

    val useCase = createUseCase(this)
    useCase.perform(ImportUseCase.Action.SelectSource(source))
    useCase.perform(ImportUseCase.Action.SelectFile(testUri))
    advanceUntilIdle()

    val state = useCase.state.first()
    assert(state is ImportUseCase.State.CategoriesReview) { "Expected CategoriesReview but got $state" }
    val categories = (state as ImportUseCase.State.CategoriesReview).categories
    assert(categories.size == 1)
    // The icon image should be the matched one (from existingIconId), not the imported default
    assert(categories[0].icon == importedIcon.image) {
        "Expected matched icon image but got ${categories[0].icon}"
    }
}
```

> **Note:** `ColorScheme.default()` — check what factory/companion exists on `ColorScheme`. If none, mock `colorRepository.schemeFor(existingColorId)` to return whatever `colorRepository.schemeFor(Id.Known("color-000"))` would return (just verify it's called with the existing colorId, not the imported one). Use `verify(colorRepository).schemeFor(existingColorId)` instead.

- [ ] **Step 4: Run the new test**

```bash
./gradlew :zero-core:test --tests "com.hluhovskyi.zero.imports.DefaultImportUseCaseTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/google-mac/Projects/zero add zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt
git -C /Users/google-mac/Projects/zero commit -m "test: add name-merge test for DefaultImportUseCase"
```

---

## Task 9: Full build verification

- [ ] **Step 1: Run lint**

```bash
./gradlew lintDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` with 0 errors.

- [ ] **Step 2: Run all zero-core tests**

```bash
./gradlew :zero-core:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Full debug assemble**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 10: Create PR

- [ ] **Step 1: Push branch**

```bash
git -C /Users/google-mac/Projects/zero push -u origin feat/import-back-and-name-merge
```

- [ ] **Step 2: Create PR**

```bash
gh pr create --title "feat: fix import back button + name-match icon/color merge" --body "$(cat <<'EOF'
## Summary
- Hardware back press on import steps 1–3 (Categories, Accounts, Transactions) now navigates within the flow instead of closing it
- When importing categories, if a category with the same name already exists, its icon and color scheme are used in the preview
- When importing accounts, if an account with the same name already exists, its icon is used in the review screen
- `AccountsReviewViewProvider` now accepts `ImageLoader` and renders matched account icons

## Test plan
- [ ] Import a file; on Categories screen, press hardware back → goes to Source Selection
- [ ] On Accounts screen, press hardware back → goes to Categories screen
- [ ] On Transactions screen, press hardware back → goes to Accounts screen
- [ ] On Source Selection screen, press hardware back → closes import entirely
- [ ] Import data containing a category/account name that exists in the app → matching icon/color appears in the review UI
- [ ] Import data with all-new names → generic icon/color shown (no regression)
- [ ] Run `:zero-core:test` → all tests pass

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Fix 1 (BackHandler) — Task 2
- [x] Fix 2 categories icon/color merge — Tasks 3, 4 (`buildCategories` update)
- [x] Fix 2 accounts icon merge — Tasks 3, 4 (`buildAccounts`), 5, 6, 7
- [x] `ImportComponent.Dependencies` updated — Task 5
- [x] `AccountsReviewComponent` imageLoader wired — Task 6
- [x] `AccountsReviewViewProvider` renders icon — Task 7
- [x] Tests updated — Task 8

**Type consistency:**
- `buildCategories(delta, existingCategoryByName, allIconsById)` — defined in Task 4 step 6, called in Task 4 step 2 ✓
- `buildAccounts(syncAccounts, transactions, existingAccountByName, allIconsById)` — defined in Task 4 step 7, called in Task 4 step 3 ✓
- `ImportAccount.icon: Image?` — defined in Task 3, used in Tasks 4 and 7 ✓
- `imageLoader: ImageLoader` in `AccountsReviewViewProvider` — set in Task 6, used in Task 7 ✓

**Placeholder scan:** No TBDs. The `ColorScheme.default()` note in Task 8 step 3 gives the fallback strategy explicitly.
