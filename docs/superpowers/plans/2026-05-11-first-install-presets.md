# First-Install Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed preset accounts and categories on first install so the app is not empty when a new user opens it.

**Architecture:** `DefaultPresetsUseCase.seed()` reads a `ConfigurationKey<Boolean>` flag; if false, it inserts preset categories + accounts and writes the flag to true (idempotent). `ActivityComponent.attach()` launches the call in an IO coroutine — it fires once per process start but `seed()` is a no-op after first run.

**Tech Stack:** Kotlin coroutines, Room (via repository layer), Dagger, `ConfigurationRepository`, `CategoryRepository`, `AccountRepository`, `CurrencyPrimaryUseCase`.

---

## File Map

| Action | File |
|--------|------|
| Create | `app/src/main/res/drawable/ic_shopping_cart_24.xml` |
| Create | `app/src/main/res/drawable/ic_health_24.xml` |
| Create | `app/src/main/res/drawable/ic_salary_24.xml` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/icons/KnownIconIds.kt` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt` |
| Create | `zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsUseCase.kt` |
| Create | `zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsConfigurationKey.kt` |
| Create | `zero-core/src/main/java/com/hluhovskyi/zero/presets/DefaultPresetsUseCase.kt` |
| Create | `zero-core/src/test/java/com/hluhovskyi/zero/presets/DefaultPresetsUseCaseTest.kt` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt` |
| Modify | `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt` |

---

## Task 1: Add three new vector drawables

**Files:**
- Create: `app/src/main/res/drawable/ic_shopping_cart_24.xml`
- Create: `app/src/main/res/drawable/ic_health_24.xml`
- Create: `app/src/main/res/drawable/ic_salary_24.xml`

All follow the same format as existing icons (24dp viewport, `?attr/colorControlNormal` tint, white fill).

- [ ] **Step 1: Create `ic_shopping_cart_24.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M17,18c-1.1,0 -1.99,0.9 -1.99,2S15.9,22 17,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM3,2H1v2h2l3.6,7.59 -1.35,2.45C5.09,14.32 5,14.65 5,15c0,1.1 0.9,2 2,2h12v-2H7.42c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12 0.9,-1.63H19c0.75,0 1.41,-0.41 1.75,-1.03l3.58,-6.49C24.58,5.31 23.77,5 23,5H5.21L4.27,2H3z"/>
</vector>
```

- [ ] **Step 2: Create `ic_health_24.xml`** (heart — universally recognised for health)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5C2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3C19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"/>
</vector>
```

- [ ] **Step 3: Create `ic_salary_24.xml`** (banknote / payments)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M20,4H4C2.89,4 2,4.89 2,6v12c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V6C22,4.89 21.11,4 20,4zM20,18H4v-6h16V18zM20,8H4V6h16V8z"/>
  <path
      android:fillColor="@android:color/white"
      android:pathData="M6,14h2v2H6zM9,14h6v2H9z"/>
</vector>
```

- [ ] **Step 4: Verify the drawables compile**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_shopping_cart_24.xml app/src/main/res/drawable/ic_health_24.xml app/src/main/res/drawable/ic_salary_24.xml
git commit -m "feat: add shopping_cart, health, salary vector drawables"
```

---

## Task 2: Register new icons in KnownIconIds and PredefinedIconRepository

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/icons/KnownIconIds.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt`

- [ ] **Step 1: Add IDs to `KnownIconIds`**

Add three new entries at the bottom of the object:

```kotlin
val shoppingCart: Id.Known = Id("shopping_cart")
val health: Id.Known = Id("health")
val salary: Id.Known = Id("salary")
```

The full file should now be:

```kotlin
package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.Id

internal object KnownIconIds {
    val cash: Id.Known = Id("cash")
    val bank: Id.Known = Id("bank")
    val creditCard: Id.Known = Id("credit_card")
    val wallet: Id.Known = Id("wallet")
    val crypto: Id.Known = Id("crypto")

