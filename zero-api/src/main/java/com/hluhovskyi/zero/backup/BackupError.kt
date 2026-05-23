package com.hluhovskyi.zero.backup

sealed interface BackupError {
    object AuthExpired : BackupError
    object NetworkUnavailable : BackupError
    object QuotaExceeded : BackupError
    object ParseFailure : BackupError
    data class Unknown(val message: String) : BackupError
}
