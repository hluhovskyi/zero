package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal interface PeriodResolver {

    fun today(): LocalDate

    fun currentMonth(): Pair<LocalDate, LocalDate>

    fun monthOffsetFrom(reference: LocalDate, offsetMonths: Int): Pair<LocalDate, LocalDate>
}

internal class DefaultPeriodResolver(
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : PeriodResolver {

    override fun today(): LocalDate = clock.now().toLocalDateTime(zoneProvider.timeZone()).date

    override fun currentMonth(): Pair<LocalDate, LocalDate> = monthOffsetFrom(today(), 0)

    override fun monthOffsetFrom(reference: LocalDate, offsetMonths: Int): Pair<LocalDate, LocalDate> {
        val anchor = LocalDate(reference.year, reference.month, 1).plus(offsetMonths, DateTimeUnit.MONTH)
        val end = anchor.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return anchor to end
    }
}
