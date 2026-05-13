package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultFeedbackViewModel(
    private val feedbackService: FeedbackService,
    private val breadcrumbs: Breadcrumbs,
    private val reportFormatter: FeedbackReportFormatter,
    private val onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler,
    private val errorMessageProvider: () -> String,
    deviceInfoPreview: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : FeedbackViewModel {

    private val mutableState = MutableStateFlow(
        FeedbackViewModel.State(deviceInfoPreview = deviceInfoPreview),
    )
    override val state: Flow<FeedbackViewModel.State> = mutableState

    override fun perform(action: FeedbackViewModel.Action) {
        when (action) {
            is FeedbackViewModel.Action.UpdateDescription -> mutableState.update {
                it.copy(description = action.text, errorMessage = null)
            }
            FeedbackViewModel.Action.Submit -> submit()
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.description.isBlank() || current.isSubmitting) return
        mutableState.update { it.copy(isSubmitting = true, errorMessage = null) }
        coroutineScope.launch {
            val report = reportFormatter.format(current.description, breadcrumbs.snapshot())
            when (feedbackService.submit(report)) {
                is FeedbackSubmitResult.Success -> {
                    mutableState.update { it.copy(isSubmitting = false) }
                    onFeedbackSubmittedHandler.onFeedbackSubmitted()
                }
                FeedbackSubmitResult.Failure -> {
                    mutableState.update {
                        it.copy(isSubmitting = false, errorMessage = errorMessageProvider())
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.from { coroutineScope.cancel() }
}
