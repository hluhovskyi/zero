# E2E Robot Test Infrastructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up `:zero-test-bridge`, `TestBridge`/`DatabaseTestBridge`, `BaseE2eTest`, robots, and two passing E2E tests on device.

**Architecture:** New `:zero-test-bridge` library module holds `TestBridge` interface + `DatabaseTestBridge` impl; two minimal additions to production (`DatabaseComponent.roomDatabase`, `ApplicationComponent.databaseComponent`); `BaseE2eTest` in `app/androidTest` wires the bridge from the live DI graph; robots drive the Compose UI; tests run via `./gradlew connectedDebugAndroidTest`.

**Tech Stack:** `androidx.compose.ui:ui-test-junit4`, `androidx.room:room-runtime` (RoomDatabase type), JUnit 4, Kotlin coroutines, Dagger.

**Spec:** `docs/superpowers/specs/2026-05-13-e2e-robot-tests-design.md`

---

### File map

| File | Action |
|------|--------|
| `settings.gradle` | add `include ':zero-test-bridge'` |
| `zero-test-bridge/build.gradle` | new — library module |
| `zero-test-bridge/.gitignore` | new — `/build` |
| `zero-test-bridge/src/main/…/testbridge/TestBridge.kt` | new — interface |
| `zero-test-bridge/src/main/…/testbridge/DatabaseTestBridge.kt` | new — impl |
| `zero-database/src/main/…/DatabaseComponent.kt` | add `roomDatabase: RoomDatabase` |
| `app/src/main/…/ApplicationComponent.kt` | add `databaseComponent: DatabaseComponent` |
| `app/build.gradle` | add androidTest + debug deps |
| `app/src/androidTest/…/BaseE2eTest.kt` | new — wiring layer |
| `app/src/androidTest/…/robots/TransactionsRobot.kt` | new |
| `app/src/androidTest/…/robots/TransactionEditRobot.kt` | new |
| `app/src/androidTest/…/ZeroE2eTest.kt` | new — two tests |
| `zero-ui/src/main/…/AmountDisplay.kt` | add `testTag("TransactionEdit.amountField")` |

---

### Task 1: Register `:zero-test-bridge` module

**Files:**
- Modify: `settings.gradle`
- Create: `zero-test-bridge/build.gradle`
- Create: `zero-test-bridge/.gitignore`
- Create: `zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/.gitkeep`

- [ ] Add `include ':zero-test-bridge'` to `settings.gradle` after the last `include` line.

- [ ] Create `zero-test-bridge/.gitignore`:
```
/build
```

- [ ] Create `zero-test-bridge/build.gradle`:
```groovy
plugins {
    id 'com.android.library'
}

android {
    compileSdk versions.compileSdk
    namespace 'com.hluhovskyi.zero.testbridge'

    defaultConfig {
        minSdk versions.minSdk
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}

dependencies {
    implementation deps.room.runtime       // RoomDatabase type
    implementation deps.kotlinxDatetime

    implementation project(':zero-api')    // AccountRepository, CategoryRepository, etc.
}
```

- [ ] Verify sync: `./gradlew :zero-test-bridge:assembleDebug`
Expected: BUILD SUCCESSFUL (empty module compiles fine)

- [ ] Commit:
```bash
git add settings.gradle zero-test-bridge/
git commit -m "build: add :zero-test-bridge module scaffold"
```

---

### Task 2: Define `TestBridge` interface

**Files:**
- Create: `zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/TestBridge.kt`

- [ ] Create `TestBridge.kt`:
```kotlin
package com.hluhovskyi.zero.testbridge

interface TestBridge {
    suspend fun clearData()
    suspend fun seedDefaultSetup()
}
```

- [ ] Verify: `./gradlew :zero-test-bridge:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-test-bridge/src/
git commit -m "feat(test-bridge): add TestBridge interface"
```

---

### Task 3: Expose `roomDatabase` from `DatabaseComponent`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`

The `MainDatabase` type is `internal` to `zero-database`. We expose it as its public parent `RoomDatabase` so callers outside the module can call `clearAllTables()`.

