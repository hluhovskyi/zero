package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.accounts.AccountsQueryUseCase
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultTransactionFilterSheetViewModel(
    private val transactionFilterUseCase: TransactionFilterUseCase,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val accountsQueryUseCase: AccountsQueryUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : TransactionFilterSheetViewModel {

    private val mutableState = MutableStateFlow(TransactionFilterSheetViewModel.State())
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
            launch {
                transactionFilterUseCase.pendingFilter.take(1).collect { filter ->
                    mutableState.update { it.copy(activeFilter = filter) }
                }
            }
            combine(
                categoriesQueryUseCase.queryAll().onEmptyReturnEmptyList(),
                accountsQueryUseCase.queryAll().onEmptyReturnEmptyList(),
            ) { categories, accounts ->
                Pair(
                    buildList {
                        add(TransactionFilterSheetViewModel.FilterCategoryItem.All(count = categories.size))
                        categories.sortedBy { it.name }.forEach { c ->
                            add(
                                TransactionFilterSheetViewModel.FilterCategoryItem.Category(
                                    id = c.id,
                                    name = c.name,
                                    colorScheme = c.colorScheme,
                                    icon = c.icon,
                                ),
                            )
                        }
                    },
                    buildList {
                        add(TransactionFilterSheetViewModel.FilterAccountItem.All(count = accounts.size))
                        accounts.sortedBy { it.name }.forEach { a ->
                            add(
                                TransactionFilterSheetViewModel.FilterAccountItem.Account(
                                    id = a.id,
                                    name = a.name,
                                    colorScheme = a.colorScheme,
                                    icon = a.icon,
                                ),
                            )
                        }
                    },
                )
            }.collect { (categories, accounts) ->
                mutableState.update { current ->
                    current.copy(
                        availableCategories = categories,
                        availableAccounts = accounts,
                    )
                }
            }
        }
    }
}
