# Budget Phase 1 — Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Roadmap: [budget-roadmap.md](2026-05-13-budget-roadmap.md). Read it first for the data model and cross-cutting decisions.

**Goal:** Land the data layer, sync wiring, and a `BudgetComponent` skeleton wired to a new `Destinations.Budget` route. UI matches the design's "no budget for this month" empty state (per `BudgetScreen.jsx` lines 968–1080: dark callout card, `MonthSelector`, dashed-border "Set limit" rows). All buttons are no-ops or navigate to a stub — flow logic lands in later phases.

**Architecture:** New `BudgetRepository` + `BudgetQueryUseCase` in zero-api / zero-database / zero-core. `BudgetComponent` is a standard tab destination following the `AccountComponent` template. Sync wires `SyncBudget` through `zero-sync` so future-phase mutations replicate.

**Tech stack:** Kotlin, Jetpack Compose, Room v8 (bumped from 7), Dagger, kotlinx-coroutines, kotlinx-serialization (for sync).

---

## Files

### New — zero-api
- `zero-api/.../budget/BudgetRepository.kt` — repository contract + `Budget` model + `Criteria.ForPeriod` + `BudgetInsert`
- `zero-api/.../budget/BudgetType.kt` — `enum class BudgetType { EXPENSE, INCOME }` + `from(String)` companion
- `zero-api/.../budget/BudgetQueryUseCase.kt` — display-ready per-category model joining `BudgetRepository`, `CategoriesQueryUseCase`, `CategorySpendingUseCase`
- `zero-api/.../sync/SyncBudget.kt` — sync DTO, mirrors `SyncCategory`/`SyncAccount`

