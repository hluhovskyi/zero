package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Color
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow

interface CategoriesQueryUseCase {

    fun queryAll(): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val color: Color
    ) : Identifiable
}