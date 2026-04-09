# kotlinx.datetime Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all `java.time` domain types with `kotlinx.datetime` equivalents to make the domain layer KMP-ready, working module-by-module from zero-api inward.

**Architecture:** `zero-api` domain interfaces change first (LocalDateTime, LocalDate, Instant, TimeZone), then zero-database, zero-core, zero-ui, app, and zero-zenmoney follow. The `Clock` interface gains a `timeZone()` method so the existing `clock.localDateTime()` extension keeps its call signature — zero callers need changing. Android-specific formatting (DateTimeFormatter) stays in the `app` module as an intentional platform boundary.

**Tech Stack:** `org.jetbrains.kotlinx:kotlinx-datetime` (latest stable), `kotlin.time.Duration` (stdlib — no extra dep), java interop extensions (`toJavaLocalDateTime()`, `toKotlinLocalDateTime()`) for formatting bridges.

---

## Key API Mappings

| java.time | kotlinx.datetime / stdlib |
|-----------|--------------------------|
| `java.time.LocalDateTime` | `kotlinx.datetime.LocalDateTime` |
| `java.time.LocalDate` | `kotlinx.datetime.LocalDate` |
| `java.time.ZonedDateTime` | Removed — replaced by `Instant` in `Clock` |
| `java.time.ZoneId` | `kotlinx.datetime.TimeZone` |
| `java.time.Duration` | `kotlin.time.Duration` (stdlib) |
| `LocalDateTime.now()` | `clock.localDateTime()` (inject Clock) |
| `Duration.between(a, b).toDays()` | `(nowInstant - a.toInstant(tz)).inWholeDays` |
| `LocalDateTime.of(y, m, d, 0, 0)` | `LocalDateTime(y, m, d, 0, 0)` |
| `date.monthValue` | `date.monthNumber` |
| `LocalDate.parse(s, formatter)` | `JavaLocalDate.parse(s, formatter).toKotlinLocalDate()` |

**Room storage:** `kotlinx.datetime.LocalDateTime.toString()` produces identical ISO-8601 format as `java.time.LocalDateTime.toString()` (e.g. `"2024-01-15T10:30:00"`). No database migration needed.

**Naming conflict:** `kotlinx.datetime.Clock` conflicts with `com.hluhovskyi.zero.common.time.Clock`. In `ZoneBasedClock.kt`, use fully-qualified `kotlinx.datetime.Clock.System.now()` without importing it.

---

## File Map