### New — zero-database
- `zero-database/.../budget/BudgetEntity.kt` — Room entity (see [Roadmap §Data Model](2026-05-13-budget-roadmap.md#data-model))
- `zero-database/.../budget/BudgetRoom.kt` — DAO with `selectByUserId`, `selectForPeriod`, `selectHasAnyForPeriod` (the `SELECT EXISTS` flow), `insert`
- `zero-database/.../budget/BudgetMigrations.kt` — `MIGRATION_7_8` adding `BudgetEntity` table
- `zero-database/.../budget/BudgetSyncDao.kt` — sync queries
- `zero-database/.../budget/RoomBudgetRepository.kt` — `BudgetRepository` impl
- `zero-database/.../budget/RoomBudgetSyncSource.kt` + `RoomBudgetSyncSink.kt`

### New — zero-sync
- `zero-sync/.../sync/` (extensions to existing serializer + pipeline to handle `SyncBudget`)

### New — zero-core
- `zero-core/.../budget/BudgetComponent.kt` (scaffold per `superpowers/scaffold-feature`)
- `zero-core/.../budget/BudgetViewModel.kt`, `DefaultBudgetViewModel.kt`, `BudgetViewProvider.kt`
- `zero-core/.../budget/DefaultBudgetQueryUseCase.kt`
- `zero-core/.../budget/PeriodResolver.kt` — converts `(year, month)` → `(LocalDate, LocalDate)` first/last-of-month. Cadence-neutral helper; the only place that hardcodes "month".

### Modified — zero-database
- `MainDatabase.kt` — bump `MAIN_DATABASE_VERSION = 8`, add `BudgetEntity::class` + `abstract fun budget(): BudgetRoom` + `abstract fun budgetSync(): BudgetSyncDao`, register `MIGRATION_7_8`
- `DatabaseComponent.kt` — provide `BudgetRepository`, `EntitySyncSource<SyncBudget>`, `EntitySyncSink<SyncBudget>`

### Modified — app
- `activity/navigation/Destinations.kt` — add `object Budget : Destination by destinationOf("budget")`
- `activity/screens/bottombar/DefaultBottomBarViewModel.kt` — uncomment the `Destinations.Budget.route -> budgetId` branch and the `budgetId -> Destinations.Budget` mapping (lines 92, 134)
- `activity/screens/MainActivityScreenComponent.kt` — add `budgetComponentBuilder: BudgetComponent.Builder` to `Dependencies`, add `budgetNavigationEntry(...)` `@IntoSet` provider following `accountNavigationEntry`
- `ApplicationComponent.kt` — provide `BudgetRepository`, `BudgetQueryUseCase` factory at `@ApplicationScope`; add to `ActivityComponent.Dependencies`
- `activity/ActivityComponent.kt` — add `budgetQueryUseCase: BudgetQueryUseCase` etc.
- `app/.../res/drawable/ic_budget_24.xml` — already exists, no change

---

## Task 1: Add `BudgetType` and `BudgetRepository` API

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/budget/BudgetType.kt`
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/budget/BudgetRepository.kt`

- [ ] **Step 1: Create `BudgetType` enum**

```kotlin
package com.hluhovskyi.zero.budget

enum class BudgetType {
    EXPENSE,
    INCOME;

    companion object {
        fun from(value: String): BudgetType = entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}
```

- [ ] **Step 2: Create `BudgetRepository` interface**

Model after `CategoryRepository.kt`. Public surface:

```kotlin
package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

interface BudgetRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    suspend fun insert(budget: BudgetInsert)
    suspend fun insert(budgets: List<BudgetInsert>)
    suspend fun delete(id: Id.Known)

    sealed interface Criteria<T> {
        class All : Criteria<List<Budget>>
        data class ForPeriod(val from: LocalDate, val to: LocalDate, val type: BudgetType = BudgetType.EXPENSE) : Criteria<List<Budget>>
        data class ForCategoryAndPeriod(val categoryId: Id.Known, val from: LocalDate, val to: LocalDate, val type: BudgetType = BudgetType.EXPENSE) : Criteria<Budget?>
        data class HasAnyForPeriod(val from: LocalDate, val to: LocalDate, val type: BudgetType = BudgetType.EXPENSE) : Criteria<Boolean>
    }

    data class Budget(
        val id: Id.Known,
        val categoryId: Id.Known,
        val type: BudgetType,
        val amount: Amount,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
    )

    data class BudgetInsert(
        val id: Id = Id.Unknown,
        val categoryId: Id.Known,
        val type: BudgetType,
        val amount: Amount,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
    )

    object Noop : BudgetRepository {
        override fun <T> query(criteria: Criteria<T>): Flow<T> = emptyFlow()
        override suspend fun insert(budget: BudgetInsert) = Unit
        override suspend fun insert(budgets: List<BudgetInsert>) = Unit
        override suspend fun delete(id: Id.Known) = Unit
    }
}
```

- [ ] **Step 3: Compile and commit**

Run: `./gradlew :zero-api:compileKotlin`

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/budget
git commit -m "feat(budget): add BudgetRepository and BudgetType API"
```

---

## Task 2: Add Room entity, DAO, migration

**Files:**
- Create: `zero-database/.../budget/BudgetEntity.kt`
- Create: `zero-database/.../budget/BudgetRoom.kt`
- Create: `zero-database/.../budget/BudgetMigrations.kt`
- Modify: `zero-database/.../MainDatabase.kt` lines 21–58

Follow [Data Layer](../agents/data-layer.md) — lazy DAO, `userId` filtering, soft-delete pattern, `SELECT EXISTS` for `HasAny*`. Use `CategoryEntity` + `CategoryRoom` as the structural template; use `TransactionRoom.selectHasAny` as the EXISTS template.

- [ ] **Step 1: Write `BudgetEntity`**

```kotlin
@Entity(indices = [Index("userId"), Index("categoryId")])
data class BudgetEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val categoryId: Id.Known,
    val type: String = BudgetType.EXPENSE.name,
    val amount: BigDecimal,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
)
```

Note: `LocalDate` needs a `TypeConverter`. Check `LocalDateTimeConverter.kt` — extend or add `LocalDateConverter` and register in `MainDatabase.@TypeConverters`.

- [ ] **Step 2: Write `BudgetRoom` DAO**

Queries needed (all filter `deletedAt IS NULL`):
- `selectByUserId(userId): Flow<List<BudgetEntity>>` — All alive.
- `selectForPeriod(userId, from, to, type): Flow<List<BudgetEntity>>` — exact period match (`periodStart=:from AND periodEnd=:to AND type=:type`).
- `selectForCategoryAndPeriod(userId, categoryId, from, to, type): Flow<BudgetEntity?>` — `LIMIT 1`.
- `selectHasAnyForPeriod(userId, from, to, type): Flow<Boolean>` — `SELECT EXISTS(... LIMIT 1)`.
- `insert(BudgetEntity)` + `insert(List<BudgetEntity>)` — `REPLACE`.
- `softDelete(id, userId, updatedDateTime)` — `UPDATE BudgetEntity SET deletedAt=:dt, updatedDateTime=:dt WHERE id=:id AND userId=:userId`.

- [ ] **Step 3: Write `MIGRATION_7_8`**

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS BudgetEntity (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "userId TEXT NOT NULL, " +
                "categoryId TEXT NOT NULL, " +
                "type TEXT NOT NULL DEFAULT 'EXPENSE', " +
                "amount TEXT NOT NULL, " +
                "periodStart TEXT NOT NULL, " +
                "periodEnd TEXT NOT NULL, " +
                "creationDateTime TEXT NOT NULL, " +
                "updatedDateTime TEXT NOT NULL, " +
                "deletedAt TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_BudgetEntity_userId ON BudgetEntity(userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_BudgetEntity_categoryId ON BudgetEntity(categoryId)")
    }
}
```

Confirm column types match Room's auto-generated schema (the existing `CategoryMigrations.kt` is the reference).

- [ ] **Step 4: Wire into `MainDatabase`**

`MAIN_DATABASE_VERSION = 8`, add `BudgetEntity::class`, `abstract fun budget(): BudgetRoom`, and add migration in `DatabaseComponent.kt` `Room.databaseBuilder(...).addMigrations(...)`.

- [ ] **Step 5: Build + commit**

```bash
./gradlew :zero-database:compileDebugKotlin
git add zero-database app
git commit -m "feat(budget): add BudgetEntity, DAO, and v7→v8 migration"
```

---

## Task 3: Implement `RoomBudgetRepository`

**Files:**
- Create: `zero-database/.../budget/RoomBudgetRepository.kt`
- Modify: `zero-database/.../DatabaseComponent.kt` — provide it

Model after `RoomCategoryRepository.kt`. Use `currentUserId.take(1).flatMapConcat { ... }` for user scoping, `uncheckedCast()` for the `Criteria<T>` return, soft-delete in `delete()`.

- [ ] **Step 1: Implement** following the `RoomCategoryRepository` shape — each `Criteria` branch maps to a single DAO call. `insert(BudgetInsert)` upserts (`OnConflictStrategy.REPLACE`) — generate id via `idGenerator()` if `id is Id.Unknown`, reuse existing id otherwise.

- [ ] **Step 2: Provide via `DatabaseComponent.Module`** — copy the `categoryRepository(...)` provider, swap names.

- [ ] **Step 3: Expose on `DatabaseComponent` interface** — add `val budgetRepository: BudgetRepository`.

- [ ] **Step 4: Provide at `@ApplicationScope`** in `ApplicationComponent.kt` next to `categoryRepository(...)`, and add `budgetRepository` to `ActivityComponent.Dependencies`.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): wire BudgetRepository through DatabaseComponent + ApplicationComponent"
```

---

## Task 4: Tests for `RoomBudgetRepository`

**Files:**
- Create: `zero-database/src/test/java/com/hluhovskyi/zero/budget/RoomBudgetRepositoryTest.kt`

Mirror `RoomTransactionRepositoryPaginationTest.kt` setup (Mockito DAO double, fixed `ZonedClock`, fixed `IdGenerator`). Cover:
- `Criteria.ForPeriod` returns matched rows
- `Criteria.ForCategoryAndPeriod` returns single row or null
- `Criteria.HasAnyForPeriod` returns true when row exists, false otherwise
- `insert(BudgetInsert with Id.Unknown)` generates id; `insert(BudgetInsert with Id.Known)` reuses
- `delete(id)` calls `softDelete` with current `updatedDateTime`

Per-test pattern: 1 arrange + 1 act + 1 assert. No multi-scenario tests.

- [ ] **Step 1: Add 5 tests above**
- [ ] **Step 2: Run** `./gradlew :zero-database:testDebugUnitTest`
- [ ] **Step 3: Commit**

```bash
git commit -m "test(budget): RoomBudgetRepository unit tests"
```

---

## Task 5: Wire `SyncBudget`

**Files:**
- Create: `zero-api/.../sync/SyncBudget.kt`
- Create: `zero-database/.../budget/BudgetSyncDao.kt`, `RoomBudgetSyncSource.kt`, `RoomBudgetSyncSink.kt`
- Modify: `zero-sync/.../sync/SyncSerializer.kt` + `SyncPipeline.kt` to include `SyncBudget`

Use `SyncCategory.kt` and its corresponding sync source/sink as the template. The DTO carries: `id`, `categoryId`, `type`, `amount`, `periodStart`, `periodEnd`, `creationDateTime`, `updatedDateTime`, `deletedAt`. LWW conflict resolution uses `updatedDateTime` — identical to existing entities.

- [ ] **Step 1: Add `SyncBudget`** with `@Serializable` + same field shape as `SyncCategory`.
- [ ] **Step 2: Add `BudgetSyncDao`** with `selectAll`, `selectAfter(dateTime)`, `upsertAll`.
- [ ] **Step 3: Add `RoomBudgetSyncSource` + `RoomBudgetSyncSink`** following `RoomCategorySyncSource`/`Sink`.
- [ ] **Step 4: Register in `SyncSerializer`** — add `SyncBudget` case to the polymorphic serializer.
- [ ] **Step 5: Register in `SyncPipeline`** — add sink/source pair next to category/account/transaction.
- [ ] **Step 6: Provide in `DatabaseComponent`** — `categorySyncSource()`/`Sink()` pattern.
- [ ] **Step 7: Update `SyncBackwardCompatibilityTest`** — if it asserts the snapshot shape, extend the fixture to cover budgets.
- [ ] **Step 8: Run tests + commit**

```bash
./gradlew :zero-sync:testDebugUnitTest
git commit -m "feat(budget): wire SyncBudget through zero-sync"
```

---

## Task 6: `PeriodResolver` + `BudgetQueryUseCase`

**Files:**
- Create: `zero-core/.../budget/PeriodResolver.kt`
- Create: `zero-api/.../budget/BudgetQueryUseCase.kt`
- Create: `zero-core/.../budget/DefaultBudgetQueryUseCase.kt`

The use-case joins three sources into one display-ready list. Each row is one `Budgeted` model containing the category metadata, current-month spent, and an optional `Budget` (null means "no budget set for this period for this category"). Pattern follows `CategoriesQueryUseCase` (`zero-core/categories/DefaultCategoriesQueryUseCase.kt`).

- [ ] **Step 1: `PeriodResolver`**

```kotlin
internal class PeriodResolver(
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) {
    // V1: monthly. Cadence parameter is the only thing future phases extend.
    fun currentMonth(): Pair<LocalDate, LocalDate> {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        val start = LocalDate(today.year, today.month, 1)
        val end = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return start to end
    }

    fun monthOffsetFrom(reference: LocalDate, offsetMonths: Int): Pair<LocalDate, LocalDate> {
        val anchor = LocalDate(reference.year, reference.month, 1).plus(offsetMonths, DateTimeUnit.MONTH)
        val end = anchor.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return anchor to end
    }
}
```

- [ ] **Step 2: `BudgetQueryUseCase` interface**

```kotlin
interface BudgetQueryUseCase {
    fun query(from: LocalDate, to: LocalDate): Flow<List<Budgeted>>

