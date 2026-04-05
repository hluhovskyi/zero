package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Rate

sealed interface TransferRateMode {

    data class Default(val rate: Rate) : TransferRateMode

    data class CustomRate(val rate: String) : TransferRateMode

    data class CustomAmount(val targetAmount: String) : TransferRateMode
}
