package com.hluhovskyi.zero.feedback

sealed interface FeedbackSubmitResult {

    data class Success(val issueUrl: String) : FeedbackSubmitResult

    object Failure : FeedbackSubmitResult
}
