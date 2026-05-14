package com.hluhovskyi.zero.budget

enum class BudgetType {
    EXPENSE,
    INCOME,
    ;

    companion object {
        fun from(value: String?): BudgetType = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}
