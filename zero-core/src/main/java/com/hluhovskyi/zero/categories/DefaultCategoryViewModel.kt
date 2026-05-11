package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultCategoryViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : CategoryViewModel {

    private val mutableState = MutableStateFlow(CategoryViewModel.State())
    override val state: Flow<CategoryViewModel.State> = mutableState

    override fun perform(action: CategoryViewModel.Action) {
        when (action) {
            is CategoryViewModel.Action.SelectCategory -> coroutineScope.launch(Dispatchers.Main) {
                onCategorySelectedHandler.onSelected(action.category.id)
            }
            is CategoryViewModel.Action.SelectTab -> {
                mutableState.update { it.copy(selectedTab = action.type) }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val currencySymbol = currencyPrimaryUseCase.getPrimaryCurrency().symbol
            mutableState.update { it.copy(currencySymbol = currencySymbol) }

            mutableState
                .map { it.selectedTab }
                .distinctUntilChanged()
                .flatMapLatest { selectedTab ->
                    combine(
                        categoriesQueryUseCase.queryAll(),
                        categorySpendingUseCase.query(CategorySpendingUseCase.Period.CurrentMonth),
                    ) { categories, spendingList ->
                        val spendingById = spendingList.associateBy { it.categoryId }
                        val items = categories
                            .filter { it.type == selectedTab }
                            .map { category ->
                                val spending = spendingById[category.id]
                                CategoryViewModel.CategoryItem(
                                    id = category.id,
                                    name = category.name,
                                    icon = category.icon,
                                    colorScheme = category.colorScheme,
                                    spending = if (spending != null && spending.totalAmount > 0L) {
                                        CategoryViewModel.Spending.Active(
                                            totalAmount = spending.totalAmount,
                                            transactionCount = spending.transactionCount,
                                        )
                                    } else {
                                        CategoryViewModel.Spending.None
                                    },
                                )
                            }
                        val (active, inactive) = items.partition { it.spending is CategoryViewModel.Spending.Active }
                        val grandTotal = active.fold(Amount.zero()) { acc, item ->
                            acc + (item.spending as CategoryViewModel.Spending.Active).totalAmount
                        }
                        val sorted = active.sortedByDescending {
                            (it.spending as CategoryViewModel.Spending.Active).totalAmount.value
                        } + inactive.sortedBy { it.name }
                        sorted to grandTotal
                    }
                }
                .collectLatest { (items, grandTotal) ->
                    mutableState.update { it.copy(categories = items, grandTotal = grandTotal) }
                }
        }
    }
}
