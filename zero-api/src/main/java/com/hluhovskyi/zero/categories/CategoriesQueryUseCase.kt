package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow

interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>

    fun queryAll(): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    ) : Identifiable
}