    val flowers: Id.Known = Id("flowers")
    val grocery: Id.Known = Id("grocery")
    val fastfood: Id.Known = Id("fastfood")
    val car: Id.Known = Id("car")
    val carRepair: Id.Known = Id("car_repair")
    val diamond: Id.Known = Id("diamond")
    val gameController: Id.Known = Id("game_controller")
    val book: Id.Known = Id("book")
    val movie: Id.Known = Id("movie")
    val beach: Id.Known = Id("beach")

    val shoppingCart: Id.Known = Id("shopping_cart")
    val health: Id.Known = Id("health")
    val salary: Id.Known = Id("salary")
}
```

- [ ] **Step 2: Add `health` IconCategory and register icons in `PredefinedIconRepository`**

In `PredefinedIconRepository.kt`, add a `health` category to the `Categories` object (after `education`):

```kotlin
val health = IconCategory("health", "Health")
```

Then add three entries to the `icons` map (after the existing `book` / `movie` / `beach` entries):

```kotlin
iconOf(id = KnownIconIds.shoppingCart, resourceName = "ic_shopping_cart_24", description = "Shopping cart", category = Categories.shopping),
iconOf(id = KnownIconIds.health, resourceName = "ic_health_24", description = "Health", category = Categories.health),
iconOf(id = KnownIconIds.salary, resourceName = "ic_salary_24", description = "Salary", category = Categories.moneyBanking),
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/icons/KnownIconIds.kt app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt
git commit -m "feat: register shopping_cart, health, salary icons"
```

---

## Task 3: PresetsConfigurationKey and PresetsUseCase interface

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsConfigurationKey.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsUseCase.kt`

- [ ] **Step 1: Create `PresetsConfigurationKey.kt`**

Follows the same pattern as `CurrencyConfigurationKey` in the `app` module.

```kotlin
package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class PresetsConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("presets") {

    object PresetsSeeded : PresetsConfigurationKey<Boolean>(
        name = "seeded",
        defaultValue = false,
    )
}
```

- [ ] **Step 2: Create `PresetsUseCase.kt`**

Public so `app` module can reference it in `ActivityComponent.Dependencies`.

```kotlin
package com.hluhovskyi.zero.presets

interface PresetsUseCase {
    suspend fun seed()
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :zero-core:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsConfigurationKey.kt zero-core/src/main/java/com/hluhovskyi/zero/presets/PresetsUseCase.kt
git commit -m "feat: add PresetsUseCase interface and PresetsConfigurationKey"
```

---

## Task 4: DefaultPresetsUseCase (TDD)

**Files:**
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/presets/DefaultPresetsUseCaseTest.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/presets/DefaultPresetsUseCase.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(MockitoJUnitRunner::class)
class DefaultPresetsUseCaseTest {

    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase
    @Mock private lateinit var configurationRepository: ConfigurationRepository

    private val currencyId = Id.Known("usd")
    private val currency = Currency(id = currencyId, name = "US Dollar", symbol = "$")

    private lateinit var useCase: DefaultPresetsUseCase

    @Before
    fun setUp() {
        useCase = DefaultPresetsUseCase(
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            configurationRepository = configurationRepository,
        )
    }

    @Test
    fun `seed inserts preset categories and accounts when not yet seeded`() = runTest {
        whenever(configurationRepository.observe(PresetsConfigurationKey.PresetsSeeded, Boolean::class))
            .thenReturn(emptyFlow())
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(currency)

        useCase.seed()

        val categoriesCaptor = argumentCaptor<List<CategoryRepository.CategoryInsert>>()
        verify(categoryRepository).insert(categoriesCaptor.capture())
        val categories = categoriesCaptor.firstValue

        assertEquals(7, categories.size)

        val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE }
        val incomeCategories = categories.filter { it.type == CategoryType.INCOME }
        assertEquals(5, expenseCategories.size)
        assertEquals(2, incomeCategories.size)

        assertTrue(expenseCategories.any { it.name == "Food & Drink" && it.iconId == Id("grocery") && it.colorId == Id("orange") })
        assertTrue(expenseCategories.any { it.name == "Transport" && it.iconId == Id("car") && it.colorId == Id("teal") })
        assertTrue(expenseCategories.any { it.name == "Shopping" && it.iconId == Id("shopping_cart") && it.colorId == Id("pink") })
        assertTrue(expenseCategories.any { it.name == "Entertainment" && it.iconId == Id("game_controller") && it.colorId == Id("purple") })
        assertTrue(expenseCategories.any { it.name == "Health" && it.iconId == Id("health") && it.colorId == Id("red") })

