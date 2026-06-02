package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TransactionEditLoaderTest {

    private val loader = DefaultTransactionEditLoader(
        transactionRepository = TransactionRepository.Noop,
        incorrectStateDetector = IncorrectStateDetector.ignoreIncorrect(),
    )

    private val dateTime = LocalDateTime(2024, 6, 5, 12, 0)
    private val usd = TransactionEditCurrency(Id.Known("USD"), "US Dollar", "$")
    private val eur = TransactionEditCurrency(Id.Known("EUR"), "Euro", "€")
    private val wallet = TransactionEditAccount(Id.Known("acc-usd"), "Wallet", Id.Known("USD"))
    private val revolut = TransactionEditAccount(Id.Known("acc-eur"), "Revolut", Id.Known("EUR"))
    private fun category(id: String) = TransactionEditCategory(Id.Known(id), id, ColorScheme.Grey, Image.empty(), CategoryType.EXPENSE)

    // Reference data already loaded onto the state before seeding.
    private val state = TransactionEditState(
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
            dateTime = dateTime,
            updatedDateTime = dateTime,
            categoryId = Id.Known("c"),
            rate = Rate(BigDecimal("0.9")),
            notes = "lunch",
        )

        val seeded = loader.seed(state, expense, isDuplicate = false)

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
            dateTime = dateTime,
            updatedDateTime = dateTime,
            categoryId = Id.Known("a"),
            rate = Rate.Same,
        )

        val seeded = loader.seed(state, expense, isDuplicate = true)

        val snapshot = requireNotNull(seeded.sourceSnapshot)
        assertEquals("42", snapshot.amount)
        assertEquals(dateTime, snapshot.date)
        assertEquals("$", snapshot.currencySymbol)
    }

    @Test
    fun `transfer resolves target account and derives rate from amounts`() {
        val transfer = TransactionRepository.Transaction.Transfer(
            id = Id.Known("t"),
            amount = Amount(BigDecimal("100")),
            accountId = wallet.id,
            currencyId = usd.id,
            dateTime = dateTime,
            updatedDateTime = dateTime,
            targetAccount = revolut.id,
            targetAmount = Amount(BigDecimal("86")),
        )

        val seeded = loader.seed(state, transfer, isDuplicate = false)

        assertEquals(TransactionEditType.TRANSFER, seeded.transactionType)
        assertEquals(revolut, seeded.selectedTargetAccount)
        assertEquals("100", seeded.amount)
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
            dateTime = dateTime,
            updatedDateTime = dateTime,
            targetAccount = revolut.id,
            targetAmount = Amount(BigDecimal.ZERO),
        )

        val seeded = loader.seed(state, transfer, isDuplicate = false)

        assertEquals("1", seeded.rate)
    }
}
