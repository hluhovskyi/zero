# Edit Account Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two entry points to edit an existing account — long-press on the account list and a three-dots menu on the account detail screen — reusing the existing `AccountEditViewProvider` with edit-mode support.

**Architecture:** Follows the category edit pattern exactly: a new `Destinations.Account.Item.Edit` destination takes an `accountId`; `AccountEditComponent` and `DefaultAccountEditViewModel` gain an `accountId: Id` field that pre-populates state when `Id.Known`; two new handlers route the two entry points to that destination. All UI and DI changes are wired in `MainActivityScreenComponent`.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger 2, Room (via `AccountRepository`), coroutines/Flow

---

## File Map

**New:**
- `zero-core/.../accounts/OnEditAccountHandler.kt` — callback from list → edit
- `zero-core/.../accounts/detail/OnAccountDetailEditHandler.kt` — callback from detail → edit

**Modified:**
- `app/.../navigation/Destinations.kt` — add `Account.Item.Edit`
- `zero-core/.../accounts/AccountViewModel.kt` — add `Edit(accountId)` action
- `zero-core/.../accounts/DefaultAccountViewModel.kt` — handle `Edit` → `onEditAccountHandler`
- `zero-core/.../accounts/AccountComponent.kt` — expose `onEditAccountHandler` builder method
- `zero-core/.../accounts/AccountViewProvider.kt` — `combinedClickable` long-press → dropdown → Edit
- `zero-core/.../accounts/detail/AccountDetailViewModel.kt` — add `Edit` action
- `zero-core/.../accounts/detail/DefaultAccountDetailViewModel.kt` — handle `Edit` → `onEditHandler`
- `zero-core/.../accounts/detail/AccountDetailComponent.kt` — expose `onEditHandler` builder method
- `zero-core/.../accounts/detail/AccountDetailViewProvider.kt` — three-dots trailing menu
- `zero-core/.../accounts/edit/AccountEditViewModel.kt` — add `isEditMode` to State
- `zero-core/.../accounts/edit/AccountEditComponent.kt` — expose `accountId` builder method
- `zero-core/.../accounts/edit/DefaultAccountEditViewModel.kt` — load account data when `Id.Known`, pass id on save
- `zero-core/.../accounts/edit/AccountEditViewProvider.kt` — dynamic title from `state.isEditMode`
- `app/.../screens/MainActivityScreenComponent.kt` — wire new handlers + `Account.Item.Edit` nav entry

---

### Task 1: Add `Account.Item.Edit` destination

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt`

- [ ] **Step 1: Add the destination**

In the `Account.Item` block, after `object Detail`, add:

```kotlin
object Edit : Item, Destination by destinationOf("accounts/{accountId}/edit", AccountId)
```

Final `Account` block:
```kotlin
sealed interface Account : Destination {
    object All : Account, Destination by destinationOf("accounts")
    object Edit : Account, Destination by destinationOf("accounts/edit")

