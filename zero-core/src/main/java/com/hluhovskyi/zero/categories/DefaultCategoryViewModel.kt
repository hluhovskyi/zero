package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.observe
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val configurationRepository: ConfigurationRepository = ConfigurationRepository.Noop,
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

            combine(
                categoriesQueryUseCase.queryAll(),
                categorySpendingUseCase.query(CategorySpendingUseCase.Period.CurrentMonth),
                configurationRepository.observe(CategoryConfigurationKey.HasAddedCategory),
            ) { categories, spendingList, hasAddedCategory ->
                val spendingById = spendingList.associateBy { it.categoryId }
                val byType = CategoryType.entries.associateWith { type ->
                    categories
                        .filter { it.type == type }
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
                }
                val categoriesByType = byType.mapValues { (_, items) ->
                    val (active, inactive) = items.partition { it.spending is CategoryViewModel.Spending.Active }
                    active.sortedByDescending {
                        (it.spending as CategoryViewModel.Spending.Active).totalAmount.value
                    } + inactive.sortedBy { it.name }
                }
                val grandTotalByType = byType.mapValues { (_, items) ->
                    items.fold(Amount.zero()) { acc, item ->
                        when (val spending = item.spending) {
                            is CategoryViewModel.Spending.Active -> acc + spending.totalAmount
                            is CategoryViewModel.Spending.None -> acc
                        }
                    }
                }
                Triple(categoriesByType, grandTotalByType, hasAddedCategory)
            }
                .collectLatest { (categoriesByType, grandTotalByType, hasAddedCategory) ->
                    mutableState.update {
                        it.copy(
                            categoriesByType = categoriesByType,
                            grandTotalByType = grandTotalByType,
                            hasAddedCategory = hasAddedCategory,
                        )
                    }
                }
        }
    }
}
