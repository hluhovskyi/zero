package com.hluhovskyi.zero.imports

sealed class KnownSource(
    override val key: String,
    override val label: String,
    override val description: String,
) : Source {
    object ZeroBackup : KnownSource(
        key = "zero_backup",
        label = "Zero Backup",
        description = "Restore from a Zero backup file",
    )
    object ZenMoney : KnownSource(
        key = "zenmoney",
        label = "ZenMoney",
        description = "Import from a ZenMoney CSV export",
    )
}
