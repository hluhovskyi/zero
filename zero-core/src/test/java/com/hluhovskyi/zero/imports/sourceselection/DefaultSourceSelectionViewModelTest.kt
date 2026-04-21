package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.imports.ImportUseCase
import com.hluhovskyi.zero.imports.OnImportFinishedHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSourceSelectionViewModelTest {

    private val mutableUseCaseState = MutableStateFlow<ImportUseCase.State>(
        ImportUseCase.State.SourceSelection(sources = emptyList()),
    )
    private val capturedActions = mutableListOf<ImportUseCase.Action>()

    private val fakeUseCase = object : ImportUseCase {
        override val state: Flow<ImportUseCase.State> = mutableUseCaseState
        override fun perform(action: ImportUseCase.Action) {
            capturedActions.add(action)
        }
        override fun attach(): Closeable = Closeables.empty()
    }

    private val viewModel = DefaultSourceSelectionViewModel(
        importUseCase = fakeUseCase,
        onImportFinishedHandler = OnImportFinishedHandler.Noop,
    )

    @Test
    fun `state maps error from use case SourceSelection state`() = runTest {
        mutableUseCaseState.value = ImportUseCase.State.SourceSelection(
            sources = emptyList(),
            error = "Parse failed",
        )

        val state = viewModel.state.first()
        assertEquals("Parse failed", state.error)
    }

    @Test
    fun `state maps null error when use case has no error`() = runTest {
        mutableUseCaseState.value = ImportUseCase.State.SourceSelection(
            sources = emptyList(),
            error = null,
        )

        val state = viewModel.state.first()
        assertNull(state.error)
    }

    @Test
    fun `DismissError action forwards to ImportUseCase`() = runTest {
        viewModel.perform(SourceSelectionViewModel.Action.DismissError)

        assert(capturedActions.contains(ImportUseCase.Action.DismissError)) {
            "Expected DismissError in captured actions: $capturedActions"
        }
    }

    @Test
    fun `Retry action forwards to ImportUseCase`() = runTest {
        viewModel.perform(SourceSelectionViewModel.Action.Retry)

        assert(capturedActions.contains(ImportUseCase.Action.Retry)) {
            "Expected Retry in captured actions: $capturedActions"
        }
    }
}
