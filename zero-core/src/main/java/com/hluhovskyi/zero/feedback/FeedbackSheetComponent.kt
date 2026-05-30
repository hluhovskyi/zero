package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import java.io.Closeable

private const val TAG = "FeedbackSheetComponent"

class FeedbackSheetComponent(
    feedbackService: FeedbackService,
    breadcrumbs: Breadcrumbs,
    deviceInfo: DeviceInfo,
    clock: Clock,
    isDebugBuild: Boolean,
    errorMessages: FeedbackErrorMessages,
    onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler,
    onFeedbackCloseHandler: OnFeedbackCloseHandler,
) : AttachableViewComponent {

    override val tag: String = TAG

    private val viewModel: FeedbackViewModel by lazy {
        DefaultFeedbackViewModel(
            feedbackService = feedbackService,
            breadcrumbs = breadcrumbs,
            reportFormatter = FeedbackReportFormatter(
                deviceInfo = deviceInfo,
                isDebugBuild = isDebugBuild,
                clock = clock,
            ),
            onFeedbackSubmittedHandler = onFeedbackSubmittedHandler,
            onFeedbackCloseHandler = onFeedbackCloseHandler,
            errorMessages = errorMessages,
            deviceInfo = deviceInfo,
        )
    }

    override val viewProvider: ViewProvider by lazy {
        FeedbackViewProvider(viewModel = viewModel)
    }

    override fun attach(): Closeable = viewModel.attach()
}
