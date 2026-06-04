package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface BackupUseCase {
    val state: Flow<State>
    fun perform(action: Action)

    data class State(
        val phase: Phase = Phase.Idle,
        val lastSuccessAt: LocalDateTime? = null,
        val lastError: BackupError? = null,
        val consecutiveFailures: Int = 0,
    )

    sealed interface Phase {
        object Idle : Phase
        object Uploading : Phase
        data class Failed(val error: BackupError) : Phase
        object Restoring : Phase
    }

    sealed interface Action {
        object BackupNow : Action
        data class RestoreLatest(val onSnapshot: (SyncSnapshot) -> Unit) : Action
    }
}
