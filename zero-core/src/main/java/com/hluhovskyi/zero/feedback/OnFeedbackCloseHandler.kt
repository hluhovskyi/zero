package com.hluhovskyi.zero.feedback

fun interface OnFeedbackCloseHandler {

    fun onFeedbackClose()

    companion object {
        val Noop: OnFeedbackCloseHandler = OnFeedbackCloseHandler { }
    }
}
