package com.hluhovskyi.zero.feedback

data class FeedbackReport(
    val title: String,
    val body: String,
    val type: FeedbackType,
    val isDebug: Boolean = false,
)
