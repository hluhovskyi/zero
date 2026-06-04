package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface BackupUseCase {
    val state: Flow<State>
    fun perform(action: Action)

    data class State(
        val isSignedIn: Boolean = false,
        val accountLabel: String? = null,
        val phase: Phase = Phase.Idle,
        val lastSuccessAt: LocalDateTime? = null,
        val lastError: BackupError? = null,
        val consecutiveFailures: Int = 0,
        val signInFeedback: SignInFeedback? = null,
        val disconnectFeedback: DisconnectFeedback? = null,
    )

    sealed interface Phase {
        object Idle : Phase
        object Uploading : Phase
        data class Failed(val error: BackupError) : Phase
        object Restoring : Phase
    }

    /** One-shot outcome of [Action.Connect], cleared via [Action.SignInFeedbackShown]. */
    sealed interface SignInFeedback {
        object Cancelled : SignInFeedback
        data class Failed(val error: BackupError) : SignInFeedback
    }

    /** One-shot outcome of [Action.Disconnect], cleared via [Action.DisconnectFeedbackShown]. */
    sealed interface DisconnectFeedback {
        object DeleteFailed : DisconnectFeedback
    }

    sealed interface Action {
        object Connect : Action
        data class Disconnect(val deleteRemote: Boolean) : Action
        object BackupNow : Action
        data class RestoreLatest(val onSnapshot: (SyncSnapshot) -> Unit) : Action
        object SignInFeedbackShown : Action
        object DisconnectFeedbackShown : Action
    }
}
