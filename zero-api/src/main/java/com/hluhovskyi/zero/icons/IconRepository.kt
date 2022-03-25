package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow

interface IconRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    sealed interface Criteria<T> {

        class All : Criteria<List<Icon>>

        data class ById(val id: Id.Known) : Criteria<Icon>
    }

    companion object {

        fun unknownCategoryIconId(): Id.Known = Id("unknown_category_icon")
    }
}