**Modify:**
- `build.gradle` (root) — add `kotlinxDatetime` to `deps`
- `zero-api/build.gradle` — add `implementation deps.kotlinxDatetime`
- `zero-database/build.gradle` — add `implementation deps.kotlinxDatetime`
- `zero-core/build.gradle` — already has `implementation deps.kotlinxDatetime` (add if missing)
- `zero-ui/build.gradle` — add `implementation deps.kotlinxDatetime`
- `app/build.gradle` — add `implementation deps.kotlinxDatetime`
- `zero-zenmoney/build.gradle` — add `implementation deps.kotlinxDatetime`
- `zero-api/src/main/java/com/hluhovskyi/zero/common/time/Clock.kt`
- `zero-api/src/main/java/com/hluhovskyi/zero/common/time/ZoneProvider.kt`
- `zero-api/src/main/java/com/hluhovskyi/zero/common/DateFormatter.kt`
- `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt`
- `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`
- `zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportTransaction.kt`
- `zero-database/src/main/java/com/hluhovskyi/zero/LocalDateTimeConverter.kt`
- `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionEntity.kt`
- `zero-database/src/main/java/com/hluhovskyi/zero/categories/CategoryEntity.kt`
- `zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategoryUsageStatistic.kt`
- `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt`
- `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt`
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DatePickerCard.kt`
- `app/src/main/java/com/hluhovskyi/zero/common/LocaleBasedDateFormatter.kt`
- `app/src/main/java/com/hluhovskyi/zero/common/time/ZoneBasedClock.kt`
- `app/src/main/java/com/hluhovskyi/zero/common/time/SystemZoneProvider.kt`
- `zero-zenmoney/src/main/java/com/hluhovskyi/zero/imports/ZenMoneyImportSourceUseCase.kt`
- `docs/agents/code-style.md`

---

## Task 1: Add kotlinx.datetime dependency

**Files:**
- Modify: `build.gradle` (root)
- Modify: `zero-api/build.gradle`, `zero-database/build.gradle`, `zero-core/build.gradle`, `zero-ui/build.gradle`, `app/build.gradle`, `zero-zenmoney/build.gradle`

- [ ] **Step 1: Add to root build.gradle deps block**

In the `deps` map, after the `kotlin` section, add:

```groovy
kotlinxDatetime: "org.jetbrains.kotlinx:kotlinx-datetime:0.6.1",
```

> Check https://github.com/Kotlin/kotlinx-datetime/releases for the latest stable version and use that instead of 0.6.1 if newer.

- [ ] **Step 2: Add dependency to each module's build.gradle**

In `zero-api/build.gradle`, add to `dependencies`:
```groovy
implementation deps.kotlinxDatetime
```

Repeat for: `zero-database/build.gradle`, `zero-core/build.gradle`, `zero-ui/build.gradle`, `app/build.gradle`, `zero-zenmoney/build.gradle`.

- [ ] **Step 3: Verify dependency resolves**

```bash
./gradlew :zero-api:dependencies --configuration compileClasspath 2>&1 | grep "kotlinx-datetime"
```

Expected: a line showing `kotlinx-datetime` resolved.

- [ ] **Step 4: Commit**

```bash
git add build.gradle zero-api/build.gradle zero-database/build.gradle zero-core/build.gradle zero-ui/build.gradle app/build.gradle zero-zenmoney/build.gradle
git commit -m "chore: add kotlinx.datetime dependency to all modules"
```

---

## Task 2: Migrate zero-api domain interfaces

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/common/time/Clock.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/common/time/ZoneProvider.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/common/DateFormatter.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/imports/ImportTransaction.kt`

- [ ] **Step 1: Rewrite Clock.kt**

```kotlin
package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Injectable time provider. Always use this instead of Clock.System.now() — keeps code testable
 * with fixed clocks.
 */
interface Clock {
    fun now(): Instant
    fun timeZone(): TimeZone
}

/** Convenience extension — the most common usage throughout the codebase. */
fun Clock.localDateTime(): LocalDateTime = now().toLocalDateTime(timeZone())
```

- [ ] **Step 2: Rewrite ZoneProvider.kt**

```kotlin
package com.hluhovskyi.zero.common.time

import kotlinx.datetime.TimeZone

interface ZoneProvider {
    fun timeZone(): TimeZone
}
```

- [ ] **Step 3: Rewrite DateFormatter.kt**

Only the `LocalDate` import changes:

```kotlin
package com.hluhovskyi.zero.common

import kotlinx.datetime.LocalDate

interface DateFormatter {

    fun format(
        date: LocalDate,
        dayConfig: DayConfig,
        monthConfig: MonthConfig,
        yearConfig: YearConfig
    ): String

    enum class DayConfig {
        Default,
        WithoutZero,
    }

    enum class MonthConfig {
        Readable,
    }

    enum class YearConfig {
        Default,
        SkipCurrent,
    }
}
```

- [ ] **Step 4: Rewrite CategoriesQueryUseCase.kt**

Only the `LocalDate` import changes in `RankSignal`:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>
    fun queryAll(): Flow<List<Category>>
    fun queryRanked(signals: Flow<RankSignal>): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    ) : Identifiable

    sealed class RankSignal {
        data class AccountChanged(val accountId: Id.Known?) : RankSignal()
        data class DateChanged(val date: LocalDate?) : RankSignal()
    }
}
```

- [ ] **Step 5: Update TransactionRepository.CategoryUsageStatistic**

Find `data class CategoryUsageStatistic` inside `TransactionRepository.kt` and change `java.time.LocalDateTime` to `kotlinx.datetime.LocalDateTime`:

```kotlin
data class CategoryUsageStatistic(
    val categoryId: Id.Known,
    val transactionCount: Int,
    val lastUsedDateTime: kotlinx.datetime.LocalDateTime,
)
```

Also remove `import java.time.LocalDateTime` from the file and add `import kotlinx.datetime.LocalDateTime`.

- [ ] **Step 6: Rewrite ImportTransaction.kt**

```kotlin
package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

