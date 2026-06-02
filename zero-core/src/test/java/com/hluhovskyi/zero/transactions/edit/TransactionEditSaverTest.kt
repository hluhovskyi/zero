package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TransactionEditSaverTest {

    private val nowInstant = Instant.parse("2024-06-05T12:00:00Z")
    private val nowDateTime = LocalDateTime(2024, 6, 5, 12, 0)
    private val pickedDateTime = LocalDateTime(2024, 1, 2, 9, 30)

    private val inserted = mutableListOf<TransactionRepository.Transaction>()
    private val repository = object : TransactionRepository {
        override fun <T> query(criteria: TransactionRepository.Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
        override suspend fun insert(transaction: TransactionRepository.Transaction) {
            inserted += transaction
        }
        override suspend fun insert(transactions: List<TransactionRepository.Transaction>) {
            inserted += transactions
        }
        override suspend fun delete(id: Id.Known) = Unit
    }
    private val generatedId = Id.Known("generated")
    private val idGenerator = object : IdGenerator {
        override fun invoke() = generatedId
    }
    private val clock = object : Clock {
        override fun now() = nowInstant
    }
    private val zoneProvider = object : ZoneProvider {
        override fun timeZone() = TimeZone.UTC
    }

    private val usd = TransactionEditCurrency(Id.Known("USD"), "US Dollar", "$")
    private val eur = TransactionEditCurrency(Id.Known("EUR"), "Euro", "€")
    private val wallet = TransactionEditAccount(Id.Known("acc-usd"), "Wallet", Id.Known("USD"))
    private val revolut = TransactionEditAccount(Id.Known("acc-eur"), "Revolut", Id.Known("EUR"))
    private val food = TransactionEditCategory(Id.Known("cat"), "Food", ColorScheme.Grey, Image.empty())

    private fun saver(transactionId: Id) = DefaultTransactionEditSaver(
        transactionId = transactionId,
        transactionRepository = repository,
        idGenerator = idGenerator,
        clock = clock,
        zoneProvider = zoneProvider,
    )

    @Test
    fun `new expense generates id, falls back to now, uses picked currency`() = runBlocking {
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

        val id = saver(Id.Unknown).save(state)

        assertEquals(generatedId, id)
        val expense = inserted.single() as TransactionRepository.Transaction.Expense
        assertEquals(generatedId, expense.id)
        assertEquals(Amount(BigDecimal("12.50")), expense.amount)
        assertEquals(wallet.id, expense.accountId)
        assertEquals(eur.id, expense.currencyId)
        assertEquals(food.id, expense.categoryId)
        assertEquals(nowDateTime, expense.dateTime)
        assertEquals(nowDateTime, expense.updatedDateTime)
        assertEquals("lunch", expense.notes)
    }

    @Test
    fun `edit reuses existing id and picked date, blank notes become null, currency falls back to account`() = runBlocking {
        val state = TransactionEditState(
            transactionType = TransactionEditType.INCOME,
            selectedAccount = wallet,
            selectedCategory = food,
            selectedCurrency = null,
            amount = "100",
            notes = "   ",
            localDateTime = pickedDateTime,
        )

        val id = saver(Id.Known("existing")).save(state)

        assertEquals(Id.Known("existing"), id)
        val income = inserted.single() as TransactionRepository.Transaction.Income
        assertEquals(Id.Known("existing"), income.id)
        assertEquals(wallet.currencyId, income.currencyId)
        assertEquals(pickedDateTime, income.dateTime)
        assertNull(income.notes)
    }

    @Test
    fun `transfer saves target account, target amount, source-account currency`() = runBlocking {
        val state = TransactionEditState(
            transactionType = TransactionEditType.TRANSFER,
            selectedAccount = wallet,
            selectedTargetAccount = revolut,
            amount = "100",
            targetAmount = "86",
            localDateTime = pickedDateTime,
        )

        saver(Id.Unknown).save(state)

        val transfer = inserted.single() as TransactionRepository.Transaction.Transfer
        assertEquals(wallet.currencyId, transfer.currencyId)
        assertEquals(revolut.id, transfer.targetAccount)
        assertEquals(Amount(BigDecimal("100")), transfer.amount)
        assertEquals(Amount(BigDecimal("86")), transfer.targetAmount)
    }

    @Test
    fun `missing account returns null without inserting`() = runBlocking {
        val id = saver(Id.Unknown).save(TransactionEditState(selectedAccount = null, selectedCategory = food))
        assertNull(id)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `expense without category returns null without inserting`() = runBlocking {
        val id = saver(Id.Unknown).save(
            TransactionEditState(transactionType = TransactionEditType.EXPENSE, selectedAccount = wallet, selectedCategory = null),
        )
        assertNull(id)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `transfer without target account returns null without inserting`() = runBlocking {
        val id = saver(Id.Unknown).save(
            TransactionEditState(transactionType = TransactionEditType.TRANSFER, selectedAccount = wallet, selectedTargetAccount = null),
        )
        assertNull(id)
        assertTrue(inserted.isEmpty())
    }
}
