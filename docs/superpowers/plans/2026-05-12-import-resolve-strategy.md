# Import Resolve Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the binary include/exclude toggle on the Categories and Accounts import-review screens with a three-way per-item resolution strategy (Merge / New / Skip), driven by whether the imported entity already exists in the DB (id match or case-insensitive name match).

**Architecture:**
- Domain enum `ResolveStrategy` lives in `zero-api`.
- `ImportCategory`/`ImportAccount` gain an `existingId: Id.Known?` field (set when an existing DB entity matches by id or name).
- `ImportUseCase.State.CategoriesReview` and `State.AccountsReview` carry `strategies: Map<Id.Known, ResolveStrategy>`. Action `SetCategoryStrategy` / `SetAccountStrategy` replace `ToggleCategory`.
- On `ConfirmCategories` / `ConfirmAccounts`, strategies are applied to the in-flight `SyncSnapshot`: **Merge** drops the imported entity and remaps its id to the existing id everywhere transactions reference it; **Skip** drops the entity and every transaction that references it; **New** leaves it untouched.
- UI: each row gets a chip showing the current strategy; tapping it opens a dropdown with the eligible options. Skipped rows dim. Existing rows render an `EXISTS` badge.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Coroutines/Flow, JUnit/Mockito.

---

## File Structure

**Create**
- `zero-api/src/main/java/com/hluhovskyi/zero/imports/ResolveStrategy.kt`
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportStrategyChip.kt`

**Modify**
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt` — add `existingId`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt` — new state field, new actions
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt` — strategy application
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewModel.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/DefaultCategoriesReviewViewModel.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewModel.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/DefaultAccountsReviewViewModel.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt`
- `zero-core/src/main/res/values/strings.xml`
- `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt`

---

## Task 1: Add ResolveStrategy enum

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/imports/ResolveStrategy.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.hluhovskyi.zero.imports

enum class ResolveStrategy {
    Merge,
    New,
    Skip,
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :zero-api:assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/imports/ResolveStrategy.kt
git commit -m "feat(import): add ResolveStrategy enum"
git push
```

---

## Task 2: Add existingId to ImportCategory / ImportAccount

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt`

- [ ] **Step 1: Add field**

Add `val existingId: Id.Known? = null` as the last param of both `ImportCategory` and `ImportAccount`.

- [ ] **Step 2: Build**

Run: `./gradlew :zero-core:assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportDisplayModels.kt
git commit -m "feat(import): add existingId to display models"
git push
```

---

## Task 3: Populate existingId in DefaultImportUseCase

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

- [ ] **Step 1: Set existingId from id OR name match in `buildCategories` and `buildAccounts`**

`existingId` must be set whenever a local entity matches either way. Both branches lead to the Merge route (see Task 5 default-strategy logic).

1. **Id match (primary)** — imported `id` is already present in the local DB. Build a `Map<Id.Known, …>` keyed by local id and look up `syncCategory.id` / `syncAccount.id` in it.
2. **Name match (fallback)** — case-insensitive name overlap, using the existing `existingCategoryByName` / `existingAccountByName` maps. Only consult this when the id lookup misses.

Pseudocode for `buildCategories`:

```kotlin
val existingById = existingCategories.associateBy { it.id as Id.Known }
…
val existingMatch = existingById[syncCategory.id]
    ?: existingCategoryByName[syncCategory.name.lowercase()]