- [ ] Add `val roomDatabase: RoomDatabase` to the `DatabaseComponent` interface (after the existing sync sink/source declarations):
```kotlin
val roomDatabase: RoomDatabase
```

- [ ] Add a `@Provides` entry inside `DatabaseComponent.Module` object that upcasts `MainDatabase`:
```kotlin
@Provides
@DatabaseScope
internal fun roomDatabase(db: MainDatabase): RoomDatabase = db
```

- [ ] Verify: `./gradlew :zero-database:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt
git commit -m "feat(database): expose RoomDatabase from DatabaseComponent"
```

---

### Task 4: Expose `databaseComponent` from `ApplicationComponent`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`

- [ ] Add `abstract val databaseComponent: DatabaseComponent` to `ApplicationComponent` (after `abstract val logger: Logger`):
```kotlin
abstract val databaseComponent: DatabaseComponent
```

- [ ] Verify: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "feat(app): expose databaseComponent from ApplicationComponent"
```

---

### Task 5: Implement `DatabaseTestBridge`

**Files:**
- Create: `zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/DatabaseTestBridge.kt`

`seedDefaultSetup()` inserts known-ID test data so robots can reference "Wallet" and "Food" by name without querying. It waits for the auto-created user via `currentUserRepository.query().first()` before inserting, since Room auto-creates a user on first query but does so asynchronously after `clearAllTables()`.

- [ ] Create `DatabaseTestBridge.kt`:
```kotlin
package com.hluhovskyi.zero.testbridge

import androidx.room.RoomDatabase
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

