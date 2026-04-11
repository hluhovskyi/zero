# Accounts Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the accounts list screen to match the Stitch "My Accounts (Dense View)" design — grouped by category, dense rows with icon/name/details/balance, net worth header, and inline Add Account button.

**Architecture:** Add `AccountCategory` enum and `details: String?` to the domain model (zero-api → zero-database → zero-core), then fully rewrite `AccountViewProvider` with a `LazyColumn` grouped by category. Move the "Add Account" action from a FAB in `AccountsScreen` into the component via `OnAddAccountHandler`. Update the edit screen to expose category and details fields.

**Tech Stack:** Kotlin, Jetpack Compose (Material), Dagger 2, Room (SQLite), zero-api / zero-core / zero-database / app module structure.

---

### Task 1: Add `AccountCategory` enum to zero-api

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountCategory.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.hluhovskyi.zero.accounts

enum class AccountCategory {
    CASH,
    BANK,
    CREDIT_CARDS,
    DIGITAL_WALLETS,
    CRYPTO,
    OTHER,
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountCategory.kt
git commit -m "feat: add AccountCategory enum to zero-api"
```

---

### Task 2: Extend `AccountRepository` with `category` and `details`

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountRepository.kt`

- [ ] **Step 1: Add `category` and `details` to `Account` and `AccountInsert`**

Replace the `Account` and `AccountInsert` data classes:

```kotlin
data class Account(
    override val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
    val iconId: Id.Known,
    val initialBalance: Amount,
    val category: AccountCategory,
    val details: String?,
) : Identifiable

data class AccountInsert(
    val id: Id = Id.Unknown,
    val name: String,
    val currencyId: Id.Known,
    val iconId: Id.Known,
    val initialBalance: Amount,
    val category: AccountCategory = AccountCategory.OTHER,
    val details: String? = null,
)
```

- [ ] **Step 2: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/accounts/AccountRepository.kt
git commit -m "feat: add category and details to AccountRepository data classes"
```

---

### Task 3: Update the database layer

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountEntity.kt`
- Create: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountMigrations.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/RoomAccountRepository.kt`

- [ ] **Step 1: Add columns to `AccountEntity`**

```kotlin
package com.hluhovskyi.zero.accounts

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id

@Entity(
    indices = [Index("userId")],
)
internal data class AccountEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val currencyId: Id.Known,
    val name: String,
    val iconId: Id.Known,
    @Embedded val initialBalance: AmountEntity,
    val category: String = "OTHER",
    val details: String? = null,
)
```

- [ ] **Step 2: Create migration v1→v2**

```kotlin
package com.hluhovskyi.zero.accounts

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN details TEXT")
    }
}
```

- [ ] **Step 3: Bump database version in `MainDatabase.kt`**

Change the version constant:

```kotlin
private const val MAIN_DATABASE_VERSION = 2
```

- [ ] **Step 4: Wire the migration in `DatabaseComponent.kt`**

Update the `mainDatabase` provider to add the migration. The import `com.hluhovskyi.zero.accounts.MIGRATION_1_2` is already in the same module:

```kotlin
@Provides
@DatabaseScope
internal fun mainDatabase(
    context: Context,
): MainDatabase = Room.databaseBuilder(
    context,
    MainDatabase::class.java,
    "MainDatabase",
)
    .addMigrations(MIGRATION_1_2)
    .build()
