package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import kotlinx.coroutines.flow.Flow
import com.hluhovskyi.zero.common.Color as ColorValue

interface ColorRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    sealed interface Criteria<T> {
        class All : Criteria<List<Color>>
        data class ById(val id: Id.Known) : Criteria<Color>
    }

    data class Color(
        override val id: Id.Known,
        val color: ColorValue,
    ) : Identifiable

    companion object {

        fun unknownCategoryColorId(): Id.Known = Id("unknown_category_color")
    }
}