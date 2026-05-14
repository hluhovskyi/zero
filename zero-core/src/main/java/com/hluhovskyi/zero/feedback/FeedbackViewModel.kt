package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.AttachableActionStateModel

interface FeedbackViewModel : AttachableActionStateModel<FeedbackViewModel.Action, FeedbackViewModel.State> {

    sealed interface Action {
        data class UpdateDescription(val text: String) : Action
        object Submit : Action
    }

    data class State(
        val description: String = "",
        val isSubmitting: Boolean = false,
        val deviceInfoPreview: String = "",
        val errorMessage: String? = null,
    )
}
