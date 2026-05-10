package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

data class TransactionFilter(
    val period: DatePeriod? = null,
    val type: TransactionType = TransactionType.All,
    val categoryIds: Set<Id.Known>? = null,
    val accountIds: Set<Id.Known>? = null,
) {

    val isActive: Boolean
        get() = period != null || type != TransactionType.All || categoryIds != null || accountIds != null

    val activeCount: Int
        get() {
            var n = 0
            if (period != null) n++
            if (type != TransactionType.All) n++
            if (categoryIds != null) n++
            if (accountIds != null) n++
            return n
        }

    companion object {
        val All = TransactionFilter()
        fun forCategory(categoryId: Id.Known) = TransactionFilter(categoryIds = setOf(categoryId))
        fun forAccount(accountId: Id.Known) = TransactionFilter(accountIds = setOf(accountId))
    }

    enum class DatePeriod(val label: String) {
        Today("Today"),
        ThisWeek("This week"),
        ThisMonth("This month"),
        LastMonth("Last month"),
        ThisYear("This year"),
    }

    enum class TransactionType(val label: String) {
        All("All"),
        Expense("Expenses"),
        Income("Income"),
        Transfer("Transfers"),
    }
}
