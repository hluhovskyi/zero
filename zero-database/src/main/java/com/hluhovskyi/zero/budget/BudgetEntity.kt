package com.hluhovskyi.zero.budget

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

@Entity(
    indices = [Index("userId"), Index("categoryId")],
)
internal data class BudgetEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val categoryId: Id.Known,
    val type: String = BudgetType.EXPENSE.name,
    val amount: BigDecimal,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
)
