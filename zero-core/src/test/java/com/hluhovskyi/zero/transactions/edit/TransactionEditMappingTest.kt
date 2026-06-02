package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TransactionEditMappingTest {

    private val now = LocalDateTime(2024, 6, 5, 12, 0)
    private val picked = LocalDateTime(2024, 1, 2, 9, 30)

    private val usd = TransactionEditCurrency(Id.Known("USD"), "US Dollar", "$")
    private val eur = TransactionEditCurrency(Id.Known("EUR"), "Euro", "€")
    private val wallet = TransactionEditAccount(Id.Known("acc-usd"), "Wallet", Id.Known("USD"))
    private val revolut = TransactionEditAccount(Id.Known("acc-eur"), "Revolut", Id.Known("EUR"))
    private val food = TransactionEditCategory(Id.Known("cat"), "Food", ColorScheme.Grey, Image.empty())
    private fun category(id: String) = TransactionEditCategory(Id.Known(id), id, ColorScheme.Grey, Image.empty(), CategoryType.EXPENSE)

    // ── buildTransaction ───────────────────────────────────────────────────────────────────

    @Test
    fun `expense uses picked currency, falls back to now when no date`() {
        val state = TransactionEditState(
            transactionType = TransactionEditType.EXPENSE,
            selectedAccount = wallet,
            selectedCategory = food,
            selectedCurrency = eur,
            amount = "12.50",
            rate = "0.9",
            notes = "lunch",
            localDateTime = null,
        )

        val tx = buildTransaction(state, Id.Known("t"), now) as TransactionRepository.Transaction.Expense

        assertEquals(Id.Known("t"), tx.id)
        assertEquals(Amount(BigDecimal("12.50")), tx.amount)
        assertEquals(wallet.id, tx.accountId)
        assertEquals(eur.id, tx.currencyId)
        assertEquals(food.id, tx.categoryId)
        assertEquals(now, tx.dateTime)
        assertEquals(now, tx.updatedDateTime)
        assertEquals(BigDecimal("0.9"), tx.rate.value)
        assertEquals("lunch", tx.notes)
    }

    @Test
    fun `income uses picked date, blank notes become null, currency falls back to account`() {
        val state = TransactionEditState(
            transactionType = TransactionEditType.INCOME,
            selectedAccount = wallet,
            selectedCategory = food,
            selectedCurrency = null,
            amount = "100",
            notes = "   ",
            localDateTime = picked,
        )

        val tx = buildTransaction(state, Id.Known("t"), now) as TransactionRepository.Transaction.Income

        assertEquals(wallet.currencyId, tx.currencyId)
        assertEquals(picked, tx.dateTime)
        assertNull(tx.notes)
    }

    @Test
    fun `transfer saves target account, target amount, source-account currency`() {
        val state = TransactionEditState(
            transactionType = TransactionEditType.TRANSFER,
            selectedAccount = wallet,
            selectedTargetAccount = revolut,
            amount = "100",
            targetAmount = "86",
            localDateTime = picked,
        )

        val tx = buildTransaction(state, Id.Known("t"), now) as TransactionRepository.Transaction.Transfer

        assertEquals(wallet.currencyId, tx.currencyId)
        assertEquals(revolut.id, tx.targetAccount)
        assertEquals(Amount(BigDecimal("100")), tx.amount)
        assertEquals(Amount(BigDecimal("86")), tx.targetAmount)
    }

    @Test
    fun `missing account returns null`() {
        assertNull(buildTransaction(TransactionEditState(selectedAccount = null), Id.Known("t"), now))
    }

    @Test
    fun `expense without category returns null`() {
        val state = TransactionEditState(transactionType = TransactionEditType.EXPENSE, selectedAccount = wallet, selectedCategory = null)
        assertNull(buildTransaction(state, Id.Known("t"), now))
    }

    @Test
    fun `transfer without target account returns null`() {
        val state = TransactionEditState(transactionType = TransactionEditType.TRANSFER, selectedAccount = wallet, selectedTargetAccount = null)
        assertNull(buildTransaction(state, Id.Known("t"), now))
    }

    // ── seedEditState ──────────────────────────────────────────────────────────────────────

    private val loaded = TransactionEditState(
        accounts = listOf(wallet, revolut),
        allCategories = listOf(category("a"), category("b"), category("c")),
        currencies = listOf(usd, eur),
    )

    @Test
    fun `edit expense resolves account, currency, category to front, sets rate, clears auto`() {
        val expense = TransactionRepository.Transaction.Expense(
            id = Id.Known("t"),
            amount = Amount(BigDecimal("42")),
            accountId = wallet.id,
            currencyId = eur.id,
            dateTime = now,
            updatedDateTime = now,
            categoryId = Id.Known("c"),
            rate = Rate(BigDecimal("0.9")),
            notes = "lunch",
        )

        val seeded = seedEditState(loaded, expense, isDuplicate = false)

        assertEquals(TransactionEditType.EXPENSE, seeded.transactionType)
        assertEquals("42", seeded.amount)
        assertEquals(wallet, seeded.selectedAccount)
        assertEquals(eur, seeded.selectedCurrency)
        assertEquals(Id.Known("c"), seeded.selectedCategory?.id)
        assertEquals(listOf("c", "a", "b"), seeded.allCategories.map { it.id.value })
        assertEquals("0.9", seeded.rate)
        assertFalse(seeded.rateAuto)
        assertEquals("lunch", seeded.notes)
        assertNull(seeded.sourceSnapshot)
    }

    @Test
    fun `duplicate captures source snapshot`() {
        val expense = TransactionRepository.Transaction.Expense(
            id = Id.Known("t"),
            amount = Amount(BigDecimal("42")),
            accountId = wallet.id,
            currencyId = usd.id,
            dateTime = now,
            updatedDateTime = now,
            categoryId = Id.Known("a"),
            rate = Rate.Same,
        )

        val snapshot = requireNotNull(seedEditState(loaded, expense, isDuplicate = true).sourceSnapshot)

        assertEquals("42", snapshot.amount)
        assertEquals(now, snapshot.date)
        assertEquals("$", snapshot.currencySymbol)
    }

    @Test
    fun `transfer resolves target account and derives rate from amounts`() {
        val transfer = TransactionRepository.Transaction.Transfer(
            id = Id.Known("t"),
            amount = Amount(BigDecimal("100")),
            accountId = wallet.id,
            currencyId = usd.id,
            dateTime = now,
            updatedDateTime = now,
            targetAccount = revolut.id,
            targetAmount = Amount(BigDecimal("86")),
        )

        val seeded = seedEditState(loaded, transfer, isDuplicate = false)

        assertEquals(TransactionEditType.TRANSFER, seeded.transactionType)
        assertEquals(revolut, seeded.selectedTargetAccount)
        assertEquals("86", seeded.targetAmount)
        assertEquals("0.86", seeded.rate)
        assertFalse(seeded.rateAuto)
    }

    @Test
    fun `transfer with zero source amount falls back to rate 1`() {
        val transfer = TransactionRepository.Transaction.Transfer(
            id = Id.Known("t"),
            amount = Amount(BigDecimal.ZERO),
            accountId = wallet.id,
            currencyId = usd.id,
            dateTime = now,
            updatedDateTime = now,
            targetAccount = revolut.id,
            targetAmount = Amount(BigDecimal.ZERO),
        )

        assertEquals("1", seedEditState(loaded, transfer, isDuplicate = false).rate)
    }
}
