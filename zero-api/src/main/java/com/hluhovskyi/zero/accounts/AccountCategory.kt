package com.hluhovskyi.zero.accounts

enum class AccountCategory {
    CASH,
    BANK,
    CREDIT_CARDS,
    DIGITAL_WALLETS,
    CRYPTO,
    OTHER,
    ;

    companion object {
        fun from(value: String): AccountCategory = entries.find { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}
