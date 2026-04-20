package com.hluhovskyi.zero.imports

sealed class KnownSource(
    override val key: String,
) : Source {
    object ZeroBackup : KnownSource(key = "zero_backup")
    object ZenMoney : KnownSource(key = "zenmoney")
}
