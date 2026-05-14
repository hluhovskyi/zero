# E2E Robot Tests — Design Spec

**Date:** 2026-05-13  
**Issue:** #180

## Goal

Establish the project's convention for end-to-end integration tests using the Robot pattern.
Phase 1 delivers one working test (add transaction → verify it appears in the list) plus all
the infrastructure needed to write more. Phase 2 (separate task) adds `ImportFlowRobotTest`
covering the 6 scenarios from issue #180.

---

## Architecture

### Test location

`app/src/androidTest/java/com/hluhovskyi/zero/`

The `app` module already depends on every other module, so robots have access to all public
APIs. The `androidTest` sourceset sees `internal` members from `app/src/main` (same module).

### Test framework

**AndroidX Compose Test** — `createAndroidComposeRule<MainActivity>()`.

Launches the real installed APK: real `MainApplication`, real `ApplicationComponent`, real
Room database on device. Robots drive the real Compose UI via Compose semantics
(`onNodeWithTag`, `onNodeWithText`, `onNodeWithContentDescription`). Tests run on a connected
device or emulator; they are instrumented tests (`./gradlew connectedDebugAndroidTest`).

### Database isolation

**TestBridge pattern** (see below). Before each test the bridge calls
`RoomDatabase.clearAllTables()` on the live database instance that the app already has open.
No file deletion, no process restart, no in-memory override.

---

## TestBridge

### Production seam (`app/src/main`)

Two small additions to existing production files:

**`TestBridge.kt`** (new file):
```kotlin
interface TestBridge {
    fun clearData()

    companion object {
        var installed: TestBridge? = null
    }
}
```

`installed` is always `null` in production — no production code ever reads or calls it.

**`DatabaseComponent`** (`zero-database`) — expose `RoomDatabase` (public Jetpack type):
```kotlin
interface DatabaseComponent {
    // ... existing members ...
    val roomDatabase: RoomDatabase
}
// + one @Provides in Module: fun roomDatabase(db: MainDatabase): RoomDatabase = db
```

**`ApplicationComponent`** (`app`) — expose the already-provided `DatabaseComponent`:
```kotlin
abstract class ApplicationComponent : ... {
    // ... existing members ...
    abstract val databaseComponent: DatabaseComponent
}
```

### Test implementation (`app/src/androidTest`)

```kotlin
class DatabaseTestBridge(private val db: RoomDatabase) : TestBridge {
    override fun clearData() {
        db.clearAllTables()
    }
}
```

Installed in a shared `@Before`:
```kotlin
@Before fun setUp() {
    val component = (applicationContext as HasApplicationComponent).applicationComponent
    TestBridge.installed = DatabaseTestBridge(component.databaseComponent.roomDatabase)
    TestBridge.installed!!.clearData()
}
```

### Future extension point

`TestBridge` will gain a `fun uploadFixture(fixture: TestFixture)` method (separate task) for
seeding the database with pre-defined data before import-flow tests.

---

## Robot Pattern

Robots encapsulate *user intent*, not UI selectors. Each method returns `this` (or a
different robot for navigation) so tests read as fluent prose.

### File structure

```
app/src/androidTest/java/com/hluhovskyi/zero/
  robots/
    TransactionsRobot.kt       ← home list: assertions + navigation
    TransactionEditRobot.kt    ← add/edit form: fill + save/discard
  ZeroE2eTest.kt               ← first test class
```

### `TransactionsRobot`

Wraps the home transactions list.

```kotlin
class TransactionsRobot(private val composeRule: ComposeTestRule) {
    fun tapAddTransaction(): TransactionEditRobot
    fun assertTransactionCount(count: Int): TransactionsRobot
    fun assertHasExpense(amount: String): TransactionsRobot
}
```

### `TransactionEditRobot`

Wraps the add/edit transaction form.

```kotlin
class TransactionEditRobot(private val composeRule: ComposeTestRule) {
    fun fillExpense(amount: String, category: String, account: String): TransactionEditRobot
    fun save(): TransactionsRobot
    fun discard(): TransactionsRobot
}
```

### Selector strategy

Use `onNodeWithText()` and `onNodeWithContentDescription()` wherever elements already carry
meaningful text. Add `Modifier.testTag("Screen.element")` annotations to production Compose
code only where text-based selectors are ambiguous (e.g. amount field vs. rate field).
Tag naming convention: `TransactionList.addButton`, `TransactionEdit.amountField`, etc.

---

## New Test Dependencies

Add to `app/build.gradle`:

```groovy
androidTestImplementation "androidx.test.ext:junit:1.1.5"
androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.7.8"
androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
debugImplementation "androidx.compose.ui:ui-test-manifest:1.7.8"
```

---

## Phase 1 Test Scope

One test class (`ZeroE2eTest`), two tests:

1. **`transaction list is empty on fresh install`** — baseline sanity check; asserts empty
   state message is visible after `clearData()`.

2. **`add expense appears in transaction list`** — full happy path:
   open add-transaction form → fill expense (amount + category + account) → save →
   assert transaction count = 1 → assert the entered amount is visible in the list.

---

## Out of Scope (Phase 2)

- `ImportFlowRobotTest` covering the 6 scenarios from issue #180
- `TestBridge.uploadFixture()` for seeding pre-defined data
- Compose UI tests for layout/interaction (issue #180 "Compose UI tests next?" section)
- CI workflow changes (measure test duration first)