    data class Budgeted(
        val categoryId: Id.Known,
        val categoryName: String,
        val iconUri: String?,
        val colorScheme: ColorScheme,
        val spent: Amount,
        val budgetId: Id.Known?,         // null when no row
        val budgeted: Amount,            // Amount.zero() when budgetId == null
    )

    object Noop : BudgetQueryUseCase {
        override fun query(from: LocalDate, to: LocalDate): Flow<List<Budgeted>> = emptyFlow()
    }
}
```

- [ ] **Step 3: `DefaultBudgetQueryUseCase`**

`combine(categoriesQueryUseCase.query(), budgetRepository.query(Criteria.ForPeriod(from, to)), categorySpendingUseCase.query(Period.Between(from, to))) { categories, budgets, spending -> ... }`. Filter `categories` to `type == EXPENSE`. For each, find matching budget by `categoryId` (null if absent), find matching spending (zero if absent), produce a `Budgeted`. Use `onStartWithEmptyList()` on each inner flow so the combine doesn't stall.

- [ ] **Step 4: Tests** — `DefaultBudgetQueryUseCaseTest.kt` covers: empty inputs → empty list; categories present, no budgets → rows with `budgeted=zero`; budget present → row carries amount; spending matched and unmatched by category id.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): BudgetQueryUseCase combining categories, budgets, spending"
```