        assertTrue(incomeCategories.any { it.name == "Salary" && it.iconId == Id("salary") && it.colorId == Id("blue") })
        assertTrue(incomeCategories.any { it.name == "Other Income" })

        val accountsCaptor = argumentCaptor<List<AccountRepository.AccountInsert>>()
        verify(accountRepository).insert(accountsCaptor.capture())
        val accounts = accountsCaptor.firstValue

        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.name == "Bank" && it.iconId == Id("bank") && it.colorId == Id("blue") && it.category == AccountCategory.BANK && it.currencyId == currencyId })
        assertTrue(accounts.any { it.name == "Cash" && it.iconId == Id("cash") && it.colorId == Id("green") && it.category == AccountCategory.CASH && it.currencyId == currencyId })
    }

    @Test
    fun `seed writes seeded flag after inserting`() = runTest {
        whenever(configurationRepository.observe(PresetsConfigurationKey.PresetsSeeded, Boolean::class))
            .thenReturn(emptyFlow())
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(currency)

        useCase.seed()

        verify(configurationRepository).write(PresetsConfigurationKey.PresetsSeeded, Boolean::class, true)
    }

    @Test
    fun `seed is a no-op when already seeded`() = runTest {
        whenever(configurationRepository.observe(PresetsConfigurationKey.PresetsSeeded, Boolean::class))
            .thenReturn(kotlinx.coroutines.flow.flowOf(true))

        useCase.seed()

        verify(categoryRepository, never()).insert(any<List<CategoryRepository.CategoryInsert>>())
        verify(accountRepository, never()).insert(any<List<AccountRepository.AccountInsert>>())
        verify(configurationRepository, never()).write(any(), any(), any())
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.presets.DefaultPresetsUseCaseTest" 2>&1 | tail -20
```
Expected: FAILED — `DefaultPresetsUseCase` does not exist yet.

- [ ] **Step 3: Implement `DefaultPresetsUseCase`**

```kotlin
package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.firstOrDefault
import com.hluhovskyi.zero.config.write
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase

internal class DefaultPresetsUseCase(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val configurationRepository: ConfigurationRepository,
) : PresetsUseCase {

    override suspend fun seed() {
        val seeded = configurationRepository.firstOrDefault(PresetsConfigurationKey.PresetsSeeded)
        if (seeded) return

        val currencyId = currencyPrimaryUseCase.getPrimaryCurrency().id
        categoryRepository.insert(presetCategories())
        accountRepository.insert(presetAccounts(currencyId))
        configurationRepository.write(PresetsConfigurationKey.PresetsSeeded, true)
    }

    private fun presetCategories(): List<CategoryRepository.CategoryInsert> = listOf(
        categoryInsert(name = "Food & Drink", iconId = "grocery", colorId = "orange", type = CategoryType.EXPENSE),
        categoryInsert(name = "Transport", iconId = "car", colorId = "teal", type = CategoryType.EXPENSE),
        categoryInsert(name = "Shopping", iconId = "shopping_cart", colorId = "pink", type = CategoryType.EXPENSE),
        categoryInsert(name = "Entertainment", iconId = "game_controller", colorId = "purple", type = CategoryType.EXPENSE),
        categoryInsert(name = "Health", iconId = "health", colorId = "red", type = CategoryType.EXPENSE),
        categoryInsert(name = "Salary", iconId = "salary", colorId = "blue", type = CategoryType.INCOME),
        categoryInsert(name = "Other Income", iconId = null, colorId = "grey", type = CategoryType.INCOME),
    )

    private fun presetAccounts(currencyId: Id.Known): List<AccountRepository.AccountInsert> = listOf(
        AccountRepository.AccountInsert(
            name = "Bank",
            currencyId = currencyId,
            iconId = Id("bank"),
            colorId = Id("blue"),
            initialBalance = Amount.zero(),
            category = AccountCategory.BANK,
        ),
        AccountRepository.AccountInsert(
            name = "Cash",
            currencyId = currencyId,
            iconId = Id("cash"),
            colorId = Id("green"),
            initialBalance = Amount.zero(),
            category = AccountCategory.CASH,
        ),
    )

    private fun categoryInsert(
        name: String,
        iconId: String?,
        colorId: String,
        type: CategoryType,
    ) = CategoryRepository.CategoryInsert(
        parentCategoryId = Id.Unknown,
        name = name,
        iconId = iconId?.let { Id(it) } ?: Id.Unknown,
        colorId = Id(colorId),
        type = type,
    )
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.presets.DefaultPresetsUseCaseTest" 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/presets/DefaultPresetsUseCase.kt zero-core/src/test/java/com/hluhovskyi/zero/presets/DefaultPresetsUseCaseTest.kt
git commit -m "feat: implement DefaultPresetsUseCase with tests"
```

---

## Task 5: DI wiring — ApplicationComponent

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`

`ApplicationComponent` already provides `CategoryRepository`, `AccountRepository`, `ConfigurationRepository`, and `CurrencyPrimaryUseCase`. We just need to add a `@Provides` for `PresetsUseCase` and expose it via `ActivityComponent.Dependencies`.

- [ ] **Step 1: Add import and `@Provides` method to `ApplicationComponent.Module`**

Add to the imports at the top of `ApplicationComponent.kt`:
```kotlin
import com.hluhovskyi.zero.presets.DefaultPresetsUseCase
import com.hluhovskyi.zero.presets.PresetsUseCase
```

Add this method inside `ApplicationComponent.Module` (the `object Module` block), after the `categoryQueryUseCase` provider:

```kotlin
@Provides
@ApplicationScope
fun presetsUseCase(
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    configurationRepository: ConfigurationRepository,
): PresetsUseCase = DefaultPresetsUseCase(
    categoryRepository = categoryRepository,
    accountRepository = accountRepository,
    currencyPrimaryUseCase = currencyPrimaryUseCase,
    configurationRepository = configurationRepository,
)
```

- [ ] **Step 2: Expose `presetsUseCase` in `ActivityComponent.Dependencies`**

`ApplicationComponent` implements `ActivityComponent.Dependencies`. Add `val presetsUseCase: PresetsUseCase` to the `ActivityComponent.Dependencies` interface in `ActivityComponent.kt` (Task 6 below adds this — do it as part of Task 6 to keep changes atomic).

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL (will fail until Task 6 adds the `Dependencies` entry — do both tasks before verifying).

---

## Task 6: DI wiring — ActivityComponent

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`

- [ ] **Step 1: Add import**

Add to the imports at the top of `ActivityComponent.kt`:
```kotlin
import com.hluhovskyi.zero.presets.PresetsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Add `presetsUseCase` to `ActivityComponent.Dependencies`**

Inside the `interface Dependencies` block, add after `settingsComponentBuilder`:
```kotlin
val presetsUseCase: PresetsUseCase
```

- [ ] **Step 3: Override `attach()` in `ActivityComponent`**

Replace:
```kotlin
override fun attach(): Closeable = Closeables.empty()
```
with:
```kotlin
abstract val presetsUseCase: PresetsUseCase

override fun attach(): Closeable = Closeables.of {
    CoroutineScope(Dispatchers.IO).launch {
        presetsUseCase.seed()
    }
}
```

The `abstract val presetsUseCase` is a Dagger provision method — Dagger's generated `DaggerActivityComponent` will satisfy it from `ActivityComponent.Dependencies` automatically.

- [ ] **Step 4: Verify the full app compiles**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 6: Run lint**

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt
git commit -m "feat: wire PresetsUseCase into ActivityComponent.attach()"
```

---

## Verification

After all tasks are committed:

- [ ] Run `./gradlew testDebugUnitTest` — all tests pass
- [ ] Run `./gradlew lintDebug` — no errors
- [ ] Install app on a fresh emulator (or after clearing data) — `DefaultPresetsUseCase.seed()` runs; Bank + Cash accounts and 7 categories appear in the respective list screens
- [ ] Restart the app — no duplicate accounts or categories are created (idempotency confirmed)