    sealed interface Item : Account {
        object AccountId : Argument<Id.Known> by idKnownValueOf("accountId")
        object Detail : Item, Destination by destinationOf("accounts/{accountId}", AccountId)
        object Edit : Item, Destination by destinationOf("accounts/{accountId}/edit", AccountId)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt
git commit -m "feat: add Account.Item.Edit navigation destination"
```

---

### Task 2: New handler interfaces

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnEditAccountHandler.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/OnAccountDetailEditHandler.kt`

- [ ] **Step 1: Create `OnEditAccountHandler`**

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id

fun interface OnEditAccountHandler {

    fun onEdit(accountId: Id.Known)

    object Noop : OnEditAccountHandler {
        override fun onEdit(accountId: Id.Known) = Unit
    }
}
```

- [ ] **Step 2: Create `OnAccountDetailEditHandler`**

```kotlin
package com.hluhovskyi.zero.accounts.detail

fun interface OnAccountDetailEditHandler {

    fun onEdit()

    object Noop : OnAccountDetailEditHandler {
        override fun onEdit() = Unit
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnEditAccountHandler.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/OnAccountDetailEditHandler.kt
git commit -m "feat: add OnEditAccountHandler and OnAccountDetailEditHandler"
```

---

### Task 3: Long-press edit from account list

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModelTest.kt`

- [ ] **Step 1: Write failing test**

Create `zero-core/src/test/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModelTest.kt`:

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultAccountViewModelTest {

    @Mock private lateinit var accountUseCase: AccountUseCase

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun default() = dispatcher
    }

    @Test
    fun `Edit action calls onEditAccountHandler with correct id`() = runTest {
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
        val editHandler = mock<OnEditAccountHandler>()
        val accountId = Id.Known("acc-1")

        val vm = DefaultAccountViewModel(
            useCase = accountUseCase,
            dispatchers = dispatchers,
            onEditAccountHandler = editHandler,
        )
        vm.attach()
        vm.perform(AccountViewModel.Action.Edit(accountId))

        verify(editHandler).onEdit(accountId)
    }
}
```

- [ ] **Step 2: Run test — expect compile failure** (types don't exist yet)

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountViewModelTest" 2>&1 | tail -20
```

- [ ] **Step 3: Add `Edit` action to `AccountViewModel`**

```kotlin
sealed interface Action {
    data class Select(val accountId: Id.Known) : Action
    data class Edit(val accountId: Id.Known) : Action
}
```

- [ ] **Step 4: Update `DefaultAccountViewModel`**

Add `private val onEditAccountHandler: OnEditAccountHandler = OnEditAccountHandler.Noop` constructor param. Handle the new action:

```kotlin
override fun perform(action: AccountViewModel.Action) {
    when (action) {
        is AccountViewModel.Action.Select -> scope.launch(dispatchers.main()) {
            onAccountSelectedHandler.onSelected(action.accountId)
        }
        is AccountViewModel.Action.Edit -> scope.launch(dispatchers.main()) {
            onEditAccountHandler.onEdit(action.accountId)
        }
    }
}
```

- [ ] **Step 5: Update `AccountComponent`**

Add to `Builder` interface:
```kotlin
@BindsInstance
fun onEditAccountHandler(handler: OnEditAccountHandler): Builder
```

Add to `companion object`:
```kotlin
fun builder(dependencies: Dependencies): Builder = DaggerAccountComponent.builder()
    .dependencies(dependencies)
    .onAddAccountHandler(OnAddAccountHandler.Noop)
    .onAccountSelectedHandler(OnAccountSelectedHandler.Noop)
    .onEditAccountHandler(OnEditAccountHandler.Noop)
```

Pass it to `DefaultAccountViewModel` in `Module.viewModel()`:
```kotlin
fun viewModel(
    useCase: AccountUseCase,
    dispatcherProvider: DispatcherProvider,
    onAccountSelectedHandler: OnAccountSelectedHandler,
    onEditAccountHandler: OnEditAccountHandler,
): AccountViewModel = DefaultAccountViewModel(
    useCase = useCase,
    dispatchers = dispatcherProvider,
    onAccountSelectedHandler = onAccountSelectedHandler,
    onEditAccountHandler = onEditAccountHandler,
)
```

- [ ] **Step 6: Update `AccountViewProvider` — add long-press + context menu**

Add imports: `androidx.compose.foundation.combinedClickable`, `androidx.compose.material.DropdownMenu`, `androidx.compose.material.DropdownMenuItem`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Edit`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.remember`.

In `AccountView`, add state var before `LazyColumn`:
```kotlin
var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
```

Update the `items(accounts, ...)` lambda to pass the expand callback:
```kotlin
items(accounts, key = { it.id.value }) { account ->
    AccountRow(
        account = account,
        imageLoader = imageLoader,
        amountFormatter = amountFormatter,
        onClick = { viewModel.perform(AccountViewModel.Action.Select(account.id)) },
        onLongClick = { expandedItemId = account.id },
        menuExpanded = expandedItemId == account.id,
        onMenuDismiss = { expandedItemId = null },
        onEditClick = {
            expandedItemId = null
            viewModel.perform(AccountViewModel.Action.Edit(account.id))
        },
    )
}
```

Update `AccountRow` signature and body — replace `Modifier.clickable(onClick = onClick)` with `combinedClickable` and add a `DropdownMenu`:

```kotlin
@Composable
private fun AccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onEditClick: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(SurfaceContainerLowest, shape = RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ... existing content unchanged ...
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss,
        ) {
            DropdownMenuItem(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Edit account")
            }
        }
    }
}
```

- [ ] **Step 7: Run test — expect pass**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountViewModelTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/accounts/DefaultAccountViewModelTest.kt
git commit -m "feat: add long-press edit entry point on account list"
```

---

### Task 4: Three-dots edit from account detail screen

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt`

- [ ] **Step 1: Write failing test** (add to existing `DefaultAccountDetailViewModelTest.kt`)

```kotlin
@Test
fun `Edit action calls onEditHandler`() = runTest {
    val editHandler = mock<OnAccountDetailEditHandler>()
    val vm = createViewModel(backgroundScope, onEditHandler = editHandler)
    vm.attach()
    runCurrent()

    vm.perform(AccountDetailViewModel.Action.Edit)

    verify(editHandler).onEdit()
}
```

Update `createViewModel` to accept an optional `onEditHandler`:
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
    clock = fakeClock,
    zoneProvider = fakeZone,
    coroutineScope = coroutineScope,
)
```

Also add `import org.mockito.kotlin.mock` and `import org.mockito.kotlin.verify`.

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountDetailViewModelTest" 2>&1 | tail -20
```

- [ ] **Step 3: Add `Edit` action to `AccountDetailViewModel`**

```kotlin
sealed interface Action {
    object Back : Action
    object Edit : Action
}
```

- [ ] **Step 4: Update `DefaultAccountDetailViewModel`**

Add `private val onEditHandler: OnAccountDetailEditHandler = OnAccountDetailEditHandler.Noop` constructor param. Handle the action:

```kotlin
override fun perform(action: AccountDetailViewModel.Action) {
    when (action) {
        AccountDetailViewModel.Action.Back -> coroutineScope.launch(Dispatchers.Main) {
            onBackHandler.onBack()
        }
        AccountDetailViewModel.Action.Edit -> coroutineScope.launch(Dispatchers.Main) {
            onEditHandler.onEdit()
        }
    }
}
```

- [ ] **Step 5: Update `AccountDetailComponent`**

Add to `Builder`:
```kotlin
@BindsInstance
fun onEditHandler(handler: OnAccountDetailEditHandler): Builder
```

Add to `companion object`:
```kotlin
fun builder(dependencies: Dependencies): Builder = DaggerAccountDetailComponent.builder()
    .dependencies(dependencies)
    .onBackHandler(OnBackHandler.Noop)
    .onTransactionSelectedHandler(OnTransactionSelectedHandler.Noop)
    .onEditHandler(OnAccountDetailEditHandler.Noop)
```

Pass to `DefaultAccountDetailViewModel` in `Module.viewModel()`:
```kotlin
fun viewModel(
    accountId: Id.Known,
    accountUseCase: AccountUseCase,
    accountDetailSpendingUseCase: AccountDetailSpendingUseCase,
    onBackHandler: OnBackHandler,
    onEditHandler: OnAccountDetailEditHandler,
    clock: Clock,
    zoneProvider: ZoneProvider,
): AccountDetailViewModel = DefaultAccountDetailViewModel(
    accountId = accountId,
    accountUseCase = accountUseCase,
    accountDetailSpendingUseCase = accountDetailSpendingUseCase,
    onBackHandler = onBackHandler,
    onEditHandler = onEditHandler,
    clock = clock,
    zoneProvider = zoneProvider,
)
```

- [ ] **Step 6: Add three-dots menu to `AccountDetailViewProvider`**

Add imports: `androidx.compose.material.DropdownMenu`, `androidx.compose.material.DropdownMenuItem`, `androidx.compose.material.Icon`, `androidx.compose.material.IconButton`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Edit`, `androidx.compose.material.icons.filled.MoreVert`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `com.hluhovskyi.zero.ui.theme.PrimaryContainer`.

Update `CollapsibleHeroLayout` call inside `View()`:
```kotlin
CollapsibleHeroLayout(
    topBar = {
        DetailTopBar(
            title = state.accountName,
            onBack = { viewModel.perform(AccountDetailViewModel.Action.Back) },
            trailing = {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = PrimaryContainer,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        onClick = {
                            menuExpanded = false
                            viewModel.perform(AccountDetailViewModel.Action.Edit)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Edit account")
                    }
                }
            },
        )
    },
    hero = { HeroCard(state, amountFormatter, imageLoader) },
    content = { transactionComponent.AttachWithView() },
)
```

Also add `import androidx.compose.foundation.layout.Spacer` and `import androidx.compose.foundation.layout.width`.

- [ ] **Step 7: Run test — expect pass**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountDetailViewModelTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailComponent.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt
git commit -m "feat: add three-dots edit menu to account detail screen"
```

---

### Task 5: Edit mode in `AccountEditViewModel` / `DefaultAccountEditViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModelTest.kt`

- [ ] **Step 1: Write failing test**

Create `zero-core/src/test/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModelTest.kt`:

```kotlin
package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultAccountEditViewModelTest {

    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var currencyRepository: CurrencyRepository
    @Mock private lateinit var iconRepository: IconRepository
    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase
    @Mock private lateinit var accountEditIconUseCase: AccountEditIconUseCase
    @Mock private lateinit var accountEditCurrencyUseCase: AccountEditCurrencyUseCase

    private val iconId = Id.Known("icon-1")
    private val currencyId = Id.Known("cur-1")
    private val accountId = Id.Known("acc-1")

    @Before
    fun setUp() {
        whenever(accountRepository.query(any())).thenReturn(flowOf(emptyList()))
        whenever(currencyRepository.query(any())).thenReturn(flowOf(emptyList()))
        whenever(iconRepository.query(any())).thenReturn(flowOf(
            IconRepository.Icon(id = iconId, image = com.hluhovskyi.zero.common.Image.empty())
        ))
        whenever(accountEditIconUseCase.state).thenReturn(flowOf())
        whenever(accountEditCurrencyUseCase.state).thenReturn(flowOf())
    }

    @Test
    fun `isEditMode is false when accountId is Unknown`() = runTest {
        val vm = createViewModel(Id.Unknown)
        vm.attach()
        runCurrent()
        assertFalse(vm.state.first().isEditMode)
    }

    @Test
    fun `isEditMode is true when accountId is Known`() = runTest {
        val vm = createViewModel(accountId)
        vm.attach()
        runCurrent()
        assertTrue(vm.state.first().isEditMode)
    }

    @Test
    fun `edit mode pre-populates state from account repository`() = runTest {
        val account = AccountRepository.Account(
            id = accountId,
            name = "Chase Sapphire",
            currencyId = currencyId,
            iconId = iconId,
            initialBalance = Amount(BigDecimal("5000.00")),
            category = AccountCategory.BANK,
            details = "Checking",
        )
        whenever(accountRepository.query(any())).thenReturn(flowOf(listOf(account)))

        val vm = createViewModel(accountId)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals("Chase Sapphire", state.name)
        assertEquals("5000.00", state.balance)
        assertEquals(AccountCategory.BANK, state.category)
        assertEquals("Checking", state.details)
    }

    private fun createViewModel(id: Id) = DefaultAccountEditViewModel(
        accountId = id,
        accountRepository = accountRepository,
        currencyRepository = currencyRepository,
        iconRepository = iconRepository,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        accountEditIconUseCase = accountEditIconUseCase,
        accountEditCurrencyUseCase = accountEditCurrencyUseCase,
        onAccountSavedHandler = OnAccountSavedHandler.Noop,
    )
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountEditViewModelTest" 2>&1 | tail -20
```

- [ ] **Step 3: Add `isEditMode` to `AccountEditViewModel.State`**

```kotlin
data class State(
    val name: String = "",
    val balance: String = "",
    val details: String = "",
    val category: AccountCategory = AccountCategory.OTHER,
    val currencies: List<Currency> = emptyList(),
    val selectedCurrency: Currency? = null,
    val selectedIcon: Image = Image.empty(),
    val colorScheme: ColorScheme = ColorScheme.Grey,
    val isEditMode: Boolean = false,
)
```

- [ ] **Step 4: Update `AccountEditComponent` — add `accountId` builder method**

Add to `Builder` interface:
```kotlin
@BindsInstance
fun accountId(id: Id): Builder
```

Add to `companion object`:
```kotlin
fun builder(dependencies: Dependencies): Builder = DaggerAccountEditComponent.builder()
    .dependencies(dependencies)
    .onAccountSavedHandler(OnAccountSavedHandler.Noop)
    .onCloseHandler(OnCloseHandler.Noop)
    .accountEditIconUseCase(AccountEditIconUseCase.Noop)
    .accountEditCurrencyUseCase(AccountEditCurrencyUseCase.Noop)
    .accountId(Id.Unknown)
```

Add to `Module.viewModel()`:
```kotlin
@Provides
@AccountEditScope
fun viewModel(
    accountId: Id,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    iconRepository: IconRepository,
    currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    accountEditIconUseCase: AccountEditIconUseCase,
    accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
    onAccountSavedHandler: OnAccountSavedHandler,
): AccountEditViewModel = DefaultAccountEditViewModel(
    accountId = accountId,
    accountRepository = accountRepository,
    currencyRepository = currencyRepository,
    iconRepository = iconRepository,
    currencyPrimaryUseCase = currencyPrimaryUseCase,
    accountEditIconUseCase = accountEditIconUseCase,
    accountEditCurrencyUseCase = accountEditCurrencyUseCase,
    onAccountSavedHandler = onAccountSavedHandler,
)
```

Note: `Id` must be imported from `com.hluhovskyi.zero.common.Id`.

- [ ] **Step 5: Update `DefaultAccountEditViewModel` — add `accountId: Id` and edit-mode logic**

Add `private val accountId: Id = Id.Unknown` constructor param.

Add `targetCurrencyId: Id = Id.Unknown` field to `CompositeState`.

Update `state` mapping to expose `isEditMode`:
```kotlin
override val state: Flow<AccountEditViewModel.State> = mutableState
    .map { state ->
        AccountEditViewModel.State(
            name = state.name,
            balance = state.balance,
            details = state.details,
            category = state.category,
            currencies = state.currencies,
            selectedCurrency = state.selectedCurrency,
            selectedIcon = state.icon,
            colorScheme = state.colorScheme,
            isEditMode = accountId is Id.Known,
        )
    }
```

Update `perform(Save)` to pass `accountId` to `AccountInsert`:
```kotlin
is AccountEditViewModel.Action.Save -> coroutineScope.launch {
    val state = mutableState.value
    val selectedCurrency = state.selectedCurrency ?: return@launch
    accountRepository.insert(
        AccountRepository.AccountInsert(
            id = accountId,
            name = state.name,
            currencyId = selectedCurrency.id,
            iconId = state.iconId,
            initialBalance = Amount(state.balance.toDoubleOrNull() ?: 0.0),
            category = state.category,
            details = state.details.takeIf { it.isNotBlank() },
        ),
    )
    launch(context = Dispatchers.Main) { onAccountSavedHandler.onSaved() }
}
```

Update `attach()` to load existing account when `accountId is Id.Known`. Place the account loading block BEFORE the icon/currency launchers so `mutableState.value.iconId` is correct when the icon launcher starts:

```kotlin
override fun attach(): Closeable = Closeables.of {
    coroutineScope.launch {
        if (accountId is Id.Known) {
            val account = accountRepository.query(AccountRepository.Criteria.All())
                .firstOrNull()
                ?.find { it.id == accountId }
            if (account != null) {
                mutableState.update { state ->
                    state.copy(
                        name = account.name,
                        balance = account.initialBalance.value.toPlainString(),
                        details = account.details.orEmpty(),
                        category = account.category,
                        iconId = account.iconId,
                        targetCurrencyId = account.currencyId,
                    )
                }
            }
        }

        val primaryCurrency = runCatching { currencyPrimaryUseCase.getPrimaryCurrency() }.getOrNull()
        launch {
            iconRepository.query(IconRepository.Criteria.ById(mutableState.value.iconId))
                .collect { icon ->
                    mutableState.update { state ->
                        if (state.icon == Image.empty()) state.copy(icon = icon.image) else state
                    }
                }
        }
        launch {
            currencyRepository.query(CurrencyRepository.Criteria.InUse())
                .collectLatest { currencies ->
                    mutableState.update { state ->
                        state.copy(
                            currencies = currencies,
                            selectedCurrency = state.selectedCurrency
                                ?: currencies.firstOrNull { it.id == state.targetCurrencyId }
                                ?: currencies.firstOrNull { it.id == primaryCurrency?.id }
                                ?: primaryCurrency
                                ?: currencies.firstOrNull(),
                        )
                    }
                }
        }
        launch(context = Dispatchers.Main) {
            accountEditIconUseCase.state.collect { iconState ->
                when (iconState) {
                    is AccountEditIconUseCase.State.Picked -> mutableState.update { state ->
                        state.copy(
                            iconId = iconState.icon.id,
                            icon = iconState.icon.image,
                            colorScheme = iconState.colorScheme ?: state.colorScheme,
                        )
                    }
                    is AccountEditIconUseCase.State.ColorChanged -> mutableState.update { state ->
                        state.copy(
                            colorId = iconState.colorId,
                            colorScheme = iconState.colorScheme,
                        )
                    }
                }
            }
        }
        launch(context = Dispatchers.Main) {
            accountEditCurrencyUseCase.state
                .filterIsInstance<AccountEditCurrencyUseCase.State.Picked>()
                .collectLatest { currencyState ->
                    mutableState.update { state ->
                        state.copy(selectedCurrency = currencyState.currency)
                    }
                }
        }
    }
}
```

Also add `targetCurrencyId: Id = Id.Unknown` to `CompositeState` and the import `kotlinx.coroutines.flow.firstOrNull`.

- [ ] **Step 6: Update `AccountEditViewProvider` — dynamic title**

Change:
```kotlin
ModalHeader(
    title = "New Account",
    onClose = { onClose.onClose() },
)
```
To:
```kotlin
ModalHeader(
    title = if (state.isEditMode) "Edit Account" else "New Account",
    onClose = { onClose.onClose() },
)
```

- [ ] **Step 7: Run tests — expect pass**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultAccountEditViewModelTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 8: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditComponent.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModelTest.kt
git commit -m "feat: add edit mode to AccountEditViewModel"
```

---

### Task 6: Wire navigation in `MainActivityScreenComponent`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Add `OnEditAccountHandler` and `OnAccountDetailEditHandler` imports**

Add at top with other account imports:
```kotlin
import com.hluhovskyi.zero.accounts.OnEditAccountHandler
import com.hluhovskyi.zero.accounts.detail.OnAccountDetailEditHandler
```

- [ ] **Step 2: Wire `onEditAccountHandler` in account list nav entry**

Update `accountNavigationEntry` (around line 439):
```kotlin
AccountsScreen(
    component = componentBuilder
        .onAddAccountHandler { navigator.navigateTo(Destinations.Account.Edit) }
        .onAccountSelectedHandler { accountId ->
            navigator.navigateTo(
                Destinations.Account.Item.Detail,
                Destinations.Account.Item.AccountId.withValue(accountId),
            )
        }
        .onEditAccountHandler { accountId ->
            navigator.navigateTo(
                Destinations.Account.Item.Edit,
                Destinations.Account.Item.AccountId.withValue(accountId),
            )
        }
        .logging(logger),
)
```

- [ ] **Step 3: Wire `onEditHandler` in account detail nav entry**

Update `accountDetailNavigationEntry` (around line 500):
```kotlin
componentBuilder
    .accountId(accountId)
    .onBackHandler { navigator.back() }
    .onEditHandler {
        navigator.navigateTo(
            Destinations.Account.Item.Edit,
            Destinations.Account.Item.AccountId.withValue(accountId),
        )
    }
    .onTransactionSelectedHandler { transactionId ->
        navigator.navigateTo(
            Destinations.Transaction.Item.Edit,
            Destinations.Transaction.Item.TransactionId.withValue(transactionId),
        )
    }
    .logging(logger)
```

- [ ] **Step 4: Add `Account.Item.Edit` nav entry**

Add a new `@Provides @IntoSet @MainActivityScreenScope` method after `accountDetailNavigationEntry`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun accountItemEditNavigationEntry(
    componentBuilder: AccountEditComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
    accountEditIconUseCase: AccountEditIconUseCase,
    accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
): NavigatorEntry = navigatorScope.buildable(Destinations.Account.Item.Edit) {
    val accountId = arguments.getValue(Destinations.Account.Item.AccountId)
    componentBuilder
        .accountId(accountId)
        .accountEditIconUseCase(accountEditIconUseCase)
        .accountEditCurrencyUseCase(accountEditCurrencyUseCase)
        .onAccountSavedHandler { navigator.back() }
        .onCloseHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: wire account edit navigation for both entry points"
```

---

### Task 7: Verification

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint**

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Expected: no errors

- [ ] **Step 3: UI inspection via android-ui-inspector**

Invoke `zero-project:android-ui-inspector` to verify on device:
1. Account list: long-press an account → context menu with "Edit account" appears → tapping it opens the edit screen pre-populated with the account's data and title "Edit Account"
2. Account detail: tap three-dots icon (top right) → dropdown with "Edit account" → tapping it opens the same edit screen pre-populated with that account's data
3. Creating a new account via "Add Account" still works and shows "New Account" title

---

## Self-Review Checklist

- **Entry point 1 (long press):** Task 3 adds `combinedClickable` + `DropdownMenu` in `AccountViewProvider`, routes through `AccountViewModel.Action.Edit` → `OnEditAccountHandler` → `Account.Item.Edit` in Task 6. ✓
- **Entry point 2 (three-dots):** Task 4 adds `MoreVert` menu in `AccountDetailViewProvider`, routes through `AccountDetailViewModel.Action.Edit` → `OnAccountDetailEditHandler` → `Account.Item.Edit` in Task 6. ✓
- **Same screen reused, no new screens:** `AccountEditComponent`/`AccountEditViewProvider` is reused in both cases via the new `Account.Item.Edit` destination (Task 1, Task 6). ✓
- **Edit mode pre-populates data:** Task 5 loads the existing account from repository and pre-populates name, balance, category, details, icon, currency. ✓
- **Save updates existing record:** Task 5 passes `accountId` to `AccountInsert`, which uses it as an upsert key. ✓
- **Title changes:** Task 5 step 6 changes "New Account" → "Edit Account" in edit mode. ✓
- **Add flow unchanged:** `Account.Edit` (no ID) destination still passes `Id.Unknown` as default, no pre-population happens. ✓