val existingId = existingMatch?.id as? Id.Known
```

Same pattern in `buildAccounts`.

Rationale: id-level match catches "previously-imported entity that the user later renamed locally" (delta carries the import's old name, local has a new one — id is the only signal). Name match catches the cross-source / first-import case where ids differ but the user already has a similarly-named entity. Either way, the merge target id is the **local** entity's id, which is what the strategy remap in Task 5 uses.

- [ ] **Step 2: Failing test**

Add to `DefaultImportUseCaseTest.kt`:

```kotlin
@Test
fun `buildCategories sets existingId when name matches existing category`() = runTest {
    val existing = CategoryRepository.Category(
        id = Id.Known("existing-1"),
        parentCategoryId = Id.Unknown,
        name = "Food",
        iconId = Id.Known("icon-1"),
        colorId = Id.Known("color-1"),
    )
    whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
        .thenReturn(flowOf(listOf(existing)))
    whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)

    val syncCategory = SyncCategory(
        id = Id.Known("import-1"),
        name = "food",
        iconId = null,
        colorId = null,
        parentCategoryId = null,
        creationDateTime = LocalDateTime(2024, 1, 1, 0, 0),
        updatedDateTime = LocalDateTime(2024, 1, 1, 0, 0),
        deletedAt = null,
    )
    val snapshot = SyncSnapshot(1, userId, LocalDateTime(2024, 1, 1, 0, 0),
        categories = listOf(syncCategory), accounts = emptyList(), transactions = emptyList())
    whenever(parser.parse(testUri)).thenReturn(snapshot)
    whenever(syncEngine.delta(snapshot, userId)).thenReturn(snapshot)

    val useCase = createUseCase(this)
    useCase.perform(ImportUseCase.Action.SelectSource(source))
    useCase.perform(ImportUseCase.Action.SelectFile(testUri))
    advanceUntilIdle()

    val state = useCase.state.first() as ImportUseCase.State.CategoriesReview
    assert(state.categories[0].existingId == Id.Known("existing-1"))
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultImportUseCaseTest.buildCategories sets existingId*"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt
git commit -m "feat(import): populate existingId for categories and accounts"
git push
```

---

## Task 4: Replace excludedIds with strategies in ImportUseCase

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt`

- [ ] **Step 1: Update state and actions**

```kotlin
sealed interface Action {
    data class SelectSource(val source: Source) : Action
    data class SelectFile(val uri: Uri.NonEmpty) : Action
    data class SetCategoryStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
    data class SetAccountStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
    object ConfirmCategories : Action
    object ConfirmAccounts : Action
    object Confirm : Action
    object Back : Action
    object DismissError : Action
    object Retry : Action
}

sealed interface State {
    // …unchanged states…
    data class CategoriesReview(
        val categories: List<ImportCategory>,
        val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    ) : State
    data class AccountsReview(
        val accounts: List<ImportAccount>,
        val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    ) : State
    // …rest unchanged…
}
```

Delete `ToggleCategory` action.

- [ ] **Step 2: Verify**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: Compile errors in `DefaultImportUseCase.kt` and the review view models — those get fixed in subsequent tasks.

---

## Task 5: Apply strategies in DefaultImportUseCase

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

- [ ] **Step 1: Replace `excludedCategoryIds` in `InternalState`**

```kotlin
private data class InternalState(
    val selectedSource: Source? = null,
    val storedDelta: SyncSnapshot? = null,
    val storedCategories: List<ImportCategory>? = null,
    val storedAccounts: List<ImportAccount>? = null,
    val categoryStrategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    val accountStrategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    val existingCategoryByName: Map<String, CategoryRepository.Category> = emptyMap(),
    val existingAccountByName: Map<String, AccountRepository.Account> = emptyMap(),
    val allIconsById: Map<Id.Known, Icon> = emptyMap(),
    val screen: ImportUseCase.State,
)
```

- [ ] **Step 2: Initialize default category strategies after building categories**

In the `SelectFile` flow, after building `categories`:

```kotlin
val defaultStrategies = categories.associate { cat ->
    cat.id to if (cat.existingId != null) ResolveStrategy.Merge else ResolveStrategy.New
}
```

Pass to the `CategoriesReview` state and store in `categoryStrategies`.

- [ ] **Step 3: Replace `ToggleCategory` handler with `SetCategoryStrategy`/`SetAccountStrategy` handlers**

