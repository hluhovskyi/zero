package com.hluhovskyi.zero.backup

interface BackupScheduler {
    fun enable(wifiOnly: Boolean)
    fun disable()

    object Noop : BackupScheduler {
        override fun enable(wifiOnly: Boolean) = Unit
        override fun disable() = Unit
    }
}