---

## Task 7: Scaffold `BudgetComponent` + ViewModel + ViewProvider

Run `superpowers/scaffold-feature` for name `Budget`, package `budget`, handlers `back` (not used yet, but consistent), no extra use case.

**After scaffold:**
- [ ] **Step 1: Wire `BudgetComponent.Dependencies`**

```kotlin
interface Dependencies {
    val imageLoader: ImageLoader
    val amountFormatter: AmountFormatter
    val budgetQueryUseCase: BudgetQueryUseCase
    val clock: Clock
    val zoneProvider: ZoneProvider
    val configurationRepository: ConfigurationRepository
}
```

- [ ] **Step 2: Wire `BudgetComponent.Module`**

Provide a `PeriodResolver` at `@BudgetScope`, then the `BudgetViewModel`, then a `BudgetViewProvider`.

- [ ] **Step 3: `BudgetViewModel`**

State carries:
```kotlin
data class State(
    val monthOffset: Int = 0,        // 0 = current, -1 = previous, +1 = next
    val displayedPeriodLabel: String = "",   // e.g. "May 2026"
    val hasPrevious: Boolean = true, // always true for now
    val hasNext: Boolean = true,     // always true for now
    val budgeted: List<BudgetQueryUseCase.Budgeted> = emptyList(),
    val previousPeriodBudgets: List<BudgetQueryUseCase.Budgeted> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface Action {
    object SelectOlderMonth : Action
    object SelectNewerMonth : Action
    data class TapUnsetCategory(val categoryId: Id.Known) : Action  // no-op stub Phase 1
    data class TapSetCategory(val categoryId: Id.Known) : Action    // no-op stub Phase 1
    object TapCreateBudget : Action                                 // no-op stub Phase 1
    object TapCopyFromPrevious : Action                             // no-op stub Phase 1
}
```

