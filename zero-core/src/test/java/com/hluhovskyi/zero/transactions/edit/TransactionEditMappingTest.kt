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
import org.junit.Assert.assertTrue
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

    private val accounts = listOf(wallet, revolut)
    private val currencies = listOf(usd, eur)
    private val categories = listOf(category("a"), category("b"), category("c"))

    private fun expense(currencyId: Id.Known = eur.id, categoryId: String = "c", rate: Rate = Rate(BigDecimal("0.9"))) = TransactionRepository.Transaction.Expense(
        id = Id.Known("t"),
        amount = Amount(BigDecimal("42")),
        accountId = wallet.id,
        currencyId = currencyId,
        dateTime = now,
        updatedDateTime = now,
        categoryId = Id.Known(categoryId),
        rate = rate,
        notes = "lunch",
    )

    private fun transfer(amount: String = "100", target: String = "86") = TransactionRepository.Transaction.Transfer(
        id = Id.Known("t"),
        amount = Amount(BigDecimal(amount)),
        accountId = wallet.id,
        currencyId = usd.id,
        dateTime = now,
        updatedDateTime = now,
        targetAccount = revolut.id,
        targetAmount = Amount(BigDecimal(target)),
    )

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

    // ── applyLoaded ────────────────────────────────────────────────────────────────────────

    @Test
    fun `applyLoaded folds an expense into the draft by id, fixes rate`() {
        val draft = applyLoaded(TransactionEditDraft(), expense(), isDuplicate = false)

        assertEquals(TransactionEditType.EXPENSE, draft.transactionType)
        assertEquals("42", draft.amount)
        assertEquals(wallet.id, draft.accountId)
        assertEquals(eur.id, draft.currencyId)
        assertFalse(draft.manuallyChangedCurrency)
        assertEquals(Id.Known("c"), draft.categoryId)
        assertTrue(draft.pinSelectedCategory)
        assertEquals("0.9", draft.rate)
        assertFalse(draft.rateAuto)
        assertEquals("lunch", draft.notes)
        assertNull(draft.sourceSnapshot)
    }

    @Test
    fun `applyLoaded derives the transfer rate from amounts, falls back to 1 at zero source`() {
        assertEquals("0.86", applyLoaded(TransactionEditDraft(), transfer(), isDuplicate = false).rate)
        assertEquals("1", applyLoaded(TransactionEditDraft(), transfer(amount = "0", target = "0"), isDuplicate = false).rate)
    }

    // ── resolve ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolve maps a loaded expense draft to selections, pins category to front`() {
        val draft = applyLoaded(TransactionEditDraft(), expense(), isDuplicate = false)

        val state = resolve(draft, accounts, categories, currencies)

        assertEquals(wallet, state.selectedAccount)
        assertEquals(eur, state.selectedCurrency)
        assertEquals(Id.Known("c"), state.selectedCategory?.id)
        assertEquals(listOf("c", "a", "b"), state.allCategories.map { it.id.value })
        assertEquals("0.9", state.rate)
        assertFalse(state.rateAuto)
        assertNull(state.sourceSnapshot)
    }

    @Test
    fun `resolve defaults selection for a fresh draft`() {
        val state = resolve(TransactionEditDraft(), accounts, categories, currencies)

        assertEquals(wallet, state.selectedAccount)
        assertEquals(usd, state.selectedCurrency)
        assertEquals(Id.Known("a"), state.selectedCategory?.id)
        assertEquals(listOf("a", "b", "c"), state.allCategories.map { it.id.value })
    }

    @Test
    fun `resolve survives empty lists, then resolves once they arrive — no gate`() {
        val draft = applyLoaded(TransactionEditDraft(), expense(), isDuplicate = false)

        val empty = resolve(draft, emptyList(), emptyList(), emptyList())
        assertNull(empty.selectedAccount)
        assertNull(empty.selectedCurrency)
        assertNull(empty.selectedCategory)

        val full = resolve(draft, accounts, categories, currencies)
        assertEquals(wallet, full.selectedAccount)
        assertEquals(Id.Known("c"), full.selectedCategory?.id)
    }

    @Test
    fun `resolve builds the duplicate snapshot with the source currency symbol`() {
        val draft = applyLoaded(TransactionEditDraft(), expense(currencyId = usd.id, categoryId = "a", rate = Rate.Same), isDuplicate = true)

        val snapshot = requireNotNull(resolve(draft, accounts, categories, currencies).sourceSnapshot)

        assertEquals("42", snapshot.amount)
        assertEquals(now, snapshot.date)
        assertEquals("$", snapshot.currencySymbol)
    }

    @Test
    fun `resolve maps a loaded transfer draft to its target account`() {
        val draft = applyLoaded(TransactionEditDraft(), transfer(), isDuplicate = false)

        val state = resolve(draft, accounts, categories, currencies)

        assertEquals(TransactionEditType.TRANSFER, state.transactionType)
        assertEquals(revolut, state.selectedTargetAccount)
        assertEquals("86", state.targetAmount)
        assertEquals("0.86", state.rate)
    }

    @Test
    fun `resolve injects a picked currency that is outside the in-use list`() {
        val gbp = TransactionEditCurrency(Id.Known("GBP"), "Pound", "£")
        val draft = TransactionEditDraft(manuallyChangedCurrency = true, currencyId = gbp.id, pickedCurrency = gbp)

        val state = resolve(draft, accounts, categories, currencies)

        assertEquals(gbp, state.selectedCurrency)
        assertTrue(state.currencies.contains(gbp))
    }
}