sealed interface ImportTransaction {

    val id: Id.Known
    val amount: Amount
    val currencyId: Id.Known
    val accountId: Id.Known
    val dateTime: LocalDateTime

    data class Expense(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known,
    ) : ImportTransaction

    data class Income(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known,
    ) : ImportTransaction

    data class Transfer(
        override val id: Id.Known,
        override val amount: Amount,
        override val currencyId: Id.Known,
        override val accountId: Id.Known,
        override val dateTime: LocalDateTime,
        val targetAccount: Id.Known,
        val targetAmount: Amount,
    ) : ImportTransaction
}
```

- [ ] **Step 7: Verify zero-api compiles**

```bash
./gradlew :zero-api:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add zero-api/
git commit -m "feat: migrate zero-api domain types to kotlinx.datetime"
```

---

## Task 3: Migrate zero-database

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/LocalDateTimeConverter.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionEntity.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/categories/CategoryEntity.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategoryUsageStatistic.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`

> **No Room migration needed.** `kotlinx.datetime.LocalDateTime.toString()` produces the same ISO-8601 format as `java.time.LocalDateTime.toString()` (e.g. `"2024-01-15T10:30:00"`). The converter strings are identical.

- [ ] **Step 1: Rewrite LocalDateTimeConverter.kt**

```kotlin
package com.hluhovskyi.zero

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDateTime

internal object LocalDateTimeConverter {

    @TypeConverter
    fun localDateTimeToString(dateTime: LocalDateTime): String =
        dateTime.toString()

    @TypeConverter
    fun stringToLocalDateTime(rawDateTime: String): LocalDateTime =
        LocalDateTime.parse(rawDateTime)
}
```

- [ ] **Step 2: Rewrite TransactionEntity.kt**

```kotlin
package com.hluhovskyi.zero.transactions

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.RateEntity
import kotlinx.datetime.LocalDateTime

@Entity(
    indices = [Index("userId")]
)
internal data class TransactionEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val type: Type,
    val currencyId: Id.Known,
    val accountId: Id.Known,
    val categoryId: String?,
    @Embedded(prefix = "amount_") val amount: AmountEntity,
    @Embedded(prefix = "rate_") val rate: RateEntity,
    val targetAccount: String?,
    @Embedded(prefix = "target_amount_") val targetAmount: AmountEntity,
    val enteredDateTime: LocalDateTime,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
) {
    enum class Type {
        EXPENSE,
        INCOME,
        TRANSFER
    }
}
```

- [ ] **Step 3: Rewrite CategoryEntity.kt**

```kotlin
package com.hluhovskyi.zero.categories

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Entity(
    indices = [Index("userId")]
)
data class CategoryEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val name: String,
    val iconId: String?,
    val colorId: String?,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
)
```

- [ ] **Step 4: Rewrite CategoryUsageStatistic.kt (zero-database internal)**

```kotlin
package com.hluhovskyi.zero.transactions

import kotlinx.datetime.LocalDateTime

internal data class CategoryUsageStatistic(
    val categoryId: String,
    val transactionCount: Int,
    val lastUsedDateTime: LocalDateTime,
)
```

- [ ] **Step 5: Update RoomTransactionRepository.kt**

Find all `java.time.LocalDateTime` and `java.time.LocalDate` imports in `RoomTransactionRepository.kt` and replace with:
```kotlin
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
```

Then for any `LocalDate`→`LocalDateTime` conversion that used `atStartOfDay()`, replace with:
```kotlin
LocalDateTime(date.year, date.month, date.dayOfMonth, 0, 0, 0)
```

- [ ] **Step 6: Verify zero-database compiles**

