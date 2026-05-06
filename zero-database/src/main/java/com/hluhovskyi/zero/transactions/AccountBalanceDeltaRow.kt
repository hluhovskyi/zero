package com.hluhovskyi.zero.transactions

import java.math.BigDecimal

internal data class AccountBalanceDeltaRow(
    val accountId: String,
    val value: BigDecimal,
)
