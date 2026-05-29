package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>
    fun queryAll(): Flow<List<Category>>
    fun queryRanked(signals: Flow<RankSignal>): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val type: CategoryType = CategoryType.EXPENSE,
    ) : Identifiable

    sealed class RankSignal {
        data class AccountChanged(val accountId: Id.Known?) : RankSignal()
        data class DateChanged(val date: LocalDate?) : RankSignal()
        data class AmountChanged(val amount: BigDecimal?) : RankSignal()
    }
}
