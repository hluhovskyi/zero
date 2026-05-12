# Account Archival Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add archive/unarchive to accounts — accessible from the long-press dropdown on the list screen and the three-dots menu on the detail screen.

**Architecture:** `AccountRepository` gains `archive(id)` / `unarchive(id)` methods backed by a new nullable `archivedAt` column (DB migration 4→5). Both `AccountViewModel` and `AccountDetailViewModel` gain corresponding `Archive`/`Unarchive` actions; `Account` exposes `archivedAt` so the UI can show the correct menu item. Archived accounts continue to render normally (follow-up will filter them).

**Tech Stack:** Kotlin, Room (upsert pattern), kotlinx-datetime, Jetpack Compose, Dagger, material-icons-extended

---

### Task 1: Data layer — API + Room migration + DAO + Repository

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountEntity.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountMigrations.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountRoom.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/RoomAccountRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`

- [ ] **Step 1: Add `archivedAt` to `AccountRepository.Account` and new methods to interface**

In `AccountRepository.kt`:
```kotlin
data class Account(
    override val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
    val iconId: Id.Known,
    val colorId: Id = Id.Unknown,
    val initialBalance: Amount,
    val category: AccountCategory,
    val details: String?,
    val archivedAt: LocalDateTime? = null,  // add this
) : Identifiable
```

Add to the interface (before `Noop`):
```kotlin
suspend fun archive(id: Id.Known)
suspend fun unarchive(id: Id.Known)
```

Add to `Noop`:
```kotlin
override suspend fun archive(id: Id.Known) = Unit
override suspend fun unarchive(id: Id.Known) = Unit
```

Add import: `import kotlinx.datetime.LocalDateTime`

- [ ] **Step 2: Add `archivedAt` column to `AccountEntity`**

In `AccountEntity.kt`, add the field (after `deletedAt`):
```kotlin
val archivedAt: LocalDateTime? = null,
```

- [ ] **Step 3: Add DB migration 4→5**

In `AccountMigrations.kt`, add:
```kotlin
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN archivedAt TEXT")
    }
}
```

- [ ] **Step 4: Bump database version**

In `MainDatabase.kt`, change:
```kotlin
private const val MAIN_DATABASE_VERSION = 5
```

- [ ] **Step 5: Add one-shot DAO query for read-modify-write**

In `AccountRoom.kt`, add:
```kotlin
@Query("SELECT * FROM AccountEntity WHERE userId=:userId AND id=:id LIMIT 1")
suspend fun selectByIdOnce(userId: String, id: String): AccountEntity?
```

- [ ] **Step 6: Implement `archive` / `unarchive` in `RoomAccountRepository`**

Add `clock: Clock` and `zoneProvider: ZoneProvider` constructor parameters.

Add imports:
```kotlin
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
```

Change constructor signature:
```kotlin
internal class RoomAccountRepository(
    private val accountRoom: () -> AccountRoom,
    private val currentUserId: Flow<Id.Known>,
    private val idGenerator: IdGenerator,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : AccountRepository {
```

Add `archivedAt` to `toDomain()` mapping:
```kotlin
private fun List<AccountEntity>.toDomain(): List<AccountRepository.Account> = map { account ->
    AccountRepository.Account(
        id = account.id,
        name = account.name,
        currencyId = account.currencyId,
        iconId = account.iconId,
        colorId = Id(account.colorId),
        initialBalance = Amount(account.initialBalance.value),
        category = AccountCategory.from(account.category),
        details = account.details,
        archivedAt = account.archivedAt,
    )
}
```

Add at the end of the class (before the closing brace):
```kotlin
override suspend fun archive(id: Id.Known) {
    incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
        val entity = accountRoom().selectByIdOnce(userId.value, id.value) ?: return@requireCurrentUserId
        val now = clock.localDateTime(zoneProvider.timeZone())
        accountRoom().insert(entity.copy(archivedAt = now, updatedDateTime = now))
    }
}

override suspend fun unarchive(id: Id.Known) {
    incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
        val entity = accountRoom().selectByIdOnce(userId.value, id.value) ?: return@requireCurrentUserId
        val now = clock.localDateTime(zoneProvider.timeZone())
        accountRoom().insert(entity.copy(archivedAt = null, updatedDateTime = now))
    }
}
```

- [ ] **Step 7: Wire `clock` + `zoneProvider` + `MIGRATION_4_5` in `DatabaseComponent`**

In `DatabaseComponent.kt`, update the `addMigrations` call:
```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

