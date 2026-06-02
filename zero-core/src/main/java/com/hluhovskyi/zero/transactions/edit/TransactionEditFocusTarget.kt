package com.hluhovskyi.zero.transactions.edit

/** Which field the inline amount keypad currently drives. `Received` is transfer-only. */
enum class TransactionEditFocusTarget {
    Amount,
    Rate,
    Received,
}
