package com.hluhovskyi.zero.transactions.preview

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Image
import kotlinx.datetime.LocalDateTime

interface TransactionPreviewViewModel
    : AttachableActionStateModel<TransactionPreviewViewModel.Action, TransactionPreviewViewModel.State> {

    interface Action {

    }

    data class State(
        val amount: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val categoryName: String = "",
        val categoryIcon: Image = Image.empty(),
        val categoryColor: ColorValue = ColorValue.unspecified(),
        val accountName: String = "",
        val accountIcon: Image = Image.empty(),
        val dateTime: LocalDateTime? = null
    )
}
