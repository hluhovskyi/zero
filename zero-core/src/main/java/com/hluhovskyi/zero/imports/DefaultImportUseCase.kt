package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultImportUseCase(
    private val importSourceUseCase: ImportSourceUseCase,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val onImportFinishedHandler: OnImportFinishedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : ImportUseCase {

    private val mutableState = MutableStateFlow(CompositeState())
    override fun perform(action: ImportUseCase.Action) {
        when (action) {
            is ImportUseCase.Action.SelectFile -> {
                mutableState.update { state ->
                    state.copy(fileToImport = action.uri)
                }
                // TODO: Handle disposal
                coroutineScope.launch {
                    val result = importSourceUseCase.load(ImportSourceUseCase.Request.FromFile(action.uri))
                    mutableState.update { state ->
                        state.copy(
                            accounts = result.accounts,
                            categories = result.categories,
                            transactions = result.transactions,
                        )
                    }
                }
            }
            is ImportUseCase.Action.SelectAccounts -> mutableState.update { state ->
                state.copy(selectedAccountIds = action.accountIds)
            }
            is ImportUseCase.Action.SelectCategories -> mutableState.update { state ->
                state.copy(selectedCategoryIds = action.categoryIds)
            }
            is ImportUseCase.Action.SubmitTransactions -> {
                // TODO: Filter by selection
                coroutineScope.launch {
                    val state = mutableState.value
                    launch {
                        val accounts = state.accounts.map { account ->
                            AccountRepository.AccountInsert(
                                id = account.id,
                                name = account.name,
                                currencyId = account.currencyId,
                                iconId = IconRepository.defaultAccountIconId(),
                                initialBalance = Amount.zero(),
                            )
                        }
                        accountRepository.insert(accounts)
                    }
                    launch {
                        val categories = state.categories.map { category ->
                            CategoryRepository.CategoryInsert(
                                id = category.id,
                                name = category.name,
                                parentCategoryId = Id.Unknown,
                                iconId = Id.Unknown,
                                colorId = Id.Unknown,
                            )
                        }
                        categoryRepository.insert(categories)
                    }
                    launch {
                        val transactions = state.transactions.map { transaction ->
                            when (transaction) {
                                is ImportTransaction.Expense -> TransactionRepository.Transaction.Expense(
                                    id = transaction.id,
                                    amount = transaction.amount,
                                    accountId = transaction.accountId,
                                    currencyId = transaction.currencyId,
                                    categoryId = transaction.categoryId,
                                    dateTime = transaction.dateTime,
                                    // TODO: Handle rate
                                    rate = Rate.Same
                                )
                                is ImportTransaction.Income -> TransactionRepository.Transaction.Income(
                                    id = transaction.id,
                                    amount = transaction.amount,
                                    accountId = transaction.accountId,
                                    currencyId = transaction.currencyId,
                                    categoryId = transaction.categoryId,
                                    dateTime = transaction.dateTime,
                                    // TODO: Handle rate
                                    rate = Rate.Same
                                )
                                is ImportTransaction.Transfer -> TransactionRepository.Transaction.Transfer(
                                    id = transaction.id,
                                    amount = transaction.amount,
                                    currencyId = transaction.currencyId,
                                    accountId = transaction.accountId,
                                    dateTime = transaction.dateTime,
                                    targetAmount = transaction.targetAmount,
                                    targetAccount = transaction.targetAccount
                                )
                            }
                        }
                        transactionRepository.insert(transactions)
                    }
                }.invokeOnCompletion { throwable ->
                    if (throwable != null) {
                        // TODO: Actually, doesn't work
                        coroutineScope.launch(context = Dispatchers.Main) {
                            onImportFinishedHandler.onFinished()
                        }
                    } else {
                        // TODO: Handle error
                    }
                }
            }
        }
    }

    override val state: Flow<ImportUseCase.State> = mutableState
        .mapNotNull { state ->
            when {
                state.selectedCategoryIds.isNotEmpty() -> ImportUseCase.State.TransactionsPreview(state.transactions)
                state.selectedAccountIds.isNotEmpty() -> ImportUseCase.State.CategoriesPicker(state.categories)
                state.fileToImport is Uri.NonEmpty -> ImportUseCase.State.AccountsPicker(state.accounts)
                else -> ImportUseCase.State.FilePicker
            }
        }

    override fun attach(): Closeable = Closeables.empty()

    private data class CompositeState(
        val fileToImport: Uri = Uri.Empty,
        val accounts: List<ImportAccount> = emptyList(),
        val selectedAccountIds: List<Id.Known> = emptyList(),
        val categories: List<ImportCategory> = emptyList(),
        val selectedCategoryIds: List<Id.Known> = emptyList(),
        val transactions: List<ImportTransaction> = emptyList(),
    )
}