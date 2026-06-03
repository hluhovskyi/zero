package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.categories.CategoryType

enum class TransactionEditType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

/** Category type to rank/filter by; null for transfers, which have no category. */
internal fun TransactionEditType.categoryType(): CategoryType? = when (this) {
    TransactionEditType.EXPENSE -> CategoryType.EXPENSE
    TransactionEditType.INCOME -> CategoryType.INCOME
    TransactionEditType.TRANSFER -> null
}
