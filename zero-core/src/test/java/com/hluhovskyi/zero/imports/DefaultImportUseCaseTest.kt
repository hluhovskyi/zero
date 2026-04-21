package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultImportUseCaseTest {

    @Mock private lateinit var parser: SnapshotParser

    @Mock private lateinit var syncEngine: SyncEngine

    @Mock private lateinit var currentUserRepository: CurrentUserRepository

    private val source = KnownSource.ZeroBackup
    private val userId = Id.Known("user-1")
    private val testUri = Uri("file://test.zero") as Uri.NonEmpty

    @Before
    fun setUp() {
        whenever(parser.source).thenReturn(source)
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
    }

    private fun createUseCase(scope: CoroutineScope) = DefaultImportUseCase(
        parsers = listOf(parser),
        syncEngine = syncEngine,
        currentUserRepository = currentUserRepository,
        onImportFinishedHandler = OnImportFinishedHandler.Noop,
        coroutineScope = scope,
    )

    @Test
    fun `SelectFile sets error state when parser throws`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNotNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `DismissError clears the error`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.DismissError)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `Retry transitions to FilePicker`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.Retry)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.FilePicker) { "Expected FilePicker but got $state" }
    }
}