Update `accountRepository` provider to pass the new params:
```kotlin
@Provides
@DatabaseScope
internal fun accountRepository(
    database: Provider<MainDatabase>,
    @CurrentUserId currentUserId: Flow<Id.Known>,
    incorrectStateDetector: IncorrectStateDetector,
    idGenerator: IdGenerator,
    clock: Clock,
    zoneProvider: ZoneProvider,
): AccountRepository = RoomAccountRepository(
    accountRoom = { database.get().account() },
    currentUserId = currentUserId,
    incorrectStateDetector = incorrectStateDetector,
    idGenerator = idGenerator,
    clock = clock,
    zoneProvider = zoneProvider,
)
```

Add imports if not already present:
```kotlin
import com.hluhovskyi.zero.accounts.MIGRATION_4_5
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
```

- [ ] **Step 8: Commit**
```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountRepository.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountEntity.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountMigrations.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountRoom.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/accounts/RoomAccountRepository.kt \
        zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt
git commit -m "feat: add archivedAt column and archive/unarchive to AccountRepository"
git push
```

---

### Task 2: Domain pass-through — `Account` + `DefaultAccountUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/Account.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountUseCase.kt`

- [ ] **Step 1: Add `archivedAt` to domain `Account`**

In `Account.kt`:
```kotlin
import kotlinx.datetime.LocalDateTime

data class Account(
    val id: Id.Known,
    val name: String,
    val balance: Amount,
    val currencySymbol: String,
    val icon: Image,
    val colorScheme: ColorScheme = ColorScheme.Grey,
    val category: AccountCategory,
    val details: String?,
    val archivedAt: LocalDateTime? = null,
)
```

- [ ] **Step 2: Pass `archivedAt` through in `DefaultAccountUseCase`**

In `DefaultAccountUseCase.kt`, inside the `accounts.map` lambda, add:
```kotlin
Account(
    id = account.id,
    name = account.name,
    balance = account.initialBalance + (accountIdToBalance[account.id] ?: Amount.zero()),
    currencySymbol = idToCurrency[account.currencyId]?.symbol.orEmpty(),
    icon = idToIcon[account.iconId]?.image ?: iconRepository.iconFor(account.category).image,
    colorScheme = colorScheme,
    category = account.category,
    details = account.details,
    archivedAt = account.archivedAt,   // add this line
)
```

- [ ] **Step 3: Commit**
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/Account.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountUseCase.kt
git commit -m "feat: propagate archivedAt through domain Account"
git push
```

---

### Task 3: Account list screen — ViewModel + Component + ViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModelTest.kt`

- [ ] **Step 1: Write failing tests for Archive/Unarchive actions**

In `DefaultAccountViewModelTest.kt`, add imports and two new tests:
```kotlin
import com.hluhovskyi.zero.accounts.AccountRepository
import org.mockito.Mock

@Mock private lateinit var accountRepository: AccountRepository
```

Add test methods:
```kotlin
@Test
fun `Archive action calls repository archive with account id`() = runTest {
    whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
    val accountId = Id.Known("acc-1")

    val vm = DefaultAccountViewModel(
        useCase = accountUseCase,
        dispatchers = dispatchers,
        accountRepository = accountRepository,
    )
    vm.attach()
    vm.perform(AccountViewModel.Action.Archive(accountId))

    verify(accountRepository).archive(accountId)
}

@Test
fun `Unarchive action calls repository unarchive with account id`() = runTest {
    whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
    val accountId = Id.Known("acc-1")

    val vm = DefaultAccountViewModel(
        useCase = accountUseCase,
        dispatchers = dispatchers,
        accountRepository = accountRepository,
    )
    vm.attach()
    vm.perform(AccountViewModel.Action.Unarchive(accountId))

    verify(accountRepository).unarchive(accountId)
}
```

