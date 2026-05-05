package com.hluhovskyi.zero.transactions

import androidx.compose.runtime.Composable

fun interface TransactionHeader {
    @Composable
    fun Content()

    companion object {
        val None: TransactionHeader = TransactionHeader {}
    }
}
