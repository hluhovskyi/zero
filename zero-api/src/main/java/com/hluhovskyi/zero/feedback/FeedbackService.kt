package com.hluhovskyi.zero.feedback

interface FeedbackService {

    suspend fun submit(report: FeedbackReport): FeedbackSubmitResult
}