class DatabaseTestBridge(
    private val db: RoomDatabase,
    private val currentUserRepository: CurrentUserRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : TestBridge {

    override suspend fun clearData() {
        db.clearAllTables()
    }

    override suspend fun seedDefaultSetup() {
        // Wait for the auto-recreated user (RoomCurrentUserRepository inserts one on first query)
        currentUserRepository.query().first()

        val iconId = IconRepository.unknownCategoryIconId()
        val colorId = Id.Known("blue")
        val currencyId = Id.Known("USD")
        val accountId = Id.Known("test-account")
        val categoryId = Id.Known("test-category")
        val epoch = LocalDateTime(2020, 1, 1, 0, 0)

        accountRepository.insert(
            AccountRepository.AccountInsert(
                id = accountId,
                name = "Wallet",
                currencyId = currencyId,
                iconId = iconId,
                colorId = colorId,
                initialBalance = Amount(BigDecimal.ZERO),
                category = AccountCategory.CASH,
            )
        )

        categoryRepository.insert(
            CategoryRepository.CategoryInsert(
                id = categoryId,
                parentCategoryId = Id.Unknown,
                name = "Food",
                iconId = iconId,
                colorId = colorId,
                type = CategoryType.EXPENSE,
            )
        )

        // Insert bootstrap transaction so the app shows the transaction list (not welcome screen).
        // isNewUser = !hasAnyTransactions — see DefaultNewUserUseCase.
        transactionRepository.insert(
            TransactionRepository.Transaction.Expense(
                id = Id.Known("bootstrap"),
                amount = Amount(BigDecimal.ONE),
                accountId = accountId,
                currencyId = currencyId,
                dateTime = epoch,
                updatedDateTime = epoch,
                categoryId = categoryId,
                rate = Rate.Same,
            )
        )
    }
}
```

- [ ] Verify: `./gradlew :zero-test-bridge:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-test-bridge/src/
git commit -m "feat(test-bridge): implement DatabaseTestBridge"
```

---

### Task 6: Add test dependencies to `app/build.gradle`

**Files:**
- Modify: `app/build.gradle`

- [ ] Add to the `dependencies` block:
```groovy
androidTestImplementation project(':zero-test-bridge')
androidTestImplementation "androidx.test.ext:junit:1.1.5"
androidTestImplementation "androidx.compose.ui:ui-test-junit4:${versions.compose}"
androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
debugImplementation "androidx.compose.ui:ui-test-manifest:${versions.compose}"
```

- [ ] Verify: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/build.gradle
git commit -m "build(app): add androidTest and ui-test-manifest dependencies"
```

---

### Task 7: Create `BaseE2eTest`

**Files:**
- Create: `app/src/androidTest/java/com/hluhovskyi/zero/BaseE2eTest.kt`

`BaseE2eTest` is the only place in test code that imports `HasApplicationComponent`, `ApplicationComponent`, `DatabaseComponent`. All user-written `@Test` files extend this class and import only from `:zero-test-bridge` and `robots/`.

- [ ] Create `BaseE2eTest.kt`:
```kotlin
package com.hluhovskyi.zero

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.testbridge.DatabaseTestBridge
import com.hluhovskyi.zero.testbridge.TestBridge
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule

abstract class BaseE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val bridge: TestBridge by lazy {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dbComponent = (app as HasApplicationComponent).applicationComponent.databaseComponent
        DatabaseTestBridge(
            db = dbComponent.roomDatabase,
            currentUserRepository = dbComponent.currentUserRepository,
            accountRepository = dbComponent.accountRepository,
            categoryRepository = dbComponent.categoryRepository,
            transactionRepository = dbComponent.transactionRepository,
        )
    }

    @Before
    fun setUp() = runBlocking {
        bridge.clearData()
    }

    protected fun seedDefaultSetup() = runBlocking {
        bridge.seedDefaultSetup()
    }
}
```

- [ ] Verify: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/src/androidTest/java/com/hluhovskyi/zero/BaseE2eTest.kt
git commit -m "feat(e2e): add BaseE2eTest with bridge wiring"
```

---

### Task 8: Add `testTag` to `AmountDisplay`

The `AmountDisplay` composable uses `BasicTextField` which has no stable text selector. Add a `testTag` so robots can reliably target it.

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountDisplay.kt`

- [ ] Locate the `BasicTextField` call in `AmountDisplay.kt` (around line 117). Add `Modifier.testTag("TransactionEdit.amountField")` to its `modifier` parameter. The existing modifier chain should be extended:

Find the `BasicTextField` call and add the tag to its modifier:
```kotlin
BasicTextField(
    modifier = modifier.testTag("TransactionEdit.amountField"),
    // ... rest unchanged
```

If the `BasicTextField` already has a modifier, chain it: `existingModifier.testTag("TransactionEdit.amountField")`.

Import `androidx.compose.ui.platform.testTag` is provided automatically via the `ui` dependency.

- [ ] Verify: `./gradlew :zero-ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountDisplay.kt
git commit -m "feat(ui): add testTag to AmountDisplay for E2E tests"
```

---

### Task 9: Create `TransactionsRobot`

**Files:**
- Create: `app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionsRobot.kt`

UI notes:
- FAB has `contentDescription = "Add transaction"` (from `R.string.transaction_add`)
- Welcome screen heading: "Your finances," (first line of `R.string.welcome_heading`)

- [ ] Create `TransactionsRobot.kt`:
```kotlin
package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class TransactionsRobot(private val composeRule: ComposeTestRule) {

    fun assertWelcomeScreenVisible(): TransactionsRobot {
        composeRule.onNodeWithText("Your finances,", substring = true).assertIsDisplayed()
        return this
    }

    fun tapAddTransaction(): TransactionEditRobot {
        composeRule.onNodeWithContentDescription("Add transaction").performClick()
        return TransactionEditRobot(composeRule)
    }

    fun assertHasExpense(amount: String): TransactionsRobot {
        composeRule.onNodeWithText(amount, substring = true).assertIsDisplayed()
        return this
    }
}
```

- [ ] Verify: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionsRobot.kt
git commit -m "feat(e2e): add TransactionsRobot"
```

---

### Task 10: Create `TransactionEditRobot`

**Files:**
- Create: `app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionEditRobot.kt`

UI notes:
- Amount field: `testTag("TransactionEdit.amountField")` (added in Task 8)
- Category chip: `onNodeWithText(category)` in the horizontal scroll row
- Account selector: click `onNodeWithText("ACCOUNT")` (SelectorCard label uppercased) to open dropdown, then click the account name
- Save FAB: `contentDescription = "Save Transaction"` (from `R.string.transaction_edit_save`)

- [ ] Create `TransactionEditRobot.kt`:
```kotlin
package com.hluhovskyi.zero.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement

class TransactionEditRobot(private val composeRule: ComposeTestRule) {

    fun fillExpense(amount: String, category: String, account: String): TransactionEditRobot {
        composeRule.onNodeWithTag("TransactionEdit.amountField")
            .performClick()
            .performTextReplacement(amount)

        composeRule.onNodeWithText(category).performClick()

        // SelectorCard renders its label uppercased; tap it to open the dropdown
        composeRule.onNodeWithText("ACCOUNT").performClick()
        composeRule.onNodeWithText(account).performClick()

        return this
    }

    fun save(): TransactionsRobot {
        composeRule.onNodeWithContentDescription("Save Transaction").performClick()
        return TransactionsRobot(composeRule)
    }
}
```

- [ ] Verify: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionEditRobot.kt
git commit -m "feat(e2e): add TransactionEditRobot"
```

---

### Task 11: Write and run the two E2E tests

**Files:**
- Create: `app/src/androidTest/java/com/hluhovskyi/zero/ZeroE2eTest.kt`

Test 1: `@Before` clears data → app lands on welcome screen → assert it's visible.  
Test 2: `seedDefaultSetup()` creates Wallet account + Food category + bootstrap transaction → tap FAB → fill "42" expense → save → assert "42" visible in list.

- [ ] Create `ZeroE2eTest.kt`:
```kotlin
package com.hluhovskyi.zero

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hluhovskyi.zero.robots.TransactionsRobot
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZeroE2eTest : BaseE2eTest() {

    @Test
    fun `fresh install shows welcome screen`() {
        // @Before already called clearData() — no transactions means welcome screen
        onTransactions().assertWelcomeScreenVisible()
    }

    @Test
    fun `add expense appears in transaction list`() {
        seedDefaultSetup()

        onTransactions()
            .tapAddTransaction()
            .fillExpense(amount = "42", category = "Food", account = "Wallet")
            .save()
            .assertHasExpense(amount = "42")
    }

    private fun onTransactions() = TransactionsRobot(composeRule)
}
```

- [ ] Run the tests on a connected device:
```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.hluhovskyi.zero.ZeroE2eTest" 2>&1 | tail -30
```
Expected: both tests PASS

If `fresh install shows welcome screen` fails with "node not found": the welcome screen text uses a newline — try `substring = true` (already set) or check the actual rendered string via `composeRule.onRoot().printToLog("UI")`.

If `add expense appears in transaction list` fails at amount entry: verify the `testTag` was applied to the correct node in `AmountDisplay.kt`.

If account dropdown is not found: `SelectorCard` renders label as `label.uppercase()` → the node text is "ACCOUNT" (all caps). Confirm with `composeRule.onRoot().printToLog("UI")` to inspect the semantic tree.

- [ ] Commit:
```bash
git add app/src/androidTest/java/com/hluhovskyi/zero/ZeroE2eTest.kt
git commit -m "test(e2e): add ZeroE2eTest — welcome screen and add expense"
git push
```

---

### Task 12: Lint check + update PR

- [ ] Run lint to ensure no regressions:
```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```
Expected: no new errors

- [ ] Update the PR (already open as draft from planning session):
```bash
gh pr edit 203 \
  --title "feat: E2E robot test infrastructure (issue #180)" \
  --body "$(cat <<'EOF'
## Summary
- Adds :zero-test-bridge module with TestBridge interface and DatabaseTestBridge
- Exposes roomDatabase from DatabaseComponent and databaseComponent from ApplicationComponent
- BaseE2eTest wires the bridge; test files only import from :zero-test-bridge and robots/
- TransactionsRobot and TransactionEditRobot drive the real Compose UI
- Two passing E2E tests: welcome screen on fresh install, add expense appears in list

## Test plan
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` on a device — both tests pass
- [ ] Run `./gradlew lintDebug` — no new errors

Closes #180 (Phase 1)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr ready 203
```