`attach()` launches a coroutine that:
1. Holds a `monthOffset: MutableStateFlow<Int>`.
2. `flatMapLatest` on it: resolve `currentPeriod = periodResolver.monthOffsetFrom(today, offset)` and `prevPeriod = monthOffsetFrom(today, offset - 1)`.
3. `combine(query(currentPeriod), query(prevPeriod)) { current, prev -> state.copy(...) }`.
4. `collectLatest` updates `mutableState`.

Phase 1 `perform()` only handles `SelectOlderMonth` / `SelectNewerMonth` (update `monthOffset`). All other actions: `// Phase 2+`.

- [ ] **Step 4: `BudgetViewProvider`** — first cut renders the empty-state layout from `BudgetScreen.jsx` lines 968–1080:

Compose structure:
```
Column {
  // Header: "Budget" title + MonthSelector row
  Row { Text("Budget", 22sp/Bold) }
  BudgetMonthSelector(label, hasOlder, hasNewer, onOlder, onNewer)

  // Empty-state callout (always in Phase 1)
  EmptyBudgetCallout(periodLabel = state.displayedPeriodLabel, totalCategories = state.budgeted.size, previousPeriodTotal = state.previousPeriodBudgets.sumOf { it.budgeted })

  // "Copy from {prev}" card, only if previousPeriodBudgets.isNotEmpty
  if (state.previousPeriodBudgets.isNotEmpty()) {
    CopyFromPreviousCard(monthLabel = "April 2026", count = ..., total = ..., onClick = { /* Phase 3 */ })
  }

  // Section label "Set spending limits" (uppercase tiny label, OnSurfaceVariant)
  SectionLabel("Set spending limits")

  // Dashed-border unset rows
  LazyColumn {
    items(state.budgeted) { row ->
      UnsetCategoryRow(
        name = row.categoryName,
        iconUri = row.iconUri,
        colorScheme = row.colorScheme,
        previousAmount = previousPeriodBudgets.firstOrNull { it.categoryId == row.categoryId }?.budgeted,
        onClick = { /* Phase 2 */ },
      )
    }
  }
}
```

