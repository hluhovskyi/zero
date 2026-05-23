package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEnvelopeSerializerTest {

    private val serializer = BackupEnvelopeSerializer()

    private val emptySnapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-05-21T10:00:00"),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
        budgets = emptyList(),
    )

    @Test
    fun `round trips empty snapshot`() {
        val envelope = BackupEnvelope(format = 1, snapshot = emptySnapshot)
        val json = serializer.serialize(envelope)
        assertEquals(envelope, serializer.deserialize(json))
    }

    @Test
    fun `rejects unknown format with descriptive error`() {
        val v1Json = serializer.serialize(BackupEnvelope(format = 1, snapshot = emptySnapshot))
        val futureJson = v1Json.replaceFirst("\"format\":1", "\"format\":99")
        val ex = assertThrows(IllegalStateException::class.java) {
            serializer.deserialize(futureJson)
        }
        assertTrue("Error must mention the unsupported version, got: ${ex.message}", ex.message!!.contains("99"))
        assertTrue("Error must direct user to update, got: ${ex.message}", ex.message!!.contains("update"))
    }

    @Test
    fun `deserializes v1 fixture without error`() {
        val json = javaClass.classLoader!!
            .getResource("fixtures/backup/v1-envelope.json")!!
            .readText()
        val envelope = serializer.deserialize(json)
        assertEquals(1, envelope.format)
        assertEquals(1, envelope.snapshot.version)
    }
}