- [ ] **Step 2: Run tests — expect failure**
```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountViewModelTest" 2>&1 | tail -20
```
Expected: compilation error (Archive/Unarchive actions don't exist yet).

- [ ] **Step 3: Add Archive/Unarchive actions to `AccountViewModel`**

In `AccountViewModel.kt`:
```kotlin
sealed interface Action {
    data class Select(val accountId: Id.Known) : Action
    data class Edit(val accountId: Id.Known) : Action
    data class Archive(val accountId: Id.Known) : Action
    data class Unarchive(val accountId: Id.Known) : Action
}
```

- [ ] **Step 4: Handle actions in `DefaultAccountViewModel`**

Add `accountRepository: AccountRepository` constructor parameter. Add import `import com.hluhovskyi.zero.accounts.AccountRepository`.

In `perform`:
```kotlin
is AccountViewModel.Action.Archive -> scope.launch(dispatchers.io()) {
    accountRepository.archive(action.accountId)
}
is AccountViewModel.Action.Unarchive -> scope.launch(dispatchers.io()) {
    accountRepository.unarchive(action.accountId)
}
```

- [ ] **Step 5: Run tests — expect pass**
```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountViewModelTest" 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 6: Wire `accountRepository` in `AccountComponent`**

In `AccountComponent.kt`, the `Dependencies` interface already has `accountRepository`. Wire it to the ViewModel in the `Module`:
```kotlin
@Provides
@AccountScope
fun viewModel(
    useCase: AccountUseCase,
    dispatcherProvider: DispatcherProvider,
    onAccountSelectedHandler: OnAccountSelectedHandler,
    onEditAccountHandler: OnEditAccountHandler,
    accountRepository: AccountRepository,
): AccountViewModel = DefaultAccountViewModel(
    useCase = useCase,
    dispatchers = dispatcherProvider,
    onAccountSelectedHandler = onAccountSelectedHandler,
    onEditAccountHandler = onEditAccountHandler,
    accountRepository = accountRepository,
)
```

- [ ] **Step 7: Add Archive/Unarchive to `AccountViewProvider`**

Update `AccountView` to dispatch archive/unarchive:
```kotlin
onArchiveClick = {
    expandedItemId = null
    viewModel.perform(AccountViewModel.Action.Archive(account.id))
},
onUnarchiveClick = {
    expandedItemId = null
    viewModel.perform(AccountViewModel.Action.Unarchive(account.id))
},
```

Update `AccountRow` signature to include the new callbacks:
```kotlin
private fun AccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onUnarchiveClick: () -> Unit,
)
```

In the `DropdownMenu`, add items after the Edit item:
```kotlin
if (account.archivedAt == null) {
    DropdownMenuItem(onClick = onArchiveClick) {
        Icon(
            imageVector = Icons.Filled.Archive,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Archive account")
    }
} else {
    DropdownMenuItem(onClick = onUnarchiveClick) {
        Icon(
            imageVector = Icons.Filled.Unarchive,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Unarchive account")
    }
}
```

Add imports:
```kotlin
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
```

- [ ] **Step 8: Commit**
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModelTest.kt
git commit -m "feat: add Archive/Unarchive to account list screen"
git push
```

---

### Task 4: Account detail screen — ViewModel + Component + ViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt`
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

In `DefaultAccountDetailViewModelTest.kt`, add:
```kotlin
import com.hluhovskyi.zero.accounts.AccountRepository
import org.mockito.Mock

@Mock private lateinit var accountRepository: AccountRepository
```

Add test methods and update `createViewModel` to accept `accountRepository`:
```kotlin
@Test
fun `Archive action calls repository archive with account id`() = runTest {
    val vm = createViewModel(backgroundScope)
    vm.attach()
    runCurrent()

    vm.perform(AccountDetailViewModel.Action.Archive)

    verify(accountRepository).archive(accountId)
}

@Test
fun `Unarchive action calls repository unarchive with account id`() = runTest {
    val vm = createViewModel(backgroundScope)
    vm.attach()
    runCurrent()

    vm.perform(AccountDetailViewModel.Action.Unarchive)

    verify(accountRepository).unarchive(accountId)
}

@Test
fun `isArchived state reflects archivedAt from account`() = runTest {
    val archivedAccount = makeAccount().copy(archivedAt = fakeClock.now().toLocalDateTime(TimeZone.UTC))
    whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(archivedAccount))))

    val vm = createViewModel(backgroundScope)
    vm.attach()
    runCurrent()

    assertTrue(vm.state.first().isArchived)
}
```

Update `createViewModel` signature:
```kotlin
private fun createViewModel(
    coroutineScope: CoroutineScope,
    onEditHandler: OnAccountDetailEditHandler = OnAccountDetailEditHandler.Noop,
) = DefaultAccountDetailViewModel(
    accountId = accountId,
    accountUseCase = accountUseCase,
    accountDetailSpendingUseCase = spendingUseCase,
    onBackHandler = OnBackHandler.Noop,
    onEditHandler = onEditHandler,
    accountRepository = accountRepository,
    clock = fakeClock,
    zoneProvider = fakeZone,
    coroutineScope = coroutineScope,
)
```

Add imports:
```kotlin
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertTrue
```

Also update `makeAccount` to accept `archivedAt`:
```kotlin
private fun makeAccount(
    name: String = "Test Account",
    balance: Amount = Amount.zero(),
    currencySymbol: String = "$",
    archivedAt: kotlinx.datetime.LocalDateTime? = null,
) = Account(
    id = accountId,
    name = name,
    balance = balance,
    currencySymbol = currencySymbol,
    icon = Image.empty(),
    category = AccountCategory.BANK,
    details = null,
    archivedAt = archivedAt,
)
```

- [ ] **Step 2: Run tests — expect failure**
```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountDetailViewModelTest" 2>&1 | tail -20
```
Expected: compilation error (Archive/Unarchive actions don't exist yet).

- [ ] **Step 3: Add Archive/Unarchive actions and `isArchived` to `AccountDetailViewModel`**

In `AccountDetailViewModel.kt`:
```kotlin
sealed interface Action {
    object Back : Action
    object Edit : Action
    object Archive : Action
    object Unarchive : Action
}

data class State(
    val accountName: String = "",
    val accountIcon: Image = Image.empty(),
    val accountDetails: String? = null,
    val balance: Amount = Amount.zero(),
    val currencySymbol: String = "",
    val isNegativeBalance: Boolean = false,
    val isArchived: Boolean = false,
    val periodDate: LocalDate? = null,
    val totalIn: Amount = Amount.zero(),
    val totalOut: Amount = Amount.zero(),
    val transactionCount: Int = 0,
)
```

- [ ] **Step 4: Handle actions and state in `DefaultAccountDetailViewModel`**

Add `accountRepository: AccountRepository` constructor parameter. Add import `import com.hluhovskyi.zero.accounts.AccountRepository`.

In `perform`:
```kotlin
AccountDetailViewModel.Action.Archive -> coroutineScope.launch(Dispatchers.IO) {
    accountRepository.archive(accountId)
}
AccountDetailViewModel.Action.Unarchive -> coroutineScope.launch(Dispatchers.IO) {
    accountRepository.unarchive(accountId)
}
```

In `attachOnMain`, inside the account state update lambda, add:
```kotlin
isArchived = account.archivedAt != null,
```

- [ ] **Step 5: Run tests — expect pass**
```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountDetailViewModelTest" 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 6: Wire `accountRepository` in `AccountDetailComponent`**

In `AccountDetailComponent.kt`, `Dependencies` already has `accountRepository`. Wire it in the `viewModel` provider:
```kotlin
@Provides
@AccountDetailScope
fun viewModel(
    accountId: Id.Known,
    accountUseCase: AccountUseCase,
    accountDetailSpendingUseCase: AccountDetailSpendingUseCase,
    onBackHandler: OnBackHandler,
    onEditHandler: OnAccountDetailEditHandler,
    accountRepository: AccountRepository,
    clock: Clock,
    zoneProvider: ZoneProvider,
): AccountDetailViewModel = DefaultAccountDetailViewModel(
    accountId = accountId,
    accountUseCase = accountUseCase,
    accountDetailSpendingUseCase = accountDetailSpendingUseCase,
    onBackHandler = onBackHandler,
    onEditHandler = onEditHandler,
    accountRepository = accountRepository,
    clock = clock,
    zoneProvider = zoneProvider,
)
```

- [ ] **Step 7: Add Archive/Unarchive to `AccountDetailViewProvider`**

In the `DropdownMenu` block inside `CollapsibleHeroLayout`, add after the Edit item:
```kotlin
if (state.isArchived) {
    DropdownMenuItem(
        onClick = {
            menuExpanded = false
            viewModel.perform(AccountDetailViewModel.Action.Unarchive)
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Unarchive,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Unarchive account")
    }
} else {
    DropdownMenuItem(
        onClick = {
            menuExpanded = false
            viewModel.perform(AccountDetailViewModel.Action.Archive)
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Archive,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Archive account")
    }
}
```

Add imports:
```kotlin
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
```

Move `val state by viewModel.state.collectAsState(...)` out of the `trailing` lambda to the top of `View()` so that `state.isArchived` is accessible in the dropdown (it's already at the top in the current code — confirm before editing).

- [ ] **Step 8: Run full test suite**
```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 9: Commit**
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt
git commit -m "feat: add Archive/Unarchive to account detail screen"
git push
```
