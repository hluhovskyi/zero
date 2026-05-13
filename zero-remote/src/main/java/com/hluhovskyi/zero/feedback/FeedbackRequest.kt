package com.hluhovskyi.zero.feedback

import kotlinx.serialization.Serializable

@Serializable
internal data class FeedbackRequest(
    val title: String,
    val body: String,
    val labels: List<String>,
)