```

Add import at the top of `DatabaseComponent.kt`:

```kotlin
import com.hluhovskyi.zero.accounts.MIGRATION_1_2
```

- [ ] **Step 5: Map new fields in `RoomAccountRepository`**

Update the `query` mapping in `RoomAccountRepository.kt`:

```kotlin
AccountRepository.Account(
    id = account.id,
    name = account.name,
    currencyId = account.currencyId,
    iconId = account.iconId,
    initialBalance = Amount(account.initialBalance.value),
    category = runCatching {
        AccountCategory.valueOf(account.category)
    }.getOrDefault(AccountCategory.OTHER),
    details = account.details,
)
```

Add `import com.hluhovskyi.zero.accounts.AccountCategory` to `RoomAccountRepository.kt`.

Update `toEntity` to persist category and details:

```kotlin
private fun AccountRepository.AccountInsert.toEntity(userId: Id.Known): AccountEntity = AccountEntity(
    id = (id as? Id.Known) ?: idGenerator(),
    userId = userId,
    currencyId = currencyId,
    name = name,
    iconId = iconId,
    initialBalance = AmountEntity(initialBalance.value),
    category = category.name,
    details = details,
)
```

- [ ] **Step 6: Commit**

```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountEntity.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountMigrations.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/accounts/RoomAccountRepository.kt
git commit -m "feat: add category and details columns to AccountEntity with Room migration"
```

---

### Task 4: Update the domain `Account` model and `DefaultAccountUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/Account.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountUseCase.kt`

- [ ] **Step 1: Add fields to `Account`**

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

data class Account(
    val id: Id.Known,
    val name: String,
    val balance: Amount,
    val currencySymbol: String,
    val icon: Image,
    val category: AccountCategory,
    val details: String?,
)
```

- [ ] **Step 2: Pass through fields in `DefaultAccountUseCase`**

In `DefaultAccountUseCase.kt`, update the `Account(...)` construction inside the `combine` block:

```kotlin
val resultAccounts = accounts.map { account ->
    Account(
        id = account.id,
        name = account.name,
        balance = account.initialBalance + (accountIdToBalance[account.id] ?: Amount.zero()),
        currencySymbol = idToCurrency[account.currencyId]?.symbol.orEmpty(),
        icon = idToIcon[account.iconId]?.image ?: Image.empty(),
        category = account.category,
        details = account.details,
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/Account.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/DefaultAccountUseCase.kt
git commit -m "feat: add category and details to Account domain model"
```

---

### Task 5: Wire `OnAddAccountHandler` through `AccountComponent`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnAddAccountHandler.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt`

- [ ] **Step 1: Create `OnAddAccountHandler`**

```kotlin
package com.hluhovskyi.zero.accounts

fun interface OnAddAccountHandler {

    fun onAddAccount()

    object Noop : OnAddAccountHandler {
        override fun onAddAccount() = Unit
    }
}
```

- [ ] **Step 2: Update `AccountComponent` to bind and thread the handler**

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountScope

private const val TAG = "AccountComponent"

@AccountScope
@dagger.Component(
    modules = [AccountComponent.Module::class],
    dependencies = [AccountComponent.Dependencies::class],
)
abstract class AccountComponent : AttachableViewComponent {

    internal abstract val viewModel: AccountViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val dispatchers: DispatcherProvider
        val iamgeLoader: ImageLoader
        val amountFormatter: AmountFormatter

        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase

        val accountRepository: AccountRepository
        val transactionRepository: TransactionRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerAccountComponent.builder()
            .dependencies(dependencies)
            .onAddAccountHandler(OnAddAccountHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onAddAccountHandler(handler: OnAddAccountHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountScope
        fun useCase(
            accountRepository: AccountRepository,
            transactionRepository: TransactionRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
        ): AccountUseCase = DefaultAccountUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
        )

        @Provides
        @AccountScope
        fun viewModel(
            useCase: AccountUseCase,
            dispatcherProvider: DispatcherProvider,
        ): AccountViewModel = DefaultAccountViewModel(
            useCase = useCase,
            dispatchers = dispatcherProvider,
        )

        @Provides
        @AccountScope
        fun viewProvider(
            viewModel: AccountViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
            onAddAccountHandler: OnAddAccountHandler,
        ): ViewProvider = AccountViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddAccount = onAddAccountHandler,
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/OnAddAccountHandler.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountComponent.kt
git commit -m "feat: add OnAddAccountHandler to AccountComponent"
```

---

### Task 6: Redesign `AccountViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`

- [ ] **Step 1: Replace the entire file**

```kotlin
package com.hluhovskyi.zero.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import java.math.BigDecimal

internal class AccountViewProvider(
    private val viewModel: AccountViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val onAddAccount: OnAddAccountHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddAccount = onAddAccount,
        )
    }
}

