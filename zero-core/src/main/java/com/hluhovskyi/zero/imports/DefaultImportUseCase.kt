package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.sync.SyncAccount
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.sync.SyncTransaction
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultImportUseCase(
    private val parsers: List<SnapshotParser>,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val onImportFinishedHandler: OnImportFinishedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ImportUseCase {

    private data class InternalState(
        val selectedSource: Source? = null,
        val storedDelta: SyncSnapshot? = null,
        val storedCategories: List<ImportCategory>? = null,
        val storedAccounts: List<ImportAccount>? = null,
        val categoryStrategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
        val accountStrategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
        val existingCategoryById: Map<Id.Known, CategoryRepository.Category> = emptyMap(),
        val existingCategoryByName: Map<String, CategoryRepository.Category> = emptyMap(),
        val existingAccountById: Map<Id.Known, AccountRepository.Account> = emptyMap(),
        val existingAccountByName: Map<String, AccountRepository.Account> = emptyMap(),
        val existingTransactionSignatures: Set<TransactionSignature> = emptySet(),
        val allIconsById: Map<Id.Known, Icon> = emptyMap(),
        val screen: ImportUseCase.State,
    )

    private val mutableState = MutableStateFlow(
        InternalState(screen = ImportUseCase.State.SourceSelection(parsers.map { it.source })),
    )
    override val state: Flow<ImportUseCase.State> = mutableState.map { it.screen }

    override fun perform(action: ImportUseCase.Action) {
        when (action) {
            is ImportUseCase.Action.SelectSource -> mutableState.update { current ->
                current.copy(
                    selectedSource = action.source,
                    screen = ImportUseCase.State.FilePicker,
                )
            }
            is ImportUseCase.Action.SelectFile -> coroutineScope.launch {
                mutableState.update { it.copy(screen = ImportUseCase.State.Loading) }
                val source = mutableState.value.selectedSource ?: return@launch
                val parser = parsers.first { it.source.key == source.key }
                val userId = currentUserRepository.query().first().id
                try {
                    val snapshot = parser.parse(action.uri)
                    val delta = syncEngine.delta(snapshot, userId)

                    val allIcons = iconRepository.query(IconRepository.Criteria.All()).first()
                    val allIconsById = allIcons.associateBy { it.id }

                    val existingCategories = categoryRepository.query(CategoryRepository.Criteria.All()).first()
                    val existingCategoryById = existingCategories.associateBy { it.id }
                    val existingCategoryByName = existingCategories.associateBy { it.name.lowercase() }

                    val existingAccounts = accountRepository.query(AccountRepository.Criteria.All()).first()
                    val existingAccountById = existingAccounts.associateBy { it.id }
                    val existingAccountByName = existingAccounts.associateBy { it.name.lowercase() }

                    val existingTransactionSignatures = transactionRepository
                        .query(TransactionRepository.Criteria.All())
                        .first()
                        .map { it.toSignature() }
                        .toSet()

                    val categories = buildCategories(delta, existingCategoryById, existingCategoryByName, allIconsById)
                    if (categories.isEmpty() && delta.accounts.isEmpty() && delta.transactions.isEmpty()) {
                        mutableState.update { current ->
                            current.copy(screen = ImportUseCase.State.UpToDate)
                        }
                        return@launch
                    }
                    val defaults = categories.associate { it.id to defaultStrategy(it.existingId) }
                    mutableState.update { current ->
                        current.copy(
                            storedDelta = delta,
                            storedCategories = categories,
                            categoryStrategies = defaults,
                            accountStrategies = emptyMap(),
                            existingCategoryById = existingCategoryById,
                            existingCategoryByName = existingCategoryByName,
                            existingAccountById = existingAccountById,
                            existingAccountByName = existingAccountByName,
                            existingTransactionSignatures = existingTransactionSignatures,
                            allIconsById = allIconsById,
                            screen = ImportUseCase.State.CategoriesReview(categories, defaults),
                        )
                    }
                } catch (e: Exception) {
                    mutableState.update { current ->
                        InternalState(
                            selectedSource = current.selectedSource,
                            screen = ImportUseCase.State.SourceSelection(
                                sources = parsers.map { it.source },
                                error = "Couldn't read file. Check the format and try again.",
                            ),
                        )
                    }
                }
            }
            is ImportUseCase.Action.SetCategoryStrategy -> mutableState.update { current ->
                val categories = current.storedCategories ?: return@update current
                val newStrategies = current.categoryStrategies + (action.id to action.strategy)
                current.copy(
                    categoryStrategies = newStrategies,
                    screen = ImportUseCase.State.CategoriesReview(categories, newStrategies),
                )
            }
            is ImportUseCase.Action.SetAccountStrategy -> mutableState.update { current ->
                val accounts = current.storedAccounts ?: return@update current
                val newStrategies = current.accountStrategies + (action.id to action.strategy)
                current.copy(
                    accountStrategies = newStrategies,
                    screen = ImportUseCase.State.AccountsReview(accounts, newStrategies),
                )
            }
            is ImportUseCase.Action.ConfirmCategories -> coroutineScope.launch {
                val current = mutableState.value
                val delta = current.storedDelta ?: return@launch
                val categories = current.storedCategories ?: return@launch
                val nextDelta = applyCategoryStrategies(delta, categories, current.categoryStrategies)
                val accounts = buildAccounts(
                    syncAccounts = nextDelta.accounts,
                    transactions = nextDelta.transactions,
                    existingAccountById = current.existingAccountById,
                    existingAccountByName = current.existingAccountByName,
                    allIconsById = current.allIconsById,
                )
                val defaults = accounts.associate { it.id to defaultStrategy(it.existingId) }
                mutableState.update { cur ->
                    cur.copy(
                        storedDelta = nextDelta,
                        storedAccounts = accounts,
                        accountStrategies = defaults,
                        screen = ImportUseCase.State.AccountsReview(accounts, defaults),
                    )
                }
            }
            is ImportUseCase.Action.ConfirmAccounts -> mutableState.update { current ->
                val delta = current.storedDelta ?: return@update current
                val accounts = current.storedAccounts ?: return@update current
                val categories = current.storedCategories ?: emptyList()
                val afterAccounts = applyAccountStrategies(delta, accounts, current.accountStrategies)
                val nextDelta = dedupeAgainstExisting(afterAccounts, current.existingTransactionSignatures)
                val transactions = toImportTransactions(
                    nextDelta,
                    buildCategoryNameLookup(categories, current.existingCategoryById),
                )
                current.copy(
                    storedDelta = nextDelta,
                    screen = ImportUseCase.State.TransactionsPreview(
                        transactions = transactions,
                        totalCount = transactions.size,
                        accounts = accounts,
                        categories = categories,
                    ),
                )
            }
            is ImportUseCase.Action.Confirm -> {
                val delta = mutableState.value.storedDelta ?: return
                coroutineScope.launch {
                    val userId = currentUserRepository.query().first().id
                    syncEngine.import(delta, userId)
                    coroutineScope.launch(Dispatchers.Main) {
                        onImportFinishedHandler.onFinished()
                    }
                }
            }
            is ImportUseCase.Action.DismissError -> mutableState.update {
                InternalState(screen = ImportUseCase.State.SourceSelection(parsers.map { it.source }))
            }
            is ImportUseCase.Action.Retry -> mutableState.update { current ->
                current.copy(screen = ImportUseCase.State.FilePicker)
            }
            is ImportUseCase.Action.Back -> mutableState.update { current ->
                when (current.screen) {
                    is ImportUseCase.State.FilePicker,
                    is ImportUseCase.State.SourceSelection,
                    is ImportUseCase.State.Loading,
                    is ImportUseCase.State.CategoriesReview,
                    is ImportUseCase.State.UpToDate,
                    -> InternalState(
                        screen = ImportUseCase.State.SourceSelection(parsers.map { it.source }),
                    )
                    is ImportUseCase.State.AccountsReview -> {
                        val categories = current.storedCategories ?: return@update current
                        current.copy(
                            screen = ImportUseCase.State.CategoriesReview(
                                categories = categories,
                                strategies = current.categoryStrategies,
                            ),
                        )
                    }
                    is ImportUseCase.State.TransactionsPreview -> {
                        val accounts = current.storedAccounts ?: return@update current
                        current.copy(
                            screen = ImportUseCase.State.AccountsReview(
                                accounts = accounts,
                                strategies = current.accountStrategies,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.empty()

    private fun buildCategories(
        delta: SyncSnapshot,
        existingCategoryById: Map<Id.Known, CategoryRepository.Category>,
        existingCategoryByName: Map<String, CategoryRepository.Category>,
        allIconsById: Map<Id.Known, Icon>,
    ): List<ImportCategory> {
        val txCountByCategoryId = delta.transactions
            .mapNotNull { transaction -> transaction.categoryId?.let { Id.Known(it) } }
            .groupBy { it }
            .mapValues { it.value.size }
        return delta.categories.map { syncCategory ->
            val existingMatch = existingCategoryById[syncCategory.id]
                ?: existingCategoryByName[syncCategory.name.lowercase()]
            val colorId = (existingMatch?.colorId as? Id.Known)
                ?: syncCategory.colorId?.let { Id.Known(it) }
                ?: ColorRepository.unknownCategoryColorId()
            val iconId = (existingMatch?.iconId as? Id.Known)
                ?: syncCategory.iconId?.let { Id.Known(it) }
                ?: IconRepository.unknownCategoryIconId()
            val icon = allIconsById[iconId] ?: Icon.empty()
            ImportCategory(
                id = syncCategory.id,
                name = syncCategory.name,
                colorScheme = colorRepository.schemeFor(colorId),
                icon = icon.image,
                transactionCount = txCountByCategoryId[syncCategory.id] ?: 0,
                existingId = existingMatch?.id,
            )
        }
    }

    private fun buildAccounts(
        syncAccounts: List<SyncAccount>,
        transactions: List<SyncTransaction>,
        existingAccountById: Map<Id.Known, AccountRepository.Account>,
        existingAccountByName: Map<String, AccountRepository.Account>,
        allIconsById: Map<Id.Known, Icon>,
    ): List<ImportAccount> {
        val txByAccountId = transactions.groupBy { it.accountId }
        return syncAccounts.map { syncAccount ->
            val existingMatch = existingAccountById[syncAccount.id]
                ?: existingAccountByName[syncAccount.name.lowercase()]
            val icon = existingMatch?.iconId?.let { allIconsById[it]?.image }
            ImportAccount(
                id = syncAccount.id,
                name = syncAccount.name,
                currencyId = syncAccount.currencyId,
                transactionCount = txByAccountId[syncAccount.id]?.size ?: 0,
                icon = icon,
                existingId = existingMatch?.id,
            )
        }
    }

    private fun defaultStrategy(existingId: Id.Known?): ResolveStrategy =
        if (existingId != null) ResolveStrategy.Merge else ResolveStrategy.New

    private fun applyCategoryStrategies(
        delta: SyncSnapshot,
        categories: List<ImportCategory>,
        strategies: Map<Id.Known, ResolveStrategy>,
    ): SyncSnapshot {
        val merges = categories
            .filter { strategies[it.id] == ResolveStrategy.Merge && it.existingId != null }
            .associate { it.id to it.existingId!! }
        val skipped = strategies.filterValues { it == ResolveStrategy.Skip }.keys
        val filteredCategories = delta.categories.filter {
            it.id !in skipped && it.id !in merges.keys
        }
        val filteredTransactions = delta.transactions.mapNotNull { tx ->
            val categoryId = tx.categoryId?.let { Id.Known(it) }
            if (categoryId in skipped) return@mapNotNull null
            val newCategoryId = categoryId?.let { merges[it]?.value ?: it.value }
            tx.copy(categoryId = newCategoryId)
        }
        return delta.copy(categories = filteredCategories, transactions = filteredTransactions)
    }

    private fun applyAccountStrategies(
        delta: SyncSnapshot,
        accounts: List<ImportAccount>,
        strategies: Map<Id.Known, ResolveStrategy>,
    ): SyncSnapshot {
        val merges = accounts
            .filter { strategies[it.id] == ResolveStrategy.Merge && it.existingId != null }
            .associate { it.id to it.existingId!! }
        val skipped = strategies.filterValues { it == ResolveStrategy.Skip }.keys
        val filteredAccounts = delta.accounts.filter {
            it.id !in skipped && it.id !in merges.keys
        }
        val filteredTransactions = delta.transactions.mapNotNull { tx ->
            if (tx.accountId in skipped) return@mapNotNull null
            val targetId = tx.targetAccountId?.let { Id.Known(it) }
            if (targetId in skipped) return@mapNotNull null
            val newAccountId = merges[tx.accountId] ?: tx.accountId
            val newTargetId = targetId?.let { merges[it]?.value ?: it.value }
            tx.copy(accountId = newAccountId, targetAccountId = newTargetId)
        }
        return delta.copy(accounts = filteredAccounts, transactions = filteredTransactions)
    }

    private fun buildCategoryNameLookup(
        importCategories: List<ImportCategory>,
        existingCategoryById: Map<Id.Known, CategoryRepository.Category>,
    ): Map<Id.Known, String> = buildMap {
        importCategories.forEach { put(it.id, it.name) }
        existingCategoryById.forEach { (id, cat) -> put(id, cat.name) }
    }

    private fun toImportTransactions(
        delta: SyncSnapshot,
        categoryNameById: Map<Id.Known, String>,
    ): List<ImportTransaction> {
        return delta.transactions.map { syncTx ->
            val categoryName = syncTx.categoryId?.let { categoryNameById[Id.Known(it)] }
            when (syncTx.type) {
                SyncTransaction.Type.EXPENSE -> ImportTransaction.Expense(
                    id = syncTx.id,
                    accountId = syncTx.accountId,
                    currencyId = syncTx.currencyId,
                    amount = Amount(syncTx.amount.toBigDecimalOrNull()),
                    dateTime = syncTx.enteredDateTime,
                    categoryId = syncTx.categoryId?.let { Id.Known(it) },
                    categoryName = categoryName,
                )
                SyncTransaction.Type.INCOME -> ImportTransaction.Income(
                    id = syncTx.id,
                    accountId = syncTx.accountId,
                    currencyId = syncTx.currencyId,
                    amount = Amount(syncTx.amount.toBigDecimalOrNull()),
                    dateTime = syncTx.enteredDateTime,
                    categoryId = syncTx.categoryId?.let { Id.Known(it) },
                    categoryName = categoryName,
                )
                SyncTransaction.Type.TRANSFER -> ImportTransaction.Transfer(
                    id = syncTx.id,
                    accountId = syncTx.accountId,
                    currencyId = syncTx.currencyId,
                    amount = Amount(syncTx.amount.toBigDecimalOrNull()),
                    dateTime = syncTx.enteredDateTime,
                    targetAccountId = Id.Known(syncTx.targetAccountId ?: syncTx.accountId.value),
                    targetAmount = Amount(syncTx.targetAmount?.toBigDecimalOrNull()),
                    targetCurrencyId = syncTx.currencyId,
                )
            }
        }
    }

    private fun dedupeAgainstExisting(
        delta: SyncSnapshot,
        existingSignatures: Set<TransactionSignature>,
    ): SyncSnapshot {
        if (existingSignatures.isEmpty()) return delta
        val filtered = delta.transactions.filterNot { it.toSignature() in existingSignatures }
        return delta.copy(transactions = filtered)
    }
}

private data class TransactionSignature(
    val type: SyncTransaction.Type,
    val accountId: String,
    val currencyId: String,
    val amount: String,
    val dateTime: String,
    val categoryId: String?,
    val targetAccountId: String?,
    val targetAmount: String?,
)

private fun SyncTransaction.toSignature() = TransactionSignature(
    type = type,
    accountId = accountId.value,
    currencyId = currencyId.value,
    amount = amount.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty(),
    dateTime = enteredDateTime.toString(),
    categoryId = categoryId,
    targetAccountId = targetAccountId,
    targetAmount = targetAmount?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString(),
)

private fun TransactionRepository.Transaction.toSignature(): TransactionSignature {
    val baseAmount = amount.value.stripTrailingZeros().toPlainString()
    val dateStr = dateTime.toString()
    val currency = currencyId.value
    val account = accountId.value
    return when (this) {
        is TransactionRepository.Transaction.Expense -> TransactionSignature(
            type = SyncTransaction.Type.EXPENSE,
            accountId = account,
            currencyId = currency,
            amount = baseAmount,
            dateTime = dateStr,
            categoryId = categoryId.value,
            targetAccountId = null,
            targetAmount = null,
        )
        is TransactionRepository.Transaction.Income -> TransactionSignature(
            type = SyncTransaction.Type.INCOME,
            accountId = account,
            currencyId = currency,
            amount = baseAmount,
            dateTime = dateStr,
            categoryId = categoryId.value,
            targetAccountId = null,
            targetAmount = null,
        )
        is TransactionRepository.Transaction.Transfer -> TransactionSignature(
            type = SyncTransaction.Type.TRANSFER,
            accountId = account,
            currencyId = currency,
            amount = baseAmount,
            dateTime = dateStr,
            categoryId = null,
            targetAccountId = targetAccount.value,
            targetAmount = targetAmount.value.stripTrailingZeros().toPlainString(),
        )
    }
}
