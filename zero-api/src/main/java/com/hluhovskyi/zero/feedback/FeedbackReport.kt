package com.hluhovskyi.zero.feedback

data class FeedbackReport(
    val title: String,
    val body: String,
    val labels: List<String> = emptyList(),
)
