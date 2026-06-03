package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime

/**
 * Pure mappings for the transaction edit flow. Plain functions (not injected collaborators): one
 * implementation each, no I/O of their own, the DB seam is already `TransactionRepository`. The
 * three together are the whole reactive load/edit/save pipeline, and all are unit-tested directly:
 *
 *  - [applyLoaded] folds a stored transaction into the draft — by id + scalar, needing no lists, so
 *    it can never race the reference collectors.
 *  - [resolve] turns the draft into the read model against whatever reference lists exist *now*;
 *    empty lists yield null selections rather than blocking, so there is no load gate.
 *  - [buildTransaction] turns the resolved state back into a row to persist.
 */

/**
 * Resolves [draft] against the latest reference lists. Selection is `id → object` lookup with a
 * sensible default, recomputed on every call, so as lists stream in the selection sharpens on its
 * own — no waiting, no pending bookkeeping.
 */
internal fun resolve(
    draft: TransactionEditDraft,
    accounts: List<TransactionEditAccount>,
    categories: List<TransactionEditCategory>,
    currencies: List<TransactionEditCurrency>,
): TransactionEditState {
    val selectedAccount = accounts.firstOrNull { it.id == draft.accountId } ?: accounts.firstOrNull()
    val selectedTargetAccount = accounts.firstOrNull { it.id == draft.targetAccountId } ?: accounts.firstOrNull()

    val allCurrencies = draft.pickedCurrency
        ?.takeIf { picked -> currencies.none { it.id == picked.id } }
        ?.let { currencies + it }
        ?: currencies
    // An explicit currency (loaded or picked) wins; otherwise follow the account's currency.
    val selectedCurrency = draft.currencyId?.let { id -> allCurrencies.firstOrNull { it.id == id } }
        ?: selectedAccount?.let { account -> allCurrencies.firstOrNull { it.id == account.currencyId } }
        ?: allCurrencies.firstOrNull()

    val categoryType = draft.transactionType.categoryType()
    val selectedCategory = categories.firstOrNull { it.id == draft.categoryId }
        ?.takeIf { categoryType == null || it.type == categoryType }
        ?: categories.firstOrNull { categoryType != null && it.type == categoryType }
    // A loaded / pre-selected category is pinned to the front; a tap leaves the ranked order alone.
    val orderedCategories = if (draft.pinSelectedCategory && selectedCategory != null) {
        listOf(selectedCategory) + categories.filter { it.id != selectedCategory.id }
    } else {
        categories
    }

    val snapshot = draft.sourceSnapshot?.let { intent ->
        TransactionEditUseCase.SourceSnapshot(
            amount = intent.amount,
            date = intent.date,
            currencySymbol = allCurrencies.firstOrNull { it.id == intent.currencyId }?.currencySymbol.orEmpty(),
        )
    }

    return TransactionEditState(
        transactionType = draft.transactionType,
        accounts = accounts,
        selectedAccount = selectedAccount,
        targetAccounts = accounts,
        selectedTargetAccount = selectedTargetAccount,
        allCategories = orderedCategories,
        selectedCategory = selectedCategory,
        categoryChosenByUser = draft.categoryChosenByUser,
        currencies = allCurrencies,
        selectedCurrency = selectedCurrency,
        localDateTime = draft.localDateTime,
        amount = draft.amount,
        rate = draft.rate,
        rateAuto = draft.rateAuto,
        targetAmount = draft.targetAmount,
        notes = draft.notes,
        sourceSnapshot = snapshot,
        isModified = draft.isModified,
    )
}

/**
 * Folds a stored [transaction] into [draft]: ids + scalars only. The loaded currency id makes
 * [resolve] keep that currency (it differs from the account's only on an FX transaction), and the
 * rate is fixed (`rateAuto = false`). [isDuplicate] keeps a source snapshot for the header.
 */
internal fun applyLoaded(
    draft: TransactionEditDraft,
    transaction: TransactionRepository.Transaction,
    isDuplicate: Boolean,
): TransactionEditDraft {
    val amount = transaction.amount.value.toString()
    val base = draft.copy(
        accountId = transaction.accountId,
        currencyId = transaction.currencyId,
        amount = amount,
        localDateTime = transaction.dateTime,
        notes = transaction.notes.orEmpty(),
        rateAuto = false,
        sourceSnapshot = if (isDuplicate) {
            TransactionEditDraft.SnapshotIntent(amount, transaction.dateTime, transaction.currencyId)
        } else {
            null
        },
    )

    return when (transaction) {
        is TransactionRepository.Transaction.Expense -> base.copy(
            transactionType = TransactionEditType.EXPENSE,
            categoryId = transaction.categoryId,
            pinSelectedCategory = true,
            rate = transaction.rate.value.toString(),
        )

        is TransactionRepository.Transaction.Income -> base.copy(
            transactionType = TransactionEditType.INCOME,
            categoryId = transaction.categoryId,
            pinSelectedCategory = true,
            rate = transaction.rate.value.toString(),
        )

        is TransactionRepository.Transaction.Transfer -> {
            val toAmount = transaction.targetAmount.value.toString()
            base.copy(
                transactionType = TransactionEditType.TRANSFER,
                targetAccountId = transaction.targetAccount,
                targetAmount = toAmount,
                rate = rateFromAmounts(amount, toAmount) ?: "1",
            )
        }
    }
}

/**
 * Builds the transaction to persist from [state], or null if a required field (account / category /
 * target account) is missing. [now] is the update timestamp and the fallback when no date was picked.
 */
internal fun buildTransaction(
    state: TransactionEditState,
    id: Id.Known,
    now: LocalDateTime,
): TransactionRepository.Transaction? {
    val account = state.selectedAccount ?: return null
    val dateTime = state.localDateTime ?: now

    return when (state.transactionType) {
        TransactionEditType.EXPENSE -> {
            val category = state.selectedCategory ?: return null
            TransactionRepository.Transaction.Expense(
                id = id,
                amount = Amount(state.amount.toBigDecimalOrNull()),
                accountId = account.id,
                currencyId = state.selectedCurrency?.id ?: account.currencyId,
                categoryId = category.id,
                dateTime = dateTime,
                updatedDateTime = now,
                rate = Rate(state.rate.toBigDecimalOrNull()),
                notes = state.notes.ifBlank { null },
            )
        }

        TransactionEditType.INCOME -> {
            val category = state.selectedCategory ?: return null
            TransactionRepository.Transaction.Income(
                id = id,
                amount = Amount(state.amount.toBigDecimalOrNull()),
                accountId = account.id,
                currencyId = state.selectedCurrency?.id ?: account.currencyId,
                categoryId = category.id,
                dateTime = dateTime,
                updatedDateTime = now,
                rate = Rate(state.rate.toBigDecimalOrNull()),
                notes = state.notes.ifBlank { null },
            )
        }

        TransactionEditType.TRANSFER -> {
            val targetAccount = state.selectedTargetAccount ?: return null
            TransactionRepository.Transaction.Transfer(
                id = id,
                amount = Amount(state.amount.toBigDecimalOrNull()),
                accountId = account.id,
                currencyId = account.currencyId,
                targetAccount = targetAccount.id,
                dateTime = dateTime,
                updatedDateTime = now,
                targetAmount = Amount(state.targetAmount.toBigDecimalOrNull()),
                notes = state.notes.ifBlank { null },
            )
        }
    }
}