@Composable
private fun AccountView(
    viewModel: AccountViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onAddAccount: OnAddAccountHandler,
) {
    val state by viewModel.state.collectAsState(initial = AccountViewModel.State())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            NetWorthHeader(
                balance = amountFormatter.format(
                    amount = state.balance,
                    currencySymbol = state.currency?.symbol.orEmpty(),
                ),
            )
        }
        item {
            MyAccountsSectionHeader(onAddAccount = { onAddAccount.onAddAccount() })
        }
        val grouped = state.accounts
            .groupBy { it.category }
            .entries
            .sortedBy { it.key.ordinal }
        grouped.forEach { (category, accounts) ->
            item(key = category.name) {
                CategoryHeader(category = category)
            }
            items(accounts, key = { it.id.value }) { account ->
                AccountRow(
                    account = account,
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                )
            }
        }
    }
}

@Composable
private fun NetWorthHeader(balance: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "TOTAL NET WORTH",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp,
            ),
        )
        Text(
            text = balance,
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                letterSpacing = (-0.5).sp,
            ),
        )
    }
}

@Composable
private fun MyAccountsSectionHeader(onAddAccount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "My Accounts",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
        )
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = Primary,
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable(onClick = onAddAccount)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Add Account",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Primary,
                ),
            )
        }
    }
}

@Composable
private fun CategoryHeader(category: AccountCategory) {
    Text(
        text = category.displayName,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceVariant,
            letterSpacing = 0.8.sp,
        ),
    )
}

@Composable
private fun AccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SurfaceContainer, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.size(24.dp),
                image = account.icon,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                ),
            )
            if (account.details != null) {
                Text(
                    text = account.details,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = amountFormatter.format(
                amount = account.balance,
                currencySymbol = account.currencySymbol,
            ),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (account.balance.value < BigDecimal.ZERO) Error else OnSurface,
            ),
        )
    }
}

private val AccountCategory.displayName: String
    get() = when (this) {
        AccountCategory.CASH -> "CASH"
        AccountCategory.BANK -> "BANK"
        AccountCategory.CREDIT_CARDS -> "CREDIT CARDS"
        AccountCategory.DIGITAL_WALLETS -> "DIGITAL WALLETS"
        AccountCategory.CRYPTO -> "CRYPTO"
        AccountCategory.OTHER -> "OTHER"
    }
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt
git commit -m "feat: redesign AccountViewProvider with dense grouped list and net worth header"
```

---

### Task 7: Update `AccountsScreen` and navigation wiring

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/AccountsScreen.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Remove FAB from `AccountsScreen`**

The Add Account action is now inside the component. `AttachWithView()` takes no modifier; the `LazyColumn` inside `AccountViewProvider` handles `fillMaxSize`. Replace the entire file:

```kotlin
package com.hluhovskyi.zero.activity.screens

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

@Composable
fun AccountsScreen(
    component: Buildable<out AttachableViewComponent>,
) {
    component.AttachWithView()
}
```

- [ ] **Step 2: Update `accountNavigationEntry` in `MainActivityScreenComponent.kt`**

Find the `accountNavigationEntry` provider (around line 337) and update it:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun accountNavigationEntry(
    componentBuilder: AccountComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.composable(Destinations.Account.All) {
    AccountsScreen(
        component = componentBuilder
            .onAddAccountHandler { navigator.navigateTo(Destinations.Account.Edit) }
            .logging(logger),
    )
}
```

Add import at top of `MainActivityScreenComponent.kt` if not already present:
```kotlin
import com.hluhovskyi.zero.accounts.OnAddAccountHandler
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/AccountsScreen.kt
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: remove FAB from AccountsScreen, wire OnAddAccountHandler via component"
```

---

### Task 8: Add category and details to the account edit screen

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`

- [ ] **Step 1: Add actions and state fields to `AccountEditViewModel`**

```kotlin
package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Image

