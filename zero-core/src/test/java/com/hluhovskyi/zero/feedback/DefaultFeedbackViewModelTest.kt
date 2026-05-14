package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultFeedbackViewModelTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2024-06-01T12:00:00Z")
    }
    private val deviceInfo = DeviceInfo(
        manufacturer = "Google",
        model = "Pixel 8",
        osVersion = "14",
        sdkInt = 34,
        versionName = "1.4.2",
        versionCode = 142L,
    )
    private val emptyBreadcrumbs = object : Breadcrumbs {
        override fun log(message: String) = Unit
        override fun snapshot(): Breadcrumbs.Snapshot = Breadcrumbs.Snapshot(navigation = emptyList(), breadcrumbs = emptyList())
    }
    private val formatter = FeedbackReportFormatter(
        deviceInfo = deviceInfo,
        isDebugBuild = false,
        clock = clock,
    )

    private class ScriptedFeedbackService(private val result: FeedbackSubmitResult) : FeedbackService {
        var callCount: Int = 0
            private set
        override suspend fun submit(report: FeedbackReport): FeedbackSubmitResult {
            callCount++
            return result
        }
    }

    private class RecordingSubmittedHandler : OnFeedbackSubmittedHandler {
        var callCount: Int = 0
            private set
        override fun onFeedbackSubmitted() {
            callCount++
        }
    }

    private fun newViewModel(
        service: FeedbackService,
        handler: OnFeedbackSubmittedHandler,
        scope: CoroutineScope,
    ): DefaultFeedbackViewModel = DefaultFeedbackViewModel(
        feedbackService = service,
        breadcrumbs = emptyBreadcrumbs,
        reportFormatter = formatter,
        onFeedbackSubmittedHandler = handler,
        errorMessageProvider = { "error" },
        deviceInfo = deviceInfo,
        coroutineScope = scope,
    )

    @Test
    fun `UpdateDescription updates state and clears prior error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = ScriptedFeedbackService(FeedbackSubmitResult.Failure)
        val handler = RecordingSubmittedHandler()
        val viewModel = newViewModel(service, handler, CoroutineScope(dispatcher))

        viewModel.perform(FeedbackViewModel.Action.UpdateDescription("first"))
        viewModel.perform(FeedbackViewModel.Action.Submit)
        advanceUntilIdle()
        viewModel.perform(FeedbackViewModel.Action.UpdateDescription("second"))

        val state = viewModel.state.first()
        assertEquals("second", state.description)
        assertNull(state.errorMessage)
    }

    @Test
    fun `Submit while description blank does not call feedbackService`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = ScriptedFeedbackService(FeedbackSubmitResult.Success("url"))
        val handler = RecordingSubmittedHandler()
        val viewModel = newViewModel(service, handler, CoroutineScope(dispatcher))

        viewModel.perform(FeedbackViewModel.Action.Submit)
        advanceUntilIdle()

        assertEquals(0, service.callCount)
        assertEquals(0, handler.callCount)
    }

    @Test
    fun `Success invokes handler and clears submitting`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = ScriptedFeedbackService(FeedbackSubmitResult.Success("url"))
        val handler = RecordingSubmittedHandler()
        val viewModel = newViewModel(service, handler, CoroutineScope(dispatcher))

        viewModel.perform(FeedbackViewModel.Action.UpdateDescription("description"))
        viewModel.perform(FeedbackViewModel.Action.Submit)
        advanceUntilIdle()

        assertEquals(1, service.callCount)
        assertEquals(1, handler.callCount)
        val state = viewModel.state.first()
        assertFalse(state.isSubmitting)
        assertNull(state.errorMessage)
    }

    @Test
    fun `Failure sets error message preserves description and does not invoke handler`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = ScriptedFeedbackService(FeedbackSubmitResult.Failure)
        val handler = RecordingSubmittedHandler()
        val viewModel = newViewModel(service, handler, CoroutineScope(dispatcher))

        viewModel.perform(FeedbackViewModel.Action.UpdateDescription("typed text"))
        viewModel.perform(FeedbackViewModel.Action.Submit)
        advanceUntilIdle()

        assertEquals(1, service.callCount)
        assertEquals(0, handler.callCount)
        val state = viewModel.state.first()
        assertFalse(state.isSubmitting)
        assertEquals("typed text", state.description)
        assertNotNull(state.errorMessage)
    }
}