```bash
./gradlew :zero-database:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run zero-database tests**

```bash
./gradlew :zero-database:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add zero-database/
git commit -m "feat: migrate zero-database entities and converters to kotlinx.datetime"
```

---

## Task 4: Migrate zero-core

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt`
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt`

> `DefaultTransactionEditUseCase` and `DefaultImportUseCase` already inject `Clock` and call `clock.localDateTime()`. Since the extension signature didn't change, those files need only an import fix: remove `import java.time.LocalDateTime` and add `import kotlinx.datetime.LocalDateTime`. Scan all files in zero-core for `import java.time` and apply the same import swap.

- [ ] **Step 1: Fix DefaultCategoriesQueryUseCase.kt — inject Clock, fix LocalDateTime.now() and Duration**

The current class does not inject `Clock` and calls `LocalDateTime.now()` directly. Replace the class with:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.LocalDateTime
import kotlin.math.exp

internal class DefaultCategoriesQueryUseCase(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) : CategoriesQueryUseCase {

    private val queryAll = combine(
        categoryRepository.query(CategoryRepository.Criteria.All()),
        iconRepository.query(IconRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
        colorRepository.query(ColorRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
    ) { categories, idToIcons, idToColors ->
        categories.map { category ->
            resolve(
                category = category,
                idToIcons = idToIcons,
                idToColors = idToColors
            )
        }
    }

    override fun queryAll(): Flow<List<CategoriesQueryUseCase.Category>> = queryAll

    override fun queryById(id: Id.Known): Flow<CategoriesQueryUseCase.Category> = queryAll
        .mapNotNull { categories -> categories.firstOrNull { it.id == id } }

    override fun queryRanked(
        signals: Flow<CategoriesQueryUseCase.RankSignal>,
    ): Flow<List<CategoriesQueryUseCase.Category>> = combine(
        queryAll,
        transactionRepository.query(TransactionRepository.Criteria.CategoryUsageStatistics()),
    ) { categories, usageStatistics ->
        rankCategories(categories, usageStatistics)
    }

    private fun rankCategories(
        categories: List<CategoriesQueryUseCase.Category>,
        usageStatistics: List<TransactionRepository.CategoryUsageStatistic>,
    ): List<CategoriesQueryUseCase.Category> {
        val statsById = usageStatistics.associateBy { it.categoryId }
        val nowInstant = clock.now()
        val timeZone = clock.timeZone()

        val (used, unused) = categories.partition { statsById.containsKey(it.id) }

        val scored = used
            .map { category ->
                val stat = statsById.getValue(category.id)
                val daysSinceLastUse = (nowInstant - stat.lastUsedDateTime.toInstant(timeZone))
                    .inWholeDays.toDouble()
                val recencyDecay = exp(-daysSinceLastUse / DECAY_PERIOD_DAYS)
                val score = stat.transactionCount * recencyDecay
                category to score
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val alphabetical = unused.sortedBy { it.name }

        return scored + alphabetical
    }

    private fun resolve(
        category: CategoryRepository.Category,
        idToIcons: Map<Id.Known, Icon>,
        idToColors: Map<Id.Known, Color>
    ): CategoriesQueryUseCase.Category {
        val icon = idToIcons[category.iconId]
            ?: idToIcons[IconRepository.unknownCategoryIconId()]
            ?: Icon.empty()

        val color = idToColors[category.colorId]
            ?: idToColors[ColorRepository.unknownCategoryColorId()]

        val colorScheme = color?.let { colorRepository.schemeFor(it.id) }
            ?: colorRepository.schemeFor(ColorRepository.unknownCategoryColorId())

        return CategoriesQueryUseCase.Category(
            id = category.id,
            name = category.name,
            icon = icon.image,
            colorScheme = colorScheme,
        )
    }

    private companion object {
        const val DECAY_PERIOD_DAYS = 30.0
    }
}
```

- [ ] **Step 2: Find and update the Dagger wiring for DefaultCategoriesQueryUseCase**

Search for where `DefaultCategoriesQueryUseCase` is instantiated in Dagger `@Provides` methods:

