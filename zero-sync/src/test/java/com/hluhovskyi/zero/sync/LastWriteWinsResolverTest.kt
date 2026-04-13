package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LastWriteWinsResolverTest {

    private val resolver = LastWriteWinsResolver<TestSyncEntity>()

    @Test
    fun `local null returns incoming`() {
        val incoming = entity("a", updatedAt = "2024-01-02T00:00:00")
        assertEquals(listOf(incoming), resolver.resolve(local = null, incoming = incoming))
    }

    @Test
    fun `incoming null returns local`() {
        val local = entity("a", updatedAt = "2024-01-01T00:00:00")
        assertEquals(listOf(local), resolver.resolve(local = local, incoming = null))
    }

    @Test
    fun `incoming newer than local wins`() {
        val local = entity("a", updatedAt = "2024-01-01T00:00:00")
        val incoming = entity("a", updatedAt = "2024-01-02T00:00:00")
        assertEquals(listOf(incoming), resolver.resolve(local, incoming))
    }

    @Test
    fun `local newer than incoming wins`() {
        val local = entity("a", updatedAt = "2024-01-03T00:00:00")
        val incoming = entity("a", updatedAt = "2024-01-02T00:00:00")
        assertEquals(listOf(local), resolver.resolve(local, incoming))
    }

    @Test
    fun `equal timestamps returns local (stable)`() {
        val ts = "2024-01-01T00:00:00"
        val local = entity("a", updatedAt = ts)
        val incoming = entity("a", updatedAt = ts)
        assertEquals(listOf(local), resolver.resolve(local, incoming))
    }

    @Test
    fun `tombstone incoming beats older local`() {
        val local = entity("a", updatedAt = "2024-01-01T00:00:00", deletedAt = null)
        val incoming = entity("a", updatedAt = "2024-01-02T00:00:00", deletedAt = LocalDateTime.parse("2024-01-02T00:00:00"))
        assertEquals(listOf(incoming), resolver.resolve(local, incoming))
    }

    private fun entity(
        id: String,
        updatedAt: String,
        deletedAt: LocalDateTime? = null,
    ) = TestSyncEntity(
        id = Id.Known(id),
        updatedDateTime = LocalDateTime.parse(updatedAt),
        deletedAt = deletedAt,
    )
}

private data class TestSyncEntity(
    override val id: Id.Known,
    override val updatedDateTime: LocalDateTime,
    override val deletedAt: LocalDateTime?,
) : SyncEntity