```kotlin
is ImportUseCase.Action.SetCategoryStrategy -> mutableState.update { current ->
    val categories = current.storedCategories ?: return@update current
    val newStrategies = current.categoryStrategies + (action.id to action.strategy)
    current.copy(
        categoryStrategies = newStrategies,
        screen = ImportUseCase.State.CategoriesReview(categories, newStrategies),
    )
}
is ImportUseCase.Action.SetAccountStrategy -> mutableState.update { current ->
    val accounts = current.storedAccounts ?: return@update current
    val newStrategies = current.accountStrategies + (action.id to action.strategy)
    current.copy(
        accountStrategies = newStrategies,
        screen = ImportUseCase.State.AccountsReview(accounts, newStrategies),
    )
}
```

- [ ] **Step 4: Apply strategies in `ConfirmCategories`**

Replace the existing exclusion logic with strategy application: build `categoryMerges` from `existingId` of categories whose strategy is `Merge`; collect `skippedCategoryIds`. Filter `delta.categories` to drop skipped + merged. Map `delta.transactions` to drop those with `categoryId in skippedCategoryIds` and remap `categoryId` via `categoryMerges`. Then build accounts from the resulting delta and seed default account strategies.

```kotlin
is ImportUseCase.Action.ConfirmCategories -> coroutineScope.launch {
    val current = mutableState.value
    val delta = current.storedDelta ?: return@launch
    val categories = current.storedCategories ?: return@launch
    val strategies = current.categoryStrategies

    val merges = categories
        .filter { strategies[it.id] == ResolveStrategy.Merge && it.existingId != null }
        .associate { it.id to it.existingId!! }
    val skipped = strategies.filterValues { it == ResolveStrategy.Skip }.keys

    val filteredCategories = delta.categories.filter {
        it.id !in skipped && it.id !in merges.keys
    }
    val filteredTransactions = delta.transactions.mapNotNull { tx ->
        val categoryId = tx.categoryId?.let { Id.Known(it) }
        if (categoryId in skipped) return@mapNotNull null
        val newCategoryId = categoryId?.let { merges[it]?.value ?: it.value }
        tx.copy(categoryId = newCategoryId)
    }
    val filteredDelta = delta.copy(
        categories = filteredCategories,
        transactions = filteredTransactions,
    )

    val accounts = buildAccounts(
        syncAccounts = filteredDelta.accounts,
        transactions = filteredDelta.transactions,
        existingAccountByName = current.existingAccountByName,
        allIconsById = current.allIconsById,
    )
    val defaultAccountStrategies = accounts.associate { acc ->
        acc.id to if (acc.existingId != null) ResolveStrategy.Merge else ResolveStrategy.New
    }

    mutableState.update { cur ->
        cur.copy(
            storedDelta = filteredDelta,
            storedAccounts = accounts,
            accountStrategies = defaultAccountStrategies,
            screen = ImportUseCase.State.AccountsReview(
                accounts = accounts,
                strategies = defaultAccountStrategies,
            ),
        )
    }
}
```

- [ ] **Step 5: Apply strategies in `ConfirmAccounts`**

Before mapping transactions to `ImportTransaction`, drop transactions referencing skipped accounts (either `accountId` or `targetAccountId`) and remap `accountId`/`targetAccountId` via merged-account ids.

```kotlin
is ImportUseCase.Action.ConfirmAccounts -> mutableState.update { current ->
    val delta = current.storedDelta ?: return@update current
    val accounts = current.storedAccounts ?: return@update current
    val strategies = current.accountStrategies

    val merges = accounts
        .filter { strategies[it.id] == ResolveStrategy.Merge && it.existingId != null }
        .associate { it.id to it.existingId!! }
    val skipped = strategies.filterValues { it == ResolveStrategy.Skip }.keys

    val filteredAccounts = delta.accounts.filter {
        it.id !in skipped && it.id !in merges.keys
    }
    val filteredTransactions = delta.transactions.mapNotNull { tx ->
        if (tx.accountId in skipped) return@mapNotNull null
        val targetId = tx.targetAccountId?.let { Id.Known(it) }
        if (targetId != null && targetId in skipped) return@mapNotNull null
        val newAccountId = merges[tx.accountId] ?: tx.accountId
        val newTargetId = targetId?.let { merges[it]?.value ?: it.value }
        tx.copy(accountId = newAccountId, targetAccountId = newTargetId)
    }
    val filteredDelta = delta.copy(
        accounts = filteredAccounts,
        transactions = filteredTransactions,
    )

    val categoryById = filteredDelta.categories.associateBy { it.id }
    val transactions = filteredDelta.transactions.map { syncTx ->
        // …existing ImportTransaction mapping, unchanged…
    }
    current.copy(
        storedDelta = filteredDelta,
        screen = ImportUseCase.State.TransactionsPreview(
            transactions = transactions,
            totalCount = transactions.size,
            accounts = current.storedAccounts ?: emptyList(),
            categories = current.storedCategories ?: emptyList(),
        ),
    )
}
```

