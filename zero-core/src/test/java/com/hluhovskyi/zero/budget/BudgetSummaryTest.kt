package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BudgetSummaryTest {

    @Test
    fun `empty list returns zero summary`() {
        val summary = emptyList<BudgetQueryUseCase.Budgeted>().toSummary()

        assertEquals(Amount.zero(), summary.totalBudgeted)
        assertEquals(Amount.zero(), summary.totalSpent)
        assertEquals(0, summary.overCount)
        assertEquals(0f, summary.overallPct, 0f)
        assertFalse(summary.isOver)
    }

    @Test
    fun `unset rows are ignored when computing totals`() {
        val rows = listOf(
            row("c1", budgetId = "b1", budgeted = "100", spent = "40"),
            row("c2", budgetId = null, budgeted = "0", spent = "999"),
        )

        val summary = rows.toSummary()

        assertEquals(BigDecimal("100"), summary.totalBudgeted.value)
        assertEquals(BigDecimal("40"), summary.totalSpent.value)
        assertEquals(0, summary.overCount)
        assertEquals(0.4f, summary.overallPct, 0.0001f)
        assertFalse(summary.isOver)
    }

    @Test
    fun `all under budget returns isOver false`() {
        val rows = listOf(
            row("c1", budgetId = "b1", budgeted = "200", spent = "50"),
            row("c2", budgetId = "b2", budgeted = "300", spent = "100"),
        )

        val summary = rows.toSummary()

        assertEquals(BigDecimal("500"), summary.totalBudgeted.value)
        assertEquals(BigDecimal("150"), summary.totalSpent.value)
        assertEquals(0, summary.overCount)
        assertEquals(0.3f, summary.overallPct, 0.0001f)
        assertFalse(summary.isOver)
    }

    @Test
    fun `one category over budget increments overCount`() {
        val rows = listOf(
            row("c1", budgetId = "b1", budgeted = "100", spent = "150"),
            row("c2", budgetId = "b2", budgeted = "200", spent = "50"),
        )

        val summary = rows.toSummary()

        assertEquals(1, summary.overCount)
        assertEquals(BigDecimal("300"), summary.totalBudgeted.value)
        assertEquals(BigDecimal("200"), summary.totalSpent.value)
        assertFalse(summary.isOver)
    }

    @Test
    fun `total over budget sets isOver and clips pct at one`() {
        val rows = listOf(
            row("c1", budgetId = "b1", budgeted = "100", spent = "200"),
        )

        val summary = rows.toSummary()

        assertTrue(summary.isOver)
        assertEquals(1, summary.overCount)
        assertEquals(1f, summary.overallPct, 0f)
    }

    private fun row(
        categoryId: String,
        budgetId: String?,
        budgeted: String,
        spent: String,
    ) = BudgetQueryUseCase.Budgeted(
        categoryId = Id.Known(categoryId),
        categoryName = "Cat $categoryId",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        spent = Amount(BigDecimal(spent)),
        budgetId = budgetId?.let { Id.Known(it) },
        budgeted = Amount(BigDecimal(budgeted)),
    )
}