```bash
grep -rn "DefaultCategoriesQueryUseCase" zero-core/src/main/java --include="*.kt"
```

In the `@Provides` method that creates it, add `clock: Clock` as a parameter and pass it to the constructor.

- [ ] **Step 3: Fix java.time imports across zero-core**

Scan for all remaining `java.time` imports in zero-core and replace:

```bash
grep -rn "import java.time" zero-core/src/main/java --include="*.kt"
```

For each file found:
- Replace `import java.time.LocalDateTime` → `import kotlinx.datetime.LocalDateTime`
- Replace `import java.time.LocalDate` → `import kotlinx.datetime.LocalDate`

Any `LocalDate.atStartOfDay()` call should be replaced with:
```kotlin
LocalDateTime(date.year, date.month, date.dayOfMonth, 0, 0, 0)
```

- [ ] **Step 4: Update DefaultCategoriesQueryUseCaseTest.kt**

Replace the test file:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultCategoriesQueryUseCaseTest {

    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var iconRepository: IconRepository
    @Mock private lateinit var colorRepository: ColorRepository
    @Mock private lateinit var transactionRepository: TransactionRepository

    // Fixed clock: 2024-06-01T12:00:00Z in UTC
    private val fixedInstant = Instant.parse("2024-06-01T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val fakeClock = object : Clock {
        override fun now() = fixedInstant
        override fun timeZone() = testTimeZone
    }

    @Before
    fun setUp() {
        whenever(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(colorRepository.query(any<ColorRepository.Criteria<List<com.hluhovskyi.zero.colors.Color>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)
    }

    private fun createUseCase() = DefaultCategoriesQueryUseCase(
        categoryRepository = categoryRepository,
        iconRepository = iconRepository,
        colorRepository = colorRepository,
        transactionRepository = transactionRepository,
        clock = fakeClock,
    )

    @Test
    fun `queryRanked sorts categories by frequency times recency`() = runTest {
        val catA = CategoryRepository.Category(
            id = Id.Known("a"), parentCategoryId = Id.Unknown,
            name = "A", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catB = CategoryRepository.Category(
            id = Id.Known("b"), parentCategoryId = Id.Unknown,
            name = "B", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catA, catB)))

        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.CategoryUsageStatistic>>>(), any()))
            .thenReturn(flowOf(listOf(
                TransactionRepository.CategoryUsageStatistic(
                    categoryId = Id.Known("a"),
                    transactionCount = 5,
                    lastUsedDateTime = LocalDateTime(2024, 4, 2, 12, 0, 0), // 60 days before fixedInstant
                ),
                TransactionRepository.CategoryUsageStatistic(
                    categoryId = Id.Known("b"),
                    transactionCount = 3,
                    lastUsedDateTime = LocalDateTime(2024, 6, 1, 12, 0, 0), // same as fixedInstant (0 days)
                ),
            )))

        val useCase = createUseCase()
        val result = useCase.queryRanked(emptyFlow()).first()

        // B: decay ~1.0 * 3 = ~3.0; A: decay ~exp(-2) * 5 = ~0.68
        assertEquals(Id.Known("b"), result[0].id)
        assertEquals(Id.Known("a"), result[1].id)
    }

    @Test
    fun `queryRanked puts unused categories at end sorted alphabetically`() = runTest {
        val catA = CategoryRepository.Category(
            id = Id.Known("a"), parentCategoryId = Id.Unknown,
            name = "Zebra", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catB = CategoryRepository.Category(
            id = Id.Known("b"), parentCategoryId = Id.Unknown,
            name = "Apple", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catC = CategoryRepository.Category(
            id = Id.Known("c"), parentCategoryId = Id.Unknown,
            name = "Cherry", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catA, catB, catC)))

        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.CategoryUsageStatistic>>>(), any()))
            .thenReturn(flowOf(listOf(
                TransactionRepository.CategoryUsageStatistic(
                    categoryId = Id.Known("c"),
                    transactionCount = 1,
                    lastUsedDateTime = LocalDateTime(2024, 6, 1, 12, 0, 0),
                ),
            )))

        val useCase = createUseCase()
        val result = useCase.queryRanked(emptyFlow()).first()

        assertEquals("Cherry", result[0].name)
        assertEquals("Apple", result[1].name)
        assertEquals("Zebra", result[2].name)
    }
}
```

- [ ] **Step 5: Verify zero-core compiles and tests pass**

```bash
./gradlew :zero-core:build && ./gradlew :zero-core:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` for both.

- [ ] **Step 6: Commit**

```bash
git add zero-core/
git commit -m "feat: migrate zero-core to kotlinx.datetime, inject Clock into DefaultCategoriesQueryUseCase"
```

---

## Task 5: Migrate zero-ui (DatePickerCard)

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DatePickerCard.kt`

