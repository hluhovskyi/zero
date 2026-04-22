package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DefaultExportUseCaseTest {

    @Mock private lateinit var currentUserRepository: CurrentUserRepository

    @Mock private lateinit var syncEngine: SyncEngine

    @Mock private lateinit var exportWriter: ExportWriter

    private val serializer = SyncSerializer()

    private val userId = Id.Known("user-1")
    private val testUri = Uri("content://test/backup.json") as Uri.NonEmpty
    private val snapshot = SyncSnapshot(
        version = 1,
        userId = userId,
        exportedAt = LocalDateTime(2026, 4, 22, 0, 0),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
    )

    private lateinit var useCase: DefaultExportUseCase

    @Before
    fun setUp() {
        useCase = DefaultExportUseCase(
            currentUserRepository = currentUserRepository,
            syncEngine = syncEngine,
            serializer = serializer,
            exportWriter = exportWriter,
        )
    }

    @Test
    fun `export returns Success when all steps succeed`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
        whenever(syncEngine.export(userId)).thenReturn(snapshot)

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Success, result)
        verify(exportWriter).write(testUri, serializer.serialize(snapshot))
    }

    @Test
    fun `export returns Failure when repository emits error`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(
            flow { throw RuntimeException("db error") },
        )

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Failure("db error"), result)
    }

    @Test
    fun `export returns Failure when sync engine throws`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
        whenever(syncEngine.export(userId)).thenThrow(RuntimeException("sync failed"))

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Failure("sync failed"), result)
    }

    @Test
    fun `export returns Failure with unknown error when exception has no message`() = runTest {
        whenever(currentUserRepository.query()).thenReturn(
            flow { throw RuntimeException() },
        )

        val result = useCase.export(testUri)

        assertEquals(ExportUseCase.Result.Failure("Unknown error"), result)
    }
}
