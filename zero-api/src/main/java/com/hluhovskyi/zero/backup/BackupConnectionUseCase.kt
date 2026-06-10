package com.hluhovskyi.zero.backup

import kotlinx.coroutines.flow.Flow

/**
 * Owns the Google Drive connection lifecycle for backup: sign-in, disconnect (with optional remote
 * delete), and the durable signed-in state. Distinct from [BackupUseCase], which owns the
 * backup-data operations (upload/restore). Both are read by the settings screen.
 */
interface BackupConnectionUseCase {
    val state: Flow<State>
    fun perform(action: Action)

    data class State(
        val isSignedIn: Boolean = false,
        val accountLabel: String? = null,
        val signInFeedback: SignInFeedback? = null,
        val disconnectFeedback: DisconnectFeedback? = null,
    )

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
        object SignInFeedbackShown : Action
        object DisconnectFeedbackShown : Action
    }
}