> `zero-ui` has no domain dependency, so `LocalDateTime` here is only used in the composable API surface and for formatting. Use `kotlinx.datetime.LocalDateTime` for the API, and bridge to `java.time` for the `DateTimeFormatter` call via `toJavaLocalDateTime()`.

- [ ] **Step 1: Rewrite DatePickerCard.kt**

```kotlin
package com.hluhovskyi.zero.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DatePickerCard(
    modifier: Modifier = Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(date) {
        date.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }

    Column(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateSelected(LocalDateTime(year, month + 1, dayOfMonth, 0, 0, 0))
                    },
                    date.year,
                    date.monthNumber - 1,
                    date.dayOfMonth
                ).show()
            }
            .padding(16.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = formattedDate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}
```

Key changes:
- `date.format(DateTimeFormatter...)` → `date.toJavaLocalDateTime().format(DateTimeFormatter...)`
- `LocalDateTime.of(year, month + 1, dayOfMonth, 0, 0)` → `LocalDateTime(year, month + 1, dayOfMonth, 0, 0, 0)`
- `date.monthValue - 1` → `date.monthNumber - 1`

- [ ] **Step 2: Verify zero-ui compiles**

```bash
./gradlew :zero-ui:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add zero-ui/
git commit -m "feat: migrate DatePickerCard to kotlinx.datetime"
```

---

## Task 6: Migrate app module

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/common/LocaleBasedDateFormatter.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/common/time/ZoneBasedClock.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/common/time/SystemZoneProvider.kt`

- [ ] **Step 1: Rewrite ZoneBasedClock.kt**

```kotlin
package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

internal class ZoneBasedClock(
    private val zoneProvider: ZoneProvider,
) : Clock {
    override fun now(): Instant = kotlinx.datetime.Clock.System.now()
    override fun timeZone(): TimeZone = zoneProvider.timeZone()
}
```

> Note the fully-qualified `kotlinx.datetime.Clock.System.now()` — do NOT import `kotlinx.datetime.Clock` as it conflicts with our `Clock` interface.

- [ ] **Step 2: Rewrite SystemZoneProvider.kt**

```kotlin
package com.hluhovskyi.zero.common.time

import kotlinx.datetime.TimeZone

internal object SystemZoneProvider : ZoneProvider {
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}
```

- [ ] **Step 3: Rewrite LocaleBasedDateFormatter.kt**

Bridge from `kotlinx.datetime.LocalDate` to `java.time` for formatting:

```kotlin
package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