- [ ] **Step 6: Update `Back` handler**

For `CategoriesReview`/`AccountsReview`, preserve `categoryStrategies` / `accountStrategies` when rebuilding the screen.

- [ ] **Step 7: Build**

Run: `./gradlew :zero-core:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportUseCase.kt zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt
git commit -m "feat(import): apply resolve strategies in use case"
git push
```

---

## Task 6: Update CategoriesReview view model

**Files:**
- Modify: `zero-core/.../categoriesreview/CategoriesReviewViewModel.kt`
- Modify: `zero-core/.../categoriesreview/DefaultCategoriesReviewViewModel.kt`

- [ ] **Step 1: New state + action**

```kotlin
interface CategoriesReviewViewModel : ActionStateModel<CategoriesReviewViewModel.Action, CategoriesReviewViewModel.State> {
    data class State(
        val categories: List<ImportCategory> = emptyList(),
        val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    )
    sealed interface Action {
        object Next : Action
        object Back : Action
        data class SetStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
    }
}
```

- [ ] **Step 2: Wire through default impl**

```kotlin
override val state: Flow<CategoriesReviewViewModel.State> = importUseCase.state
    .filterIsInstance<ImportUseCase.State.CategoriesReview>()
    .map { CategoriesReviewViewModel.State(it.categories, it.strategies) }

override fun perform(action: CategoriesReviewViewModel.Action) {
    when (action) {
        is CategoriesReviewViewModel.Action.Next ->
            importUseCase.perform(ImportUseCase.Action.ConfirmCategories)
        is CategoriesReviewViewModel.Action.Back ->
            importUseCase.perform(ImportUseCase.Action.Back)
        is CategoriesReviewViewModel.Action.SetStrategy ->
            importUseCase.perform(ImportUseCase.Action.SetCategoryStrategy(action.id, action.strategy))
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (modulo unrelated view-provider compile failure — fixed in Task 8).

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/
git commit -m "feat(import): wire strategies into categories review view model"
git push
```

---

## Task 7: Update AccountsReview view model

**Files:**
- Modify: `zero-core/.../accountsreview/AccountsReviewViewModel.kt`
- Modify: `zero-core/.../accountsreview/DefaultAccountsReviewViewModel.kt`

- [ ] **Step 1: New state + action**

Mirror Task 6 for accounts: state holds `accounts` + `strategies`, action `SetStrategy(id, strategy)`, default impl maps state from `ImportUseCase.State.AccountsReview` and delegates `SetStrategy` to `ImportUseCase.Action.SetAccountStrategy`.

- [ ] **Step 2: Build**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (modulo view-provider failure).

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewModel.kt zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/DefaultAccountsReviewViewModel.kt
git commit -m "feat(import): wire strategies into accounts review view model"
git push
```

---

## Task 8: Add ImportStrategyChip Compose component

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportStrategyChip.kt`

- [ ] **Step 1: Implement the chip + dropdown**

A composable that takes `selected: ResolveStrategy`, `options: List<ResolveStrategy>`, `onChange: (ResolveStrategy) -> Unit`. Each option has a color label per the design (Merge → primaryContainer family `#E8EEFF`/`PrimaryContainer`; New → green family `#E8F5E9`/`Secondary`; Skip → `SurfaceContainer`/`OnSurfaceVariant`). Use `DropdownMenu` (Material) for the popup. Color/label/description strings come from `R.string.import_resolve_strategy_*` (added in Task 11).

