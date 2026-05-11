package com.hluhovskyi.zero.categories

enum class CategoryType {
    EXPENSE,
    INCOME,
    ;

    companion object {
        fun from(value: String?): CategoryType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}