internal class LocaleBasedDateFormatter(
    private val localeProvider: LocaleProvider,
    private val clock: Clock,
) : DateFormatter {

    private val currentYear by lazy {
        clock.localDateTime().year
    }

    override fun format(
        date: LocalDate,
        dayConfig: DateFormatter.DayConfig,
        monthConfig: DateFormatter.MonthConfig,
        yearConfig: DateFormatter.YearConfig
    ): String {
        val patternBuilder = StringBuilder()

        when (dayConfig) {
            DateFormatter.DayConfig.Default -> patternBuilder.append("dd")
            DateFormatter.DayConfig.WithoutZero -> patternBuilder.append("d")
        }
        when (monthConfig) {
            DateFormatter.MonthConfig.Readable -> patternBuilder.append(" MMMM")
        }
        when (yearConfig) {
            DateFormatter.YearConfig.Default -> patternBuilder.append("-yyyy")
            DateFormatter.YearConfig.SkipCurrent -> if (date.year != currentYear) {
                patternBuilder.append(" yyyy")
            }
        }

        return DateTimeFormatter.ofPattern(
            patternBuilder.toString(),
            localeProvider.locale()
        ).format(date.toJavaLocalDate())
    }
}
```

- [ ] **Step 4: Verify app module compiles**

```bash
./gradlew :app:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/
git commit -m "feat: migrate app module clock and formatter to kotlinx.datetime"
```

---

## Task 7: Migrate zero-zenmoney

**Files:**
- Modify: `zero-zenmoney/src/main/java/com/hluhovskyi/zero/imports/ZenMoneyImportSourceUseCase.kt`

- [ ] **Step 1: Update date parsing in ZenMoneyImportSourceUseCase.kt**

Find the `dateParser` lambda and update it to bridge from java.time parsing to kotlinx.datetime:

```kotlin
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDate as JavaLocalDate

private val dateParser: (String) -> LocalDateTime = {
    JavaLocalDate.parse(it, DATE_PARSER).atStartOfDay().toKotlinLocalDateTime()
}
```

Also replace any other `import java.time.LocalDateTime` or `import java.time.LocalDate` with `import kotlinx.datetime.LocalDateTime` / `import kotlinx.datetime.LocalDate` as appropriate throughout the file.

- [ ] **Step 2: Verify zero-zenmoney compiles**

```bash
./gradlew :zero-zenmoney:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add zero-zenmoney/
git commit -m "feat: migrate zero-zenmoney to kotlinx.datetime"
```

---

## Task 8: Final verification and docs

**Files:**
- Modify: `docs/agents/code-style.md`

- [ ] **Step 1: Full build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: All unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Lint check**

```bash
./gradlew :zero-core:lintDebug
```

Expected: `BUILD SUCCESSFUL` — zero errors.

- [ ] **Step 4: Verify no java.time in domain modules**

```bash
grep -rn "import java.time" zero-api/src zero-database/src zero-core/src --include="*.kt"
```

Expected: no output. If any remain, fix them before proceeding.

- [ ] **Step 5: Update docs/agents/code-style.md**

Add a new section:

```markdown
## Date / Time

Use `kotlinx.datetime` types throughout the codebase:
- `kotlinx.datetime.LocalDateTime` — timestamps stored in DB, passed between layers
- `kotlinx.datetime.LocalDate` — calendar dates (e.g. transaction date filter)
- `kotlinx.datetime.Instant` — what `Clock.now()` returns
- `kotlinx.datetime.TimeZone` — what `ZoneProvider.timeZone()` returns
- `kotlin.time.Duration` — durations (Kotlin stdlib, no extra import needed)

Do NOT use `java.time.*` in domain code (`zero-api`, `zero-core`, `zero-database`).
`java.time.format.DateTimeFormatter` is allowed only in `app` (for locale-aware formatting)
and `zero-ui` (via `toJavaLocalDateTime()` bridge). This boundary is intentional — it will
become an `expect/actual` when the project goes KMP.

Always inject `Clock` from `com.hluhovskyi.zero.common.time.Clock` instead of calling
`LocalDateTime.now()` or `kotlinx.datetime.Clock.System.now()` directly. This keeps code testable.
```

- [ ] **Step 6: Commit**

```bash
git add docs/agents/code-style.md
git commit -m "docs: document kotlinx.datetime as project standard, ban java.time in domain"
```

- [ ] **Step 7: Push and open PR**

```bash
git push -u origin feat/kotlinx-datetime-migration
gh pr create --title "feat: migrate domain to kotlinx.datetime for KMP readiness" --body "Replaces all java.time domain types with kotlinx.datetime across zero-api, zero-database, zero-core, zero-ui, app, and zero-zenmoney. No Room migration needed — ISO string format is identical. Formatting bridges (DateTimeFormatter) are intentionally kept in the app layer as the future KMP expect/actual boundary."
```