Composition outline:
- `Box` wrapping the chip `Row` + `DropdownMenu`
- Chip: `Row` with rounded shape (20dp), padding 5/10/5/8 dp, colored dot 7dp, label text 12sp Bold, caret icon
- Dropdown item: `Row` 9/12 dp padding, 8dp dot, label 12sp Bold, description 11sp, optional check icon, divider between items via 1dp border on top.

Use existing theme tokens from `zero-ui/.../theme/`. Note: `Merge` background `#E8EEFF` and `New` background `#E8F5E9` do not exist in the palette — define them as local `private val` `Color` values in this file. Foreground colors are `PrimaryContainer` and `Secondary` respectively (both already in theme).

- [ ] **Step 2: Build**

Run: `./gradlew :zero-ui:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/ImportStrategyChip.kt
git commit -m "feat(ui): add ImportStrategyChip composable"
git push
```

---

## Task 9: Rewrite CategoriesReviewViewProvider

**Files:**
- Modify: `zero-core/.../categoriesreview/CategoriesReviewViewProvider.kt`

- [ ] **Step 1: Replace the toggle row with chip row**

Each row: icon | column(name + EXISTS badge, subtitle: "X transactions" or "Won't be imported") | `ImportStrategyChip`. Apply `alpha(0.4f)` on the whole row when the strategy is `Skip`.

Option set per row:
```kotlin
val options = if (category.existingId != null) listOf(ResolveStrategy.Merge, ResolveStrategy.New, ResolveStrategy.Skip)
              else listOf(ResolveStrategy.New, ResolveStrategy.Skip)
```

Default strategy if missing in the map: `if (category.existingId != null) Merge else New` (defensive — the use case should always populate it).

`onClick` for the existing toggle goes away (the chip handles selection); the row itself is not clickable.

Update the header strings from `selectedCount` to total category count (`state.categories.size`).

- [ ] **Step 2: Build**

Run: `./gradlew :zero-core:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/categoriesreview/CategoriesReviewViewProvider.kt
git commit -m "feat(import): chip-based categories review UI"
git push
```

---

## Task 10: Rewrite AccountsReviewViewProvider

**Files:**
- Modify: `zero-core/.../accountsreview/AccountsReviewViewProvider.kt`

- [ ] **Step 1: Add chip row mirroring Task 9**

Apply the same chip + EXISTS badge + skip dimming treatment to account rows. Drop the `tx` pill from the right side when the chip occupies that position; show the transaction count below the account name instead (or remove it — match the categories row treatment for consistency: `"X transactions"` subtitle for non-skipped, `"Won't be imported"` for skipped).

- [ ] **Step 2: Build**

Run: `./gradlew :zero-core:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt
git commit -m "feat(import): chip-based accounts review UI"
git push
```

---

## Task 11: Add strings

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Add resources**

```xml
<string name="import_resolve_strategy_merge">Merge</string>
<string name="import_resolve_strategy_new">New</string>
<string name="import_resolve_strategy_skip">Skip</string>
<string name="import_resolve_strategy_merge_desc">Combine with existing</string>
<string name="import_resolve_strategy_new_desc">Create separate entity</string>
<string name="import_resolve_strategy_skip_desc">Don\'t import</string>
<string name="import_resolve_exists_badge">EXISTS</string>
<string name="import_resolve_wont_be_imported">Won\'t be imported</string>
```

Reuse `import_categories_review_*` strings; `import_categories_review_info` becomes the total count ("%1$d CATEGORIES" is fine — drop the "selected" framing).

- [ ] **Step 2: Build**

Run: `./gradlew :zero-core:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/res/values/strings.xml
git commit -m "feat(import): strings for resolve strategy UI"
git push
```

---

## Task 12: Use-case tests for strategies