Use these design tokens (from `colors_and_type.css` — already exists in `zero-ui/.../theme/`):
- App bg: `MaterialTheme.colors.background` (Surface = `#FAF8FD`)
- Empty-state callout bg: `#0A2351` (PrimaryContainer)
- Dashed-border `1.5dp` color: `OutlineVariant`
- Card radius for unset row: `16.dp`
- Set limit pill: `SurfaceContainerLow` background, radius `10.dp`, internal padding `8/12`

Reuse existing `CategoryIcon` composable from `zero-ui/.../categories/` (find via `grep -rn 'fun CategoryIcon' zero-ui`). If it doesn't take the right shape, use `imageLoader.View(image = ..., tint = ...)` inside a 36dp rounded square with category color background.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): BudgetComponent skeleton + empty-state layout"
```

---

## Task 8: Wire `Destinations.Budget` + bottom-bar branch + navigation entry

**Files:**
- Modify: `app/.../activity/navigation/Destinations.kt`
- Modify: `app/.../activity/screens/bottombar/DefaultBottomBarViewModel.kt` lines 92, 134
- Modify: `app/.../activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Add destination** — see [Navigation](../agents/navigation.md). `object Budget : Destination by destinationOf("budget")`.

- [ ] **Step 2: Uncomment the two `Budget`-related branches** in `DefaultBottomBarViewModel.kt`.

- [ ] **Step 3: Add `budgetComponentBuilder: BudgetComponent.Builder` to `MainActivityScreenComponent.Dependencies`.**

- [ ] **Step 4: Add `budgetNavigationEntry(...)` `@IntoSet` provider:**

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun budgetNavigationEntry(
    componentBuilder: BudgetComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.composable(Destinations.Budget) {
    componentBuilder.logging(logger).build().AttachWithView()
}
```

Use `accountNavigationEntry` (around line 460) as the structural reference.

- [ ] **Step 5: Provide `BudgetComponent.Builder` in `ActivityComponent`** — copy the `categoryComponentBuilder` provider, swap names, pass dependencies.

- [ ] **Step 6: Build the full app** — `./gradlew :app:assembleDebug`.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(budget): wire Budget destination + bottom-bar navigation"
```

---

## Task 9: Manual verification

- [ ] **Step 1: Install** — `./gradlew :app:installDebug` on the emulator acquired by `acquire-emulator.sh`.

- [ ] **Step 2: Run UI inspector** — invoke `zero-project:android-ui-inspector` after tapping the Budget tab. Confirm:
  - The Budget tab is selected (pill behind icon, label "Budget" in primary navy).
  - Title "Budget" renders at top.
  - MonthSelector chevrons render with the current month label.
  - The dark empty-state callout renders with the icon, "No budget for {month}" text, and category/Budget set/Last month columns.
  - The category list shows every alive expense category as a dashed-border row with "Set limit" pill.
  - Tapping any of the buttons is a no-op (intentional in Phase 1).

- [ ] **Step 3: Tests + lint**

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
```

- [ ] **Step 4: Open PR**

```bash
gh pr create --title "feat: budget Phase 1 — schema + read-only viewing" --body "$(cat <<'EOF'
## Summary
- New `BudgetEntity` + `BudgetRoom` + `MIGRATION_7_8`
- `BudgetRepository` (API + Room impl) with cadence-neutral `Criteria.ForPeriod`
- `SyncBudget` wired through `zero-sync`
- `BudgetQueryUseCase` joining categories + budgets + spending
- `BudgetComponent` skeleton + Budget tab destination + empty-state UI

## Test plan
- [ ] Tap Budget tab — empty-state callout renders
- [ ] MonthSelector chevrons advance through periods
- [ ] No regressions on Transactions / Accounts / Categories / Settings tabs
- [ ] Lint passes
- [ ] Tests pass

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review Checklist

- [ ] All 11 user-facing tasks from the roadmap are accounted for across phases (this phase: schema foundation only — items 9 "navigate periods" partially via MonthSelector).
- [ ] No placeholders (TBD, etc.) in this plan.
- [ ] Naming consistent — `BudgetRepository`, `BudgetQueryUseCase`, `BudgetComponent`, `Destinations.Budget`.
- [ ] Cadence-neutral API: `Criteria.ForPeriod(from, to)` not `ForMonth(year, month)`.
- [ ] Type-ready: `BudgetType` column written from day 1, even though only `EXPENSE` is used.
