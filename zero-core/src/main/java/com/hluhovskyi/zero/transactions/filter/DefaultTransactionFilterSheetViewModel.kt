package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultTransactionFilterSheetViewModel(
    private val transactionFilterUseCase: TransactionFilterUseCase,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val accountRepository: AccountRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : TransactionFilterSheetViewModel {

    private val mutableState = MutableStateFlow(
        TransactionFilterSheetViewModel.State(activeFilter = transactionFilterUseCase.pendingFilter),
    )
    override val state: Flow<TransactionFilterSheetViewModel.State> = mutableState

    override fun perform(action: TransactionFilterSheetViewModel.Action) {
        when (action) {
            is TransactionFilterSheetViewModel.Action.Apply ->
                transactionFilterUseCase.perform(TransactionFilterUseCase.Action.Apply(action.filter))
            TransactionFilterSheetViewModel.Action.Close ->
                transactionFilterUseCase.perform(TransactionFilterUseCase.Action.Close)
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                categoriesQueryUseCase.queryAll().onEmptyReturnEmptyList(),
                accountRepository.query(AccountRepository.Criteria.All()).onEmptyReturnEmptyList(),
            ) { categories, accounts ->
                mutableState.value.copy(
                    availableCategories = categories.map { c ->
                        TransactionFilterSheetViewModel.FilterCategory(
                            id = c.id,
                            name = c.name,
                            colorScheme = c.colorScheme,
                            icon = c.icon,
                        )
                    }.sortedBy { it.name },
                    availableAccounts = accounts.map { a ->
                        TransactionFilterSheetViewModel.FilterAccount(id = a.id, name = a.name)
                    }.sortedBy { it.name },
                )
            }.collect { mutableState.update { _ -> it } }
        }
    }
}
