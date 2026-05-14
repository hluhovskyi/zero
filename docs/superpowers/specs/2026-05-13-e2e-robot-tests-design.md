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

The `app` module already depends on every other module, so it can reach all public APIs.
The `androidTest` sourceset sees `internal` members from `app/src/main` (same module).

### Test framework

**AndroidX Compose Test** — `createAndroidComposeRule<MainActivity>()`.

Launches the real installed APK: real `MainApplication`, real `ApplicationComponent`, real
Room database on device. Robots drive the real Compose UI via Compose semantics
(`onNodeWithTag`, `onNodeWithText`, `onNodeWithContentDescription`). Tests run on a connected
device or emulator; they are instrumented tests (`./gradlew connectedDebugAndroidTest`).

### Database isolation

**TestBridge pattern** (see below). Before each test the bridge calls
`RoomDatabase.clearAllTables()` on the live database instance the app already has open.
No file deletion, no process restart, no in-memory override.

---

## Module Structure

### New module: `:zero-test-bridge`

A standalone Android library module. Its purpose is to provide the `TestBridge` interface
and all concrete implementations. It can depend on zero-database, zero-core, zero-api, or any
other project module it needs to implement bridges — but it never depends on `:app` (circular)
and it cannot access `androidTest` sourcesets.

```
zero-test-bridge/
  src/main/java/com/hluhovskyi/zero/testbridge/
    TestBridge.kt          ← interface — the only type test files import
    DatabaseTestBridge.kt  ← implementation backed by RoomDatabase
```

The `app/build.gradle` declares:
```groovy
androidTestImplementation project(':zero-test-bridge')
```

Test files import only from `com.hluhovskyi.zero.testbridge`. They never import
`RoomDatabase`, `TransactionRepository`, or any other zero-api / zero-core / Room type.

---

## TestBridge

### Interface (`zero-test-bridge/src/main`)

```kotlin
interface TestBridge {
    fun clearData()
    // Future: fun uploadFixture(fixture: TestFixture)
}
```

### Implementation (`zero-test-bridge/src/main`)

```kotlin
class DatabaseTestBridge(private val db: RoomDatabase) : TestBridge {
    override fun clearData() {
        db.clearAllTables()
    }
}
```

`RoomDatabase` is a public Jetpack type — no internal types leak from zero-database.
The implementation receives the already-open database instance (see wiring below).

### Future extension point

`TestBridge` will gain `fun uploadFixture(fixture: TestFixture)` (separate task) for seeding
the database before import-flow tests. `TestFixture` will be defined in `:zero-test-bridge`
with its own data model — tests never reference zero-api domain types directly.

---

## Wiring layer (`app/androidTest`)

One abstract base class resolves the bridge from the app's DI graph. This is the only
place in the test codebase that knows about `HasApplicationComponent`, `DatabaseComponent`,
and `RoomDatabase`. User-written `@Test` methods extend this class and see only `TestBridge`.

```kotlin
abstract class BaseE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val bridge: TestBridge by lazy {
        val component = (applicationContext as HasApplicationComponent).applicationComponent
        DatabaseTestBridge(component.databaseComponent.roomDatabase)
    }

    @Before
    fun setUp() {
        bridge.clearData()
    }
}
```

### Production changes required to close the access gap

Two small additions to existing files:

**`DatabaseComponent` (zero-database)** — expose `RoomDatabase` (public Jetpack type):
```kotlin
interface DatabaseComponent {
    // ... existing members ...
    val roomDatabase: RoomDatabase
}
// + one @Provides in Module: fun roomDatabase(db: MainDatabase): RoomDatabase = db
```

**`ApplicationComponent` (app)** — expose the already-provided `DatabaseComponent`:
```kotlin
abstract class ApplicationComponent : ... {
    // ... existing members ...
    abstract val databaseComponent: DatabaseComponent
}
```

### Module boundary enforcement

The boundary "test code imports only from `:zero-test-bridge`" and "`:zero-test-bridge`
never imports from `androidTest` sourcesets" can be enforced mechanically with a custom
lint rule or Gradle dependency check if drift becomes a problem. Treat this as an available
safety net — add it when violations appear, not upfront.

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
  BaseE2eTest.kt               ← wiring: bridge + composeRule
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

Use `onNodeWithText()` and `onNodeWithContentDescription()` where elements already carry
meaningful text. Add `Modifier.testTag("Screen.element")` to production Compose code only
where text-based selectors are ambiguous (e.g. amount field vs. rate field). Tag naming
convention: `TransactionList.addButton`, `TransactionEdit.amountField`, etc.

---

## New Test Dependencies

`app/build.gradle`:
```groovy
androidTestImplementation project(':zero-test-bridge')
androidTestImplementation "androidx.test.ext:junit:1.1.5"
androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.7.8"
androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
debugImplementation "androidx.compose.ui:ui-test-manifest:1.7.8"
```

`zero-test-bridge/build.gradle`:
```groovy
// depends on whatever each bridge implementation needs
implementation "androidx.room:room-runtime:$room_version"  // for RoomDatabase type
```

---

## Phase 1 Test Scope

One test class (`ZeroE2eTest extends BaseE2eTest`), two tests:

1. **`transaction list is empty on fresh install`** — baseline sanity; asserts empty-state
   message is visible after `clearData()`.

2. **`add expense appears in transaction list`** — full happy path:
   open add-transaction form → fill expense (amount + category + account) → save →
   assert transaction count = 1 → assert the entered amount is visible in the list.

```kotlin
class ZeroE2eTest : BaseE2eTest() {

    @Test
    fun `transaction list is empty on fresh install`() {
        onTransactions()
            .assertEmpty()
    }

    @Test
    fun `add expense appears in transaction list`() {
        onTransactions()
            .tapAddTransaction()
            .fillExpense(amount = "42", category = "Food", account = "Wallet")
            .save()
            .assertTransactionCount(1)
            .assertHasExpense(amount = "42")
    }

    private fun onTransactions() = TransactionsRobot(composeRule)
}
```

---

## Out of Scope (Phase 2)

- `ImportFlowRobotTest` covering the 6 scenarios from issue #180
- `TestBridge.uploadFixture()` for seeding pre-defined data
- Compose layout/interaction tests (issue #180 "Compose UI tests next?" section)
- CI workflow changes (measure test duration first)
