package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.first

internal class DefaultExportUseCase(
    private val currentUserRepository: CurrentUserRepository,
    private val syncEngine: SyncEngine,
    private val serializer: SyncSerializer,
    private val exportWriter: ExportWriter,
) : ExportUseCase {

    override suspend fun export(uri: Uri.NonEmpty): ExportUseCase.Result = try {
        val userId = currentUserRepository.query().first().id
        val snapshot = syncEngine.export(userId)
        val json = serializer.serialize(snapshot)
        exportWriter.write(uri, json)
        ExportUseCase.Result.Success
    } catch (e: Exception) {
        ExportUseCase.Result.Failure(e.message ?: "Unknown error")
    }
}
