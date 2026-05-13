package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.SyncTransaction
import com.hluhovskyi.zero.transactions.TransactionRepository

/**
 * Content-based signature for a transaction. Used by the import flow to dedupe
 * incoming transactions against the local DB without relying on ids that sources
 * (like ZenMoney) regenerate on every export.
 *
 * Amounts are normalized via [java.math.BigDecimal.stripTrailingZeros] + `toPlainString`,
 * so `10` and `10.00` hash to the same string.
 */
internal data class TransactionSignature(
    val type: SyncTransaction.Type,
    val accountId: String,
    val currencyId: String,
    val amount: String,
    val dateTime: String,
    val categoryId: String?,
    val targetAccountId: String?,
    val targetAmount: String?,
)

/**
 * The signature this transaction *would* have if the given remaps were applied first.
 * Each remap maps an imported sync-id to a local DB id.
 */
internal fun SyncTransaction.signatureAfterRemap(
    categoryRemap: Map<Id.Known, Id.Known>,
    accountRemap: Map<Id.Known, Id.Known>,
): TransactionSignature = TransactionSignature(
    type = type,
    accountId = (accountRemap[accountId]?.value ?: accountId.value),
    currencyId = currencyId.value,
    amount = amount.normalizedDecimal(),
    dateTime = enteredDateTime.toString(),
    categoryId = categoryId?.let { categoryRemap[Id.Known(it)]?.value ?: it },
    targetAccountId = targetAccountId?.let { accountRemap[Id.Known(it)]?.value ?: it },
    targetAmount = targetAmount?.normalizedDecimal(),
)

internal fun SyncTransaction.toSignature(): TransactionSignature = TransactionSignature(
    type = type,
    accountId = accountId.value,
    currencyId = currencyId.value,
    amount = amount.normalizedDecimal(),
    dateTime = enteredDateTime.toString(),
    categoryId = categoryId,
    targetAccountId = targetAccountId,
    targetAmount = targetAmount?.normalizedDecimal(),
)

internal fun TransactionRepository.Transaction.toSignature(): TransactionSignature {
    val baseAmount = amount.value.stripTrailingZeros().toPlainString()
    val dateStr = dateTime.toString()
    return when (this) {
        is TransactionRepository.Transaction.Expense -> TransactionSignature(
            type = SyncTransaction.Type.EXPENSE,
            accountId = accountId.value,
            currencyId = currencyId.value,
            amount = baseAmount,
            dateTime = dateStr,
            categoryId = categoryId.value,
            targetAccountId = null,
            targetAmount = null,
        )
        is TransactionRepository.Transaction.Income -> TransactionSignature(
            type = SyncTransaction.Type.INCOME,
            accountId = accountId.value,
            currencyId = currencyId.value,
            amount = baseAmount,
            dateTime = dateStr,
            categoryId = categoryId.value,
            targetAccountId = null,
            targetAmount = null,
        )
        is TransactionRepository.Transaction.Transfer -> TransactionSignature(
            type = SyncTransaction.Type.TRANSFER,
            accountId = accountId.value,
            currencyId = currencyId.value,
            amount = baseAmount,
            dateTime = dateStr,
            categoryId = null,
            targetAccountId = targetAccount.value,
            targetAmount = targetAmount.value.stripTrailingZeros().toPlainString(),
        )
    }
}

private fun String.normalizedDecimal(): String =
    toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty()
