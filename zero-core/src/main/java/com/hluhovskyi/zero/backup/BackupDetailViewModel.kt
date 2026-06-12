package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface BackupDetailViewModel : AttachableActionStateModel<BackupDetailViewModel.Action, BackupDetailViewModel.State> {

    sealed interface Action {
        object Connect : Action
        object BackupNow : Action
        object Restore : Action
        object Disconnect : Action
        object DisconnectDismiss : Action
        data class DisconnectConfirmed(val deleteRemote: Boolean) : Action
        object Back : Action
        object SignInFeedbackShown : Action
        object DisconnectFeedbackShown : Action
        data class SetWifiOnly(val wifiOnly: Boolean) : Action
    }

    data class State(
        val isSignedIn: Boolean = false,
        val accountLabel: String? = null,
        val phase: BackupUseCase.Phase = BackupUseCase.Phase.Idle,
        val lastSuccessAgo: TimeAgo? = null,
        val lastError: BackupError? = null,
        val signInFeedback: BackupConnectionUseCase.SignInFeedback? = null,
        val disconnectFeedback: BackupConnectionUseCase.DisconnectFeedback? = null,
        val confirmDialog: ConfirmDialog? = null,
        val wifiOnly: Boolean = true,
    )

    /** Pure-UI state for the disconnect confirm dialog; the connection lifecycle itself lives in [BackupUseCase]. */
    sealed interface ConfirmDialog {
        object Disconnect : ConfirmDialog
    }

    object Noop : BackupDetailViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
