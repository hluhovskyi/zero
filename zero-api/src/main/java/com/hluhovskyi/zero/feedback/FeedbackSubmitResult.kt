package com.hluhovskyi.zero.feedback

sealed interface FeedbackSubmitResult {

    data class Success(val issueUrl: String) : FeedbackSubmitResult

    data class Failure(val reason: Reason) : FeedbackSubmitResult {

        sealed interface Reason {
            // Endpoint absent from this build (no env / local.gradle.properties value).
            object NotConfigured : Reason

            // Play Integrity could not vouch for this install (null token).
            object Unverified : Reason

            // Server was reached but rejected the report.
            data class Server(val code: Int) : Reason

            // Transport failure before a response was received.
            object Network : Reason
        }
    }
}
