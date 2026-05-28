package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDateTime
import java.io.Closeable

interface BackupDetailViewModel : AttachableActionStateModel<BackupDetailViewModel.Action, BackupDetailViewModel.State> {

    sealed interface Action {
        object Connect : Action
        object BackupNow : Action
        object Restore : Action
        object Disconnect : Action
        object Back : Action
        object SignInFeedbackShown : Action
    }

    data class State(
        val isSignedIn: Boolean = false,
        val accountLabel: String? = null,
        val phase: BackupUseCase.Phase = BackupUseCase.Phase.Idle,
        val lastSuccessAt: LocalDateTime? = null,
        val lastError: BackupError? = null,
        val signInFeedback: SignInFeedback? = null,
    )

    sealed interface SignInFeedback {
        object Cancelled : SignInFeedback
        data class Failed(val error: BackupError) : SignInFeedback
    }

    object Noop : BackupDetailViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
