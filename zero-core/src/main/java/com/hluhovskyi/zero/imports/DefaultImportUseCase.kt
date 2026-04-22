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
    private val onImportFinishedHandler: OnImportFinishedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ImportUseCase {

    private data class InternalState(
        val selectedSource: Source? = null,
        val storedDelta: SyncSnapshot? = null,
        val storedCategories: List<ImportCategory>? = null,
        val storedAccounts: List<ImportAccount>? = null,
        val excludedCategoryIds: Set<Id.Known> = emptySet(),
        val existingCategoryByName: Map<String, CategoryRepository.Category> = emptyMap(),
        val existingAccountByName: Map<String, AccountRepository.Account> = emptyMap(),
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
                    val existingCategoryByName = existingCategories.associateBy { it.name.lowercase() }

                    val existingAccounts = accountRepository.query(AccountRepository.Criteria.All()).first()
                    val existingAccountByName = existingAccounts.associateBy { it.name.lowercase() }

                    val categories = buildCategories(delta, existingCategoryByName, allIconsById)
                    mutableState.update { current ->
                        current.copy(
                            storedDelta = delta,
                            storedCategories = categories,
                            excludedCategoryIds = emptySet(),
                            existingCategoryByName = existingCategoryByName,
                            existingAccountByName = existingAccountByName,
                            allIconsById = allIconsById,
                            screen = ImportUseCase.State.CategoriesReview(categories = categories),
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
            is ImportUseCase.Action.ToggleCategory -> mutableState.update { current ->
                val id = action.id
                val newExcluded = if (id in current.excludedCategoryIds) {
                    current.excludedCategoryIds - id
                } else {
                    current.excludedCategoryIds + id
                }
                val categories = current.storedCategories ?: return@update current
                current.copy(
                    excludedCategoryIds = newExcluded,
                    screen = ImportUseCase.State.CategoriesReview(
                        categories = categories,
                        excludedIds = newExcluded,
                    ),
                )
            }
            is ImportUseCase.Action.ConfirmCategories -> coroutineScope.launch {
                val current = mutableState.value
                val delta = current.storedDelta ?: return@launch
                val excluded = current.excludedCategoryIds
                val filteredDelta = if (excluded.isEmpty()) {
                    delta
                } else {
                    delta.copy(
                        categories = delta.categories.filter { it.id !in excluded },
                        transactions = delta.transactions.filter { transaction ->
                            Id(transaction.categoryId) !in excluded
                        },
                    )
                }
                val accounts = buildAccounts(
                    syncAccounts = filteredDelta.accounts,
                    transactions = filteredDelta.transactions,
                    existingAccountByName = current.existingAccountByName,
                    allIconsById = current.allIconsById,
                )
                mutableState.update { cur ->
                    cur.copy(
                        storedDelta = filteredDelta,
                        storedAccounts = accounts,
                        screen = ImportUseCase.State.AccountsReview(accounts = accounts),
                    )
                }
            }
            is ImportUseCase.Action.ConfirmAccounts -> mutableState.update { current ->
                val delta = current.storedDelta ?: return@update current
                val accounts = current.storedAccounts ?: return@update current
                val categoryById = delta.categories.associateBy { it.id }
                val transactions = delta.transactions.map { syncTx ->
                    val categoryName = syncTx.categoryId?.let { categoryById[Id.Known(it)]?.name }
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
                current.copy(
                    screen = ImportUseCase.State.TransactionsPreview(
                        transactions = transactions,
                        totalCount = transactions.size,
                        accounts = accounts,
                        categories = current.storedCategories ?: emptyList(),
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
                    -> InternalState(
                        screen = ImportUseCase.State.SourceSelection(parsers.map { it.source }),
                    )
                    is ImportUseCase.State.AccountsReview -> {
                        val categories = current.storedCategories ?: return@update current
                        current.copy(
                            screen = ImportUseCase.State.CategoriesReview(
                                categories = categories,
                                excludedIds = current.excludedCategoryIds,
                            ),
                        )
                    }
                    is ImportUseCase.State.TransactionsPreview -> {
                        val accounts = current.storedAccounts ?: return@update current
                        current.copy(
                            screen = ImportUseCase.State.AccountsReview(accounts = accounts),
                        )
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.empty()

    private fun buildCategories(
        delta: SyncSnapshot,
        existingCategoryByName: Map<String, CategoryRepository.Category>,
        allIconsById: Map<Id.Known, Icon>,
    ): List<ImportCategory> {
        val txCountByCategoryId = delta.transactions
            .mapNotNull { transaction -> transaction.categoryId?.let { Id.Known(it) } }
            .groupBy { it }
            .mapValues { it.value.size }
        return delta.categories.map { syncCategory ->
            val existingMatch = existingCategoryByName[syncCategory.name.lowercase()]
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
            )
        }
    }

    private fun buildAccounts(
        syncAccounts: List<SyncAccount>,
        transactions: List<SyncTransaction>,
        existingAccountByName: Map<String, AccountRepository.Account>,
        allIconsById: Map<Id.Known, Icon>,
    ): List<ImportAccount> {
        val txByAccountId = transactions.groupBy { it.accountId }
        return syncAccounts.map { syncAccount ->
            val existingMatch = existingAccountByName[syncAccount.name.lowercase()]
            val icon = existingMatch?.iconId?.let { allIconsById[it]?.image }
            ImportAccount(
                id = syncAccount.id,
                name = syncAccount.name,
                currencyId = syncAccount.currencyId,
                transactionCount = txByAccountId[syncAccount.id]?.size ?: 0,
                icon = icon,
            )
        }
    }
}
