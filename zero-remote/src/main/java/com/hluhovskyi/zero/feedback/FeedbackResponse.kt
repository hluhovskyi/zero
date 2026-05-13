package com.hluhovskyi.zero.feedback

import kotlinx.serialization.Serializable

@Serializable
internal data class FeedbackResponse(
    val issueUrl: String,
)
