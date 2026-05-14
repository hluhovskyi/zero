package com.hluhovskyi.zero.feedback

fun interface OnFeedbackSubmittedHandler {

    fun onFeedbackSubmitted()

    companion object {
        val Noop: OnFeedbackSubmittedHandler = OnFeedbackSubmittedHandler { }
    }
}