**Files:**
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt`

- [ ] **Step 1: Replace `ToggleCategory` test cases with strategy tests**

Add and run these tests (each one boots `useCase`, lands on `CategoriesReview`, sets strategies, calls `ConfirmCategories`, asserts on `AccountsReview` / on the in-flight delta as exposed via `TransactionsPreview` after `ConfirmAccounts`):

1. `ConfirmCategories with Merge remaps categoryId in transactions to existingId and drops imported category`
2. `ConfirmCategories with Skip removes category and transactions referencing it`
3. `ConfirmCategories with New keeps imported category unchanged`
4. `ConfirmAccounts with Merge remaps accountId and targetAccountId via existingId`
5. `ConfirmAccounts with Skip removes account and transactions referencing it (as source or target)`
6. `SetCategoryStrategy updates state without leaving CategoriesReview`
7. `Default category strategy is Merge when existingId is set, New otherwise`

Test pattern (one example, the rest are variations):

```kotlin
@Test
fun `ConfirmCategories with Merge remaps transactions to existingId`() = runTest {
    val existing = CategoryRepository.Category(
        id = Id.Known("existing-1"), parentCategoryId = Id.Unknown,
        name = "Food", iconId = Id.Known("i"), colorId = Id.Known("c"),
    )
    whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
        .thenReturn(flowOf(listOf(existing)))
    whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)

    val syncCategory = SyncCategory(
        id = Id.Known("import-1"), name = "food",
        iconId = null, colorId = null, parentCategoryId = null,
        creationDateTime = LocalDateTime(2024,1,1,0,0),
        updatedDateTime = LocalDateTime(2024,1,1,0,0), deletedAt = null,
    )
    val tx = SyncTransaction(
        id = Id.Known("t1"), type = SyncTransaction.Type.EXPENSE,
        accountId = Id.Known("a1"), currencyId = Id.Known("usd"),
        categoryId = "import-1", amount = "10", rate = "1",
        targetAccountId = null, targetAmount = null,
        enteredDateTime = LocalDateTime(2024,1,1,0,0),
        creationDateTime = LocalDateTime(2024,1,1,0,0),
        updatedDateTime = LocalDateTime(2024,1,1,0,0), deletedAt = null,
    )
    val snapshot = SyncSnapshot(1, userId, LocalDateTime(2024,1,1,0,0),
        listOf(syncCategory), emptyList(), listOf(tx))
    whenever(parser.parse(testUri)).thenReturn(snapshot)
    whenever(syncEngine.delta(snapshot, userId)).thenReturn(snapshot)

    val useCase = createUseCase(this)
    useCase.perform(ImportUseCase.Action.SelectSource(source))
    useCase.perform(ImportUseCase.Action.SelectFile(testUri))
    advanceUntilIdle()
    // Default strategy is already Merge because existingId is set
    useCase.perform(ImportUseCase.Action.ConfirmCategories)
    advanceUntilIdle()
    useCase.perform(ImportUseCase.Action.ConfirmAccounts)
    advanceUntilIdle()

    val state = useCase.state.first() as ImportUseCase.State.TransactionsPreview
    val expense = state.transactions.single() as ImportTransaction.Expense
    assert(expense.categoryId == Id.Known("existing-1"))
}
```

- [ ] **Step 2: Run the new tests**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultImportUseCaseTest*"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt
git commit -m "test(import): resolve strategy use case behavior"
git push
```

---

## Task 13: Verify full test suite + lint

- [ ] **Step 1: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint**

Run: `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20`
Expected: no errors

- [ ] **Step 3: UI inspection**

Install the app on the emulator, navigate `Settings → Import`, select a source, pick a backup file that has overlapping categories with the current data. Use `zero-project:android-ui-inspector` to dump the review screen UI and verify:
  - Each category row shows the strategy chip on the right
  - Existing categories show the EXISTS badge
  - Tapping the chip opens the dropdown with the correct option set (3 if exists, 2 otherwise)
  - Selecting Skip dims the row
  - Tapping Continue applies merge/new/skip correctly (assert via the next screen)
- [ ] **Step 4: Commit any fixes from inspection** (if needed)

```bash
git add -A
git commit -m "fix(import): UI tweaks from device inspection"
git push
```
