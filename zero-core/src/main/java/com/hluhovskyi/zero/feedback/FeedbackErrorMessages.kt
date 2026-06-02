package com.hluhovskyi.zero.feedback

fun interface FeedbackErrorMessages {

    fun messageFor(reason: FeedbackSubmitResult.Failure.Reason): String
}
