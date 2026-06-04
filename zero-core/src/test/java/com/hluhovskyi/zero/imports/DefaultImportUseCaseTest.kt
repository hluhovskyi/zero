package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconCategory
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.sync.SyncAccount
import com.hluhovskyi.zero.sync.SyncCategory
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.sync.SyncTransaction
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DefaultImportUseCaseTest {

    @Mock private lateinit var parser: SnapshotProvider.File

    @Mock private lateinit var remoteProvider: SnapshotProvider.Remote

    @Mock private lateinit var syncEngine: SyncEngine

    @Mock private lateinit var currentUserRepository: CurrentUserRepository

    @Mock private lateinit var iconRepository: IconRepository

    @Mock private lateinit var colorRepository: ColorRepository

    @Mock private lateinit var categoryRepository: CategoryRepository

    @Mock private lateinit var accountRepository: AccountRepository

    @Mock private lateinit var transactionRepository: TransactionRepository

    private val source = KnownSource.ZeroBackup
    private val userId = Id.Known("user-1")
    private val testUri = Uri("file://test.zero") as Uri.NonEmpty

    @Before
    fun setUp() {
        lenient().`when`(parser.source).thenReturn(source)
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
        lenient().`when`(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>())).thenReturn(flowOf(emptyList()))
        lenient().`when`(accountRepository.query(any<AccountRepository.Criteria>())).thenReturn(flowOf(emptyList()))
        lenient().`when`(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.Transaction>>>(), any())).thenReturn(flowOf(emptyList()))
        lenient().`when`(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>())).thenReturn(flowOf(emptyList()))
        lenient().`when`(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)
    }

    private fun createUseCase(scope: CoroutineScope) = DefaultImportUseCase(
        providers = listOf(parser),
        syncEngine = syncEngine,
        currentUserRepository = currentUserRepository,
        iconRepository = iconRepository,
        colorRepository = colorRepository,
        categoryRepository = categoryRepository,
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        onImportFinishedHandler = OnImportFinishedHandler.Noop,
        coroutineScope = scope,
    )

    @Test
    fun `SelectFile sets error state when parser throws`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNotNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `DismissError clears the error`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.DismissError)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `Retry transitions to FilePicker`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.Retry)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.FilePicker) { "Expected FilePicker but got $state" }
    }

    @Test
    fun `buildCategories uses existing category icon when name matches case-insensitively`() = runTest {
        val existingIconId = Id.Known("icon-999")
        val existingColorId = Id.Known("color-999")
        val existingCategory = CategoryRepository.Category(
            id = Id.Known("existing-cat-1"),
            parentCategoryId = Id.Unknown,
            name = "Food",
            iconId = existingIconId,
            colorId = existingColorId,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existingCategory)))

        val matchedImage = Image(
            uri = Uri("file://food-icon.png") as Uri.NonEmpty,
            description = "food icon",
        )
        val matchedIcon = Icon(id = existingIconId, image = matchedImage, category = IconCategory.unknown())
        whenever(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>()))
            .thenReturn(flowOf(listOf(matchedIcon)))

        whenever(colorRepository.schemeFor(existingColorId)).thenReturn(ColorScheme.Grey)

        val syncCategory = SyncCategory(
            id = Id.Known("import-cat-1"),
            name = "food",
            iconId = "icon-000",
            colorId = "color-000",
            parentCategoryId = null,
            creationDateTime = LocalDateTime(2024, 1, 1, 0, 0),
            updatedDateTime = LocalDateTime(2024, 1, 1, 0, 0),
            deletedAt = null,
        )
        val snapshot = SyncSnapshot(
            version = 1,
            userId = userId,
            exportedAt = LocalDateTime(2024, 1, 1, 0, 0),
            categories = listOf(syncCategory),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        whenever(parser.parse(testUri)).thenReturn(snapshot)
        whenever(syncEngine.delta(snapshot, userId)).thenReturn(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.CategoriesReview) { "Expected CategoriesReview but got $state" }
        val categories = (state as ImportUseCase.State.CategoriesReview).categories
        assert(categories.size == 1)
        assert(categories[0].icon == matchedImage) {
            "Expected matched icon image from existing category but got ${categories[0].icon}"
        }
    }

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
        val snapshot = SyncSnapshot(
            version = 1,
            userId = userId,
            exportedAt = LocalDateTime(2024, 1, 1, 0, 0),
            categories = listOf(syncCategory),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        whenever(parser.parse(testUri)).thenReturn(snapshot)
        whenever(syncEngine.delta(snapshot, userId)).thenReturn(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first() as ImportUseCase.State.CategoriesReview
        assert(state.categories.single().existingId == Id.Known("existing-1"))
    }

    @Test
    fun `SelectFile transitions to UpToDate when delta is completely empty`() = runTest {
        val emptySnapshot = SyncSnapshot(
            version = 1,
            userId = userId,
            exportedAt = LocalDateTime(2024, 1, 1, 0, 0),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        whenever(parser.parse(testUri)).thenReturn(emptySnapshot)
        whenever(syncEngine.delta(emptySnapshot, userId)).thenReturn(emptySnapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.UpToDate) { "Expected UpToDate but got $state" }
    }

    @Test
    fun `SelectFile imports immediately and reports RestoreSuccess when everything is new`() = runTest {
        val snapshot = snapshotWith(
            categories = listOf(syncCategory("c1", "Food")),
            accounts = listOf(syncAccount("a1", "Wallet")),
            transactions = listOf(syncTransaction("t1", accountId = "a1", categoryId = "c1")),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        verify(syncEngine).import(snapshot, userId)
        val state = useCase.state.first()
        assert(state is ImportUseCase.State.RestoreSuccess) { "Expected RestoreSuccess but got $state" }
        assertEquals(3, (state as ImportUseCase.State.RestoreSuccess).itemCount)
    }

    @Test
    fun `SelectFile falls through to CategoriesReview when snapshot half-overlaps local data`() = runTest {
        // One category matches an existing row; the rest is new -> conflict path, not the fast path.
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existingCategory(name = "Food"))))

        val snapshot = snapshotWith(
            categories = listOf(
                syncCategory("c-match", "Food"),
                syncCategory("c-fresh", "Travel"),
            ),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        verify(syncEngine, never()).import(any(), any())
        val state = useCase.state.first()
        assert(state is ImportUseCase.State.CategoriesReview) { "Expected CategoriesReview but got $state" }
    }

    @Test
    fun `SelectSource for a file source shows the file picker`() = runTest {
        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.FilePicker) { "Expected FilePicker but got $state" }
    }

    @Test
    fun `SelectSource for a remote source skips FilePicker and loads immediately`() = runTest {
        val remoteSource = object : Source {
            override val key = "drive"
        }
        val snapshot = snapshotWith(categories = listOf(syncCategory("c1", "Food")))
        whenever(remoteProvider.source).thenReturn(remoteSource)
        whenever(remoteProvider.load()).thenReturn(snapshot)
        whenever(syncEngine.delta(eq(snapshot), eq(userId))).thenReturn(snapshot)

        val useCase = DefaultImportUseCase(
            providers = listOf(remoteProvider),
            syncEngine = syncEngine,
            currentUserRepository = currentUserRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            onImportFinishedHandler = OnImportFinishedHandler.Noop,
            coroutineScope = this,
        )
        useCase.perform(ImportUseCase.Action.SelectSource(remoteSource))
        advanceUntilIdle()

        // Never paused on the file picker; went straight to importing the all-new snapshot.
        val state = useCase.state.first()
        assert(state is ImportUseCase.State.RestoreSuccess) { "Expected RestoreSuccess but got $state" }
    }

    @Test
    fun `ConfirmCategories with Merge remaps categoryId in transactions to existingId`() = runTest {
        val existing = CategoryRepository.Category(
            id = Id.Known("existing-cat"),
            parentCategoryId = Id.Unknown,
            name = "Food",
            iconId = Id.Known("icon"),
            colorId = Id.Known("color"),
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existing)))
        whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)

        val snapshot = snapshotWith(
            categories = listOf(syncCategory("import-cat", "food")),
            transactions = listOf(syncTransaction("t1", accountId = "a1", categoryId = "import-cat")),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmAccounts)
        advanceUntilIdle()

        val state = useCase.state.first() as ImportUseCase.State.TransactionsPreview
        val expense = state.transactions.single() as ImportTransaction.Expense
        assert(expense.categoryId == Id.Known("existing-cat")) {
            "Expected categoryId existing-cat but got ${expense.categoryId}"
        }
    }

    @Test
    fun `ConfirmCategories with Skip drops category and its transactions`() = runTest {
        // Existing match keeps us on the conflict path (otherwise the all-new fast path fires).
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existingCategory(name = "Food"))))

        val snapshot = snapshotWith(
            categories = listOf(syncCategory("c-skip", "Food")),
            transactions = listOf(syncTransaction("t1", accountId = "a1", categoryId = "c-skip")),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.SetCategoryStrategy(Id.Known("c-skip"), ResolveStrategy.Skip))
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmAccounts)
        advanceUntilIdle()

        val state = useCase.state.first() as ImportUseCase.State.TransactionsPreview
        assert(state.transactions.isEmpty()) { "Expected no transactions, got ${state.transactions.size}" }
    }

    @Test
    fun `ConfirmAccounts with Merge remaps accountId and targetAccountId`() = runTest {
        val existingAccount = AccountRepository.Account(
            id = Id.Known("existing-acc"),
            name = "Wallet",
            currencyId = Id.Known("usd"),
            iconId = Id.Known("icon"),
            initialBalance = Amount(java.math.BigDecimal.ZERO),
            category = AccountCategory.CASH,
            details = null,
        )
        whenever(accountRepository.query(any<AccountRepository.Criteria>()))
            .thenReturn(flowOf(listOf(existingAccount)))

        val snapshot = snapshotWith(
            accounts = listOf(syncAccount("import-acc", "wallet")),
            transactions = listOf(
                syncTransaction("t1", accountId = "import-acc", categoryId = null),
                syncTransaction(
                    "t2",
                    accountId = "import-acc",
                    categoryId = null,
                    type = SyncTransaction.Type.TRANSFER,
                    targetAccountId = "import-acc",
                ),
            ),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmAccounts)
        advanceUntilIdle()

        val state = useCase.state.first() as ImportUseCase.State.TransactionsPreview
        val expense = state.transactions.first { it is ImportTransaction.Expense } as ImportTransaction.Expense
        assert(expense.accountId == Id.Known("existing-acc")) {
            "Expected accountId existing-acc but got ${expense.accountId}"
        }
        val transfer = state.transactions.first { it is ImportTransaction.Transfer } as ImportTransaction.Transfer
        assert(transfer.accountId == Id.Known("existing-acc")) {
            "Expected transfer.accountId existing-acc but got ${transfer.accountId}"
        }
        assert(transfer.targetAccountId == Id.Known("existing-acc")) {
            "Expected transfer.targetAccountId existing-acc but got ${transfer.targetAccountId}"
        }
    }

    @Test
    fun `ConfirmAccounts with Skip drops account and transactions referencing it`() = runTest {
        // Existing match keeps us on the conflict path (otherwise the all-new fast path fires).
        whenever(accountRepository.query(any<AccountRepository.Criteria>()))
            .thenReturn(flowOf(listOf(existingAccount(name = "Wallet"))))

        val snapshot = snapshotWith(
            accounts = listOf(syncAccount("a-skip", "Wallet")),
            transactions = listOf(
                syncTransaction("t1", accountId = "a-skip", categoryId = null),
                syncTransaction(
                    "t2",
                    accountId = "a-keep",
                    categoryId = null,
                    type = SyncTransaction.Type.TRANSFER,
                    targetAccountId = "a-skip",
                ),
            ),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.SetAccountStrategy(Id.Known("a-skip"), ResolveStrategy.Skip))
        useCase.perform(ImportUseCase.Action.ConfirmAccounts)
        advanceUntilIdle()

        val state = useCase.state.first() as ImportUseCase.State.TransactionsPreview
        assert(state.transactions.isEmpty()) { "Expected no transactions, got ${state.transactions.size}" }
    }

    @Test
    fun `SetCategoryStrategy updates state without leaving CategoriesReview`() = runTest {
        // Existing match keeps us on the conflict path (otherwise the all-new fast path fires).
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existingCategory(name = "Food"))))

        val snapshot = snapshotWith(categories = listOf(syncCategory("c1", "Food")))
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.SetCategoryStrategy(Id.Known("c1"), ResolveStrategy.Skip))

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.CategoriesReview)
        assert((state as ImportUseCase.State.CategoriesReview).strategies[Id.Known("c1")] == ResolveStrategy.Skip)
    }

    @Test
    fun `default category strategy is Merge for existing and New otherwise`() = runTest {
        val existing = CategoryRepository.Category(
            id = Id.Known("e1"),
            parentCategoryId = Id.Unknown,
            name = "Food",
            iconId = Id.Known("i"),
            colorId = Id.Known("c"),
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existing)))
        whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)

        val snapshot = snapshotWith(
            categories = listOf(
                syncCategory("c-match", "food"),
                syncCategory("c-fresh", "Travel"),
            ),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first() as ImportUseCase.State.CategoriesReview
        assert(state.strategies[Id.Known("c-match")] == ResolveStrategy.Merge)
        assert(state.strategies[Id.Known("c-fresh")] == ResolveStrategy.New)
    }

    @Test
    fun `re-confirming categories after Back preserves full account list`() = runTest {
        // Repro: user went Categories → Accounts → Back → Categories → Continue.
        // Old bug: storedDelta was narrowed by ConfirmCategories, so the second pass produced fewer accounts.
        val existingCat = CategoryRepository.Category(
            id = Id.Known("cat-local"),
            parentCategoryId = Id.Unknown,
            name = "Food",
            iconId = Id.Known("i"),
            colorId = Id.Known("c"),
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existingCat)))

        val snapshot = snapshotWith(
            categories = listOf(syncCategory("c-merge", "food")),
            accounts = listOf(
                syncAccount("a1", "Wallet"),
                syncAccount("a2", "Savings"),
                syncAccount("a3", "Credit"),
            ),
            transactions = listOf(
                syncTransaction("t1", accountId = "a1", categoryId = "c-merge"),
                syncTransaction("t2", accountId = "a2", categoryId = "c-merge"),
                syncTransaction("t3", accountId = "a3", categoryId = "c-merge"),
            ),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()

        val firstPass = useCase.state.first() as ImportUseCase.State.AccountsReview
        assert(firstPass.accounts.size == 3) {
            "First pass: expected 3 accounts but got ${firstPass.accounts.size}"
        }

        useCase.perform(ImportUseCase.Action.Back)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()

        val secondPass = useCase.state.first() as ImportUseCase.State.AccountsReview
        assert(secondPass.accounts.size == 3) {
            "Second pass: expected 3 accounts but got ${secondPass.accounts.size}"
        }
    }

    @Test
    fun `re-confirming accounts after Back produces same final transactions`() = runTest {
        // Repro: Accounts → TransactionsPreview → Back → Accounts → Continue.
        // Old bug: storedDelta was narrowed each ConfirmAccounts call, dropping accounts on the second pass.
        val existingAccount = AccountRepository.Account(
            id = Id.Known("acc-local"),
            name = "Wallet",
            currencyId = Id.Known("usd"),
            iconId = Id.Known("i"),
            initialBalance = Amount(java.math.BigDecimal.ZERO),
            category = AccountCategory.CASH,
            details = null,
        )
        whenever(accountRepository.query(any<AccountRepository.Criteria>()))
            .thenReturn(flowOf(listOf(existingAccount)))

        val snapshot = snapshotWith(
            accounts = listOf(syncAccount("a-import", "wallet")),
            transactions = listOf(
                syncTransaction("t1", accountId = "a-import", categoryId = null),
                syncTransaction("t2", accountId = "a-import", categoryId = null),
            ),
        )
        stubParseAndDelta(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmCategories)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmAccounts)
        advanceUntilIdle()

        val firstPreview = useCase.state.first() as ImportUseCase.State.TransactionsPreview
        val firstCount = firstPreview.transactions.size

        useCase.perform(ImportUseCase.Action.Back)
        advanceUntilIdle()
        useCase.perform(ImportUseCase.Action.ConfirmAccounts)
        advanceUntilIdle()

        val secondPreview = useCase.state.first() as ImportUseCase.State.TransactionsPreview
        assert(secondPreview.transactions.size == firstCount) {
            "Re-confirm produced ${secondPreview.transactions.size} but first pass had $firstCount"
        }
    }

    @Test
    fun `Back from UpToDate returns to SourceSelection`() = runTest {
        val emptySnapshot = SyncSnapshot(
            version = 1,
            userId = userId,
            exportedAt = LocalDateTime(2024, 1, 1, 0, 0),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        whenever(parser.parse(testUri)).thenReturn(emptySnapshot)
        whenever(syncEngine.delta(emptySnapshot, userId)).thenReturn(emptySnapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.Back)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection) { "Expected SourceSelection but got $state" }
    }

    private val testDateTime = LocalDateTime(2024, 1, 1, 0, 0)

    private fun existingCategory(name: String) = CategoryRepository.Category(
        id = Id.Known("local-${name.lowercase()}"),
        parentCategoryId = Id.Unknown,
        name = name,
        iconId = Id.Known("icon"),
        colorId = Id.Known("color"),
    )

    private fun existingAccount(name: String) = AccountRepository.Account(
        id = Id.Known("local-${name.lowercase()}"),
        name = name,
        currencyId = Id.Known("usd"),
        iconId = Id.Known("icon"),
        initialBalance = Amount(java.math.BigDecimal.ZERO),
        category = AccountCategory.CASH,
        details = null,
    )

    private fun snapshotWith(
        categories: List<SyncCategory> = emptyList(),
        accounts: List<SyncAccount> = emptyList(),
        transactions: List<SyncTransaction> = emptyList(),
    ) = SyncSnapshot(
        version = 1,
        userId = userId,
        exportedAt = testDateTime,
        categories = categories,
        accounts = accounts,
        transactions = transactions,
    )

    private fun syncCategory(id: String, name: String) = SyncCategory(
        id = Id.Known(id),
        name = name,
        iconId = null,
        colorId = null,
        parentCategoryId = null,
        creationDateTime = testDateTime,
        updatedDateTime = testDateTime,
        deletedAt = null,
    )

    private fun syncAccount(id: String, name: String) = SyncAccount(
        id = Id.Known(id),
        currencyId = Id.Known("usd"),
        name = name,
        iconId = Id.Known("icon"),
        initialBalance = "0",
        category = "CASH",
        details = null,
        creationDateTime = testDateTime,
        updatedDateTime = testDateTime,
        deletedAt = null,
    )

    private fun syncTransaction(
        id: String,
        accountId: String,
        categoryId: String?,
        type: SyncTransaction.Type = SyncTransaction.Type.EXPENSE,
        targetAccountId: String? = null,
    ) = SyncTransaction(
        id = Id.Known(id),
        type = type,
        accountId = Id.Known(accountId),
        currencyId = Id.Known("usd"),
        categoryId = categoryId,
        amount = "10",
        rate = "1",
        targetAccountId = targetAccountId,
        targetAmount = null,
        enteredDateTime = testDateTime,
        creationDateTime = testDateTime,
        updatedDateTime = testDateTime,
        deletedAt = null,
    )

    private suspend fun stubParseAndDelta(snapshot: SyncSnapshot) {
        whenever(parser.parse(testUri)).thenReturn(snapshot)
        whenever(syncEngine.delta(snapshot, userId)).thenReturn(snapshot)
    }
}
