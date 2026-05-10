package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.DatePeriod as KotlinDatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus

data class TransactionFilter(
    val period: DatePeriod? = null,
    val type: TransactionType = TransactionType.All,
    val categoryIds: Set<Id.Known>? = null,
    val accountIds: Set<Id.Known>? = null,
) {

    val activeCount: Int = run {
        var n = 0
        if (period != null) n++
        if (type != TransactionType.All) n++
        if (categoryIds != null) n++
        if (accountIds != null) n++
        n
    }

    val isActive: Boolean = activeCount > 0

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

fun TransactionFilter.DatePeriod.toDateRange(today: LocalDate): DateRange = when (this) {
    TransactionFilter.DatePeriod.Today -> DateRange(today, today)
    TransactionFilter.DatePeriod.ThisWeek -> {
        val daysFromMonday = today.dayOfWeek.value - 1
        DateRange(today.minus(KotlinDatePeriod(days = daysFromMonday)), today)
    }
    TransactionFilter.DatePeriod.ThisMonth -> DateRange(LocalDate(today.year, today.month, 1), today)
    TransactionFilter.DatePeriod.LastMonth -> {
        val firstOfThisMonth = LocalDate(today.year, today.month, 1)
        val lastDayOfLastMonth = firstOfThisMonth.minus(KotlinDatePeriod(days = 1))
        DateRange(LocalDate(lastDayOfLastMonth.year, lastDayOfLastMonth.month, 1), lastDayOfLastMonth)
    }
    TransactionFilter.DatePeriod.ThisYear -> DateRange(LocalDate(today.year, Month.JANUARY, 1), today)
}
