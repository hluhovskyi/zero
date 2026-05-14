package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultFeedbackViewModel(
    private val feedbackService: FeedbackService,
    private val breadcrumbs: Breadcrumbs,
    private val reportFormatter: FeedbackReportFormatter,
    private val onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler,
    private val errorMessageProvider: () -> String,
    deviceInfo: DeviceInfo,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : FeedbackViewModel {

    private val mutableState = MutableStateFlow(
        FeedbackViewModel.State(deviceInfoPreview = formatPreview(deviceInfo)),
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
        val previous = mutableState.getAndUpdate { current ->
            if (current.description.isBlank() || current.isSubmitting) {
                current
            } else {
                current.copy(isSubmitting = true, errorMessage = null)
            }
        }
        if (previous.description.isBlank() || previous.isSubmitting) return
        coroutineScope.launch {
            val report = reportFormatter.format(previous.description, breadcrumbs.snapshot())
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

private fun formatPreview(deviceInfo: DeviceInfo): String =
    "${deviceInfo.manufacturer} ${deviceInfo.model} · Android ${deviceInfo.osVersion} (${deviceInfo.sdkInt}) · ${deviceInfo.versionName} (${deviceInfo.versionCode})"