interface AccountEditViewModel : AttachableActionStateModel<AccountEditViewModel.Action, AccountEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        data class ChangeBalance(val balance: String) : Action
        data class ChangeDetails(val details: String) : Action
        data class ChangeCategory(val category: AccountCategory) : Action
        object SelectIcon : Action
        data class SelectCurrency(val currency: Currency) : Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val balance: String = "",
        val details: String = "",
        val category: AccountCategory = AccountCategory.OTHER,
        val currencies: List<Currency> = emptyList(),
        val selectedCurrency: Currency? = null,
        val selectedIcon: Image = Image.empty(),
    )
}
```

- [ ] **Step 2: Handle new actions in `DefaultAccountEditViewModel`**

Update `CompositeState` to include `category` and `details`:

```kotlin
private data class CompositeState(
    val name: String = "",
    val balance: String = "",
    val details: String = "",
    val category: AccountCategory = AccountCategory.OTHER,
    val selectedCurrency: Currency? = null,
    val currencies: List<Currency> = emptyList(),
    val iconId: Id.Known = IconRepository.defaultAccountIconId(),
    val icon: Image = Image.empty(),
)
```

Add `import com.hluhovskyi.zero.accounts.AccountCategory` to `DefaultAccountEditViewModel.kt`.

Update the `state` flow mapping to include new fields:

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
        )
    }
```

Add cases to `perform`:

```kotlin
is AccountEditViewModel.Action.ChangeDetails -> mutableState.update { state ->
    state.copy(details = action.details)
}
is AccountEditViewModel.Action.ChangeCategory -> mutableState.update { state ->
    state.copy(category = action.category)
}
```

Update the `Save` action to pass `category` and `details`:

```kotlin
is AccountEditViewModel.Action.Save -> coroutineScope.launch {
    val state = mutableState.value
    val selectedCurrency = state.selectedCurrency ?: return@launch
    accountRepository.insert(
        AccountRepository.AccountInsert(
            name = state.name,
            currencyId = selectedCurrency.id,
            iconId = state.iconId,
            initialBalance = Amount(state.balance.toDoubleOrNull() ?: 0.0),
            category = state.category,
            details = state.details.takeIf { it.isNotBlank() },
        ),
    )
    launch(context = Dispatchers.Main) {
        onAccountSavedHandler.onSaved()
    }
}
```

- [ ] **Step 3: Add category dropdown and details field to `AccountEditViewProvider`**

Add the following imports to `AccountEditViewProvider.kt`:

```kotlin
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu
```

Add a `CategorySelect` composable (after the existing `CurrencySelect` composable):

```kotlin
@Composable
private fun CategorySelect(
    modifier: Modifier = Modifier,
    selectedCategory: AccountCategory,
    onCategorySelected: (AccountCategory) -> Unit,
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = AccountCategory.entries,
        label = { Text(text = "Category") },
        nameMapping = { category ->
            when (category) {
                AccountCategory.CASH -> "Cash"
                AccountCategory.BANK -> "Bank"
                AccountCategory.CREDIT_CARDS -> "Credit Cards"
                AccountCategory.DIGITAL_WALLETS -> "Digital Wallets"
                AccountCategory.CRYPTO -> "Crypto"
                AccountCategory.OTHER -> "Other"
            }
        },
        selectedItem = selectedCategory,
        onItemSelected = onCategorySelected,
    )
}
```

Update `AccountEditView` to include category and details (insert between name and currency fields):

```kotlin
CategorySelect(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp),
    selectedCategory = state.category,
    onCategorySelected = { category ->
        viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
    },
)
OutlinedTextField(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp),
    value = state.details,
    label = { Text(text = "Details") },
    onValueChange = { details ->
        viewModel.perform(AccountEditViewModel.Action.ChangeDetails(details))
    },
)
```

Place `CategorySelect` after the name field and `OutlinedTextField` for details after it, before the currency field.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/DefaultAccountEditViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt
git commit -m "feat: add category and details fields to account edit screen"
```

---

## Verification

After all tasks are complete, build the project:

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no compilation errors. Install on a device/emulator and verify:
1. Accounts screen shows "TOTAL NET WORTH" header with the total balance
2. "My Accounts" section header has an "Add Account" pill button that navigates to the edit screen
3. Accounts are grouped under category headers (CASH & BANK, etc.)
4. Each account row shows icon in a rounded container, name, optional details line, and balance
5. Negative balances render in red (`Error` color)
6. Edit screen has Category and Details fields
