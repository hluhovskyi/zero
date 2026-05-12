package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

interface ColorRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    fun schemeFor(colorId: Id.Known): ColorScheme

    sealed interface Criteria<T> {
        class All : Criteria<List<Color>>
        data class ById(val id: Id.Known) : Criteria<Color>
        class AllSchemes : Criteria<List<ColorScheme>>
    }

    companion object {

        fun unknownCategoryColorId(): Id.Known = Id("unknown_category_color")
    }
}

fun ColorRepository.schemeForOrGrey(colorId: Id): ColorScheme = (colorId as? Id.Known)?.let { schemeFor(it) } ?: ColorScheme.Grey
