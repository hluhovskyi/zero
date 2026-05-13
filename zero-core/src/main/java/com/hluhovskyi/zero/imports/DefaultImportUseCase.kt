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
        val originalDelta: SyncSnapshot? = null,
        val storedCategories: List<ImportCategory>? = null,
        val storedAccounts: List<ImportAccount>? = null,
        val categoryStrategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
        val accountStrategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
        // Keyed by imported sync-id, valued by the local DB row it matches
        // (either by id or by case-insensitive name). Only contains entries for matches.
        val matchedCategoryByImportId: Map<Id.Known, CategoryRepository.Category> = emptyMap(),
        val matchedAccountByImportId: Map<Id.Known, AccountRepository.Account> = emptyMap(),
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
                    val matchedCategoryByImportId = matchExistingByImportId(
                        syncEntities = delta.categories,
                        existing = existingCategories,
                        syncId = { it.id },
                        syncName = { it.name },
                        existingId = { it.id },
                        existingName = { it.name },
                    )

                    val existingAccounts = accountRepository.query(AccountRepository.Criteria.All()).first()
                    val matchedAccountByImportId = matchExistingByImportId(
                        syncEntities = delta.accounts,
                        existing = existingAccounts,
                        syncId = { it.id },
                        syncName = { it.name },
                        existingId = { it.id },
                        existingName = { it.name },
                    )

                    val existingTransactionSignatures = transactionRepository
                        .query(TransactionRepository.Criteria.All())
                        .first()
                        .map { it.toSignature() }
                        .toSet()

                    val duplicateTxIds = computeDuplicateTxIds(
                        transactions = delta.transactions,
                        categoryRemap = matchedCategoryByImportId.mapValues { it.value.id },
                        accountRemap = matchedAccountByImportId.mapValues { it.value.id },
                        existingSignatures = existingTransactionSignatures,
                    )
                    val categories = buildCategories(
                        delta = delta,
                        matchedCategoryByImportId = matchedCategoryByImportId,
                        duplicateTxIds = duplicateTxIds,
                        allIconsById = allIconsById,
                    )
                    if (categories.isEmpty() && delta.accounts.isEmpty() && delta.transactions.isEmpty()) {
                        mutableState.update { current ->
                            current.copy(screen = ImportUseCase.State.UpToDate)
                        }
                        return@launch
                    }
                    val defaults = categories.associate { it.id to defaultStrategy(it.existingId) }
                    mutableState.update { current ->
                        current.copy(
                            originalDelta = delta,
                            storedCategories = categories,
                            categoryStrategies = defaults,
                            accountStrategies = emptyMap(),
                            matchedCategoryByImportId = matchedCategoryByImportId,
                            matchedAccountByImportId = matchedAccountByImportId,
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
                val original = current.originalDelta ?: return@launch
                val categories = current.storedCategories ?: return@launch
                val afterCat = applyCategoryStrategies(original, categories, current.categoryStrategies)
                val accountDuplicateTxIds = computeDuplicateTxIds(
                    transactions = afterCat.transactions,
                    // categoryIds were already remapped by applyCategoryStrategies above
                    categoryRemap = emptyMap(),
                    accountRemap = current.matchedAccountByImportId.mapValues { it.value.id },
                    existingSignatures = current.existingTransactionSignatures,
                )
                val accounts = buildAccounts(
                    syncAccounts = afterCat.accounts,
                    transactions = afterCat.transactions,
                    matchedAccountByImportId = current.matchedAccountByImportId,
                    duplicateTxIds = accountDuplicateTxIds,
                    allIconsById = current.allIconsById,
                )
                val priorStrategies = current.accountStrategies
                val accountStrategies = accounts.associate { acc ->
                    acc.id to (priorStrategies[acc.id] ?: defaultStrategy(acc.existingId))
                }
                mutableState.update { cur ->
                    cur.copy(
                        storedAccounts = accounts,
                        accountStrategies = accountStrategies,
                        screen = ImportUseCase.State.AccountsReview(accounts, accountStrategies),
                    )
                }
            }
            is ImportUseCase.Action.ConfirmAccounts -> coroutineScope.launch {
                val current = mutableState.value
                val processed = processedDelta(current) ?: return@launch
                val categories = current.storedCategories ?: emptyList()
                val accounts = current.storedAccounts ?: emptyList()
                val categoryNameById = categoryNameLookup(categories, current.matchedCategoryByImportId)
                val transactions = toImportTransactions(processed, categoryNameById)
                mutableState.update { cur ->
                    cur.copy(
                        screen = ImportUseCase.State.TransactionsPreview(
                            transactions = transactions,
                            totalCount = transactions.size,
                            accounts = accounts,
                            categories = categories,
                        ),
                    )
                }
            }
            is ImportUseCase.Action.Confirm -> {
                val processed = processedDelta(mutableState.value) ?: return
                coroutineScope.launch {
                    val userId = currentUserRepository.query().first().id
                    syncEngine.import(processed, userId)
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
        matchedCategoryByImportId: Map<Id.Known, CategoryRepository.Category>,
        duplicateTxIds: Set<Id.Known>,
        allIconsById: Map<Id.Known, Icon>,
    ): List<ImportCategory> {
        val txsByCategoryId = delta.transactions
            .filter { it.categoryId != null }
            .groupBy { Id.Known(it.categoryId!!) }
        return delta.categories.map { syncCategory ->
            val match = matchedCategoryByImportId[syncCategory.id]
            val colorId = (match?.colorId as? Id.Known)
                ?: syncCategory.colorId?.let { Id.Known(it) }
                ?: ColorRepository.unknownCategoryColorId()
            val iconId = (match?.iconId as? Id.Known)
                ?: syncCategory.iconId?.let { Id.Known(it) }
                ?: IconRepository.unknownCategoryIconId()
            val icon = allIconsById[iconId] ?: Icon.empty()
            val txs = txsByCategoryId[syncCategory.id].orEmpty()
            ImportCategory(
                id = syncCategory.id,
                name = syncCategory.name,
                colorScheme = colorRepository.schemeFor(colorId),
                icon = icon.image,
                transactionCount = txs.size,
                newTransactionCount = txs.count { it.id !in duplicateTxIds },
                existingId = match?.id,
            )
        }
    }

    private fun buildAccounts(
        syncAccounts: List<SyncAccount>,
        transactions: List<SyncTransaction>,
        matchedAccountByImportId: Map<Id.Known, AccountRepository.Account>,
        duplicateTxIds: Set<Id.Known>,
        allIconsById: Map<Id.Known, Icon>,
    ): List<ImportAccount> {
        val txByAccountId = transactions.groupBy { it.accountId }
        return syncAccounts.map { syncAccount ->
            val match = matchedAccountByImportId[syncAccount.id]
            val icon = match?.iconId?.let { allIconsById[it]?.image }
            val txs = txByAccountId[syncAccount.id].orEmpty()
            ImportAccount(
                id = syncAccount.id,
                name = syncAccount.name,
                currencyId = syncAccount.currencyId,
                transactionCount = txs.size,
                newTransactionCount = txs.count { it.id !in duplicateTxIds },
                icon = icon,
                existingId = match?.id,
            )
        }
    }

    /**
     * For each incoming sync entity, find the local DB row it should be matched
     * against — by id first, then by case-insensitive name as fallback. Entries are
     * omitted when no match exists.
     */
    private inline fun <Sync, Existing> matchExistingByImportId(
        syncEntities: List<Sync>,
        existing: List<Existing>,
        syncId: (Sync) -> Id.Known,
        syncName: (Sync) -> String,
        existingId: (Existing) -> Id.Known,
        existingName: (Existing) -> String,
    ): Map<Id.Known, Existing> {
        val existingById = existing.associateBy(existingId)
        val existingByName = existing.associateBy { existingName(it).lowercase() }
        return syncEntities.mapNotNull { sync ->
            val match = existingById[syncId(sync)] ?: existingByName[syncName(sync).lowercase()]
            match?.let { syncId(sync) to it }
        }.toMap()
    }

    private fun computeDuplicateTxIds(
        transactions: List<SyncTransaction>,
        categoryRemap: Map<Id.Known, Id.Known>,
        accountRemap: Map<Id.Known, Id.Known>,
        existingSignatures: Set<TransactionSignature>,
    ): Set<Id.Known> {
        if (existingSignatures.isEmpty()) return emptySet()
        return transactions
            .filter { tx -> tx.signatureAfterRemap(categoryRemap, accountRemap) in existingSignatures }
            .map { it.id }
            .toSet()
    }

    private fun categoryNameLookup(
        importCategories: List<ImportCategory>,
        matchedCategoryByImportId: Map<Id.Known, CategoryRepository.Category>,
    ): Map<Id.Known, String> = buildMap {
        importCategories.forEach { put(it.id, it.name) }
        matchedCategoryByImportId.values.forEach { put(it.id, it.name) }
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

    private fun processedDelta(state: InternalState): SyncSnapshot? {
        val original = state.originalDelta ?: return null
        val categories = state.storedCategories ?: emptyList()
        val accounts = state.storedAccounts ?: emptyList()
        val afterCat = applyCategoryStrategies(original, categories, state.categoryStrategies)
        val afterAcc = applyAccountStrategies(afterCat, accounts, state.accountStrategies)
        return dedupeAgainstExisting(afterAcc, state.existingTransactionSignatures)
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
