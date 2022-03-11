package com.hluhovskyi.zero.common

data class Category(
    val id: Id.Known,
    val parentCategoryId: Id,
    val name: String,
    val icon: Image,
)