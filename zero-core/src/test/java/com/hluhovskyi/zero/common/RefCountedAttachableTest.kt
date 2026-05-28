package com.hluhovskyi.zero.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

class RefCountedAttachableTest {

    private class FakeAttach {
        val starts = AtomicInteger(0)
        val closes = AtomicInteger(0)
        var live = 0
            private set

        fun onAttach(): Closeable {
            starts.incrementAndGet()
            live++
            return Closeable {
                closes.incrementAndGet()
                live--
            }
        }
    }

    @Test
    fun `starts once on first attach and stays started while count positive`() {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)

        val a = attachable.attach()
        val b = attachable.attach()

        assertEquals(1, fake.starts.get())
        assertEquals(1, fake.live)

        a.close()
        assertEquals(0, fake.closes.get())
        assertEquals(1, fake.live)

        b.close()
        assertEquals(1, fake.closes.get())
        assertEquals(0, fake.live)
    }

    @Test
    fun `re-attach after full release starts again`() {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)

        attachable.attach().close()
        attachable.attach()

        assertEquals(2, fake.starts.get())
        assertEquals(1, fake.live)
    }

    @Test
    fun `double-close of one handle does not over-decrement`() {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)

        val a = attachable.attach()
        val b = attachable.attach()

        a.close()
        a.close()

        assertEquals(1, fake.live)
        b.close()
        assertEquals(0, fake.live)
    }

    @Test
    fun `concurrent attach then close fully releases`() = runBlocking {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)

        val handles = (0 until 16).map { async(Dispatchers.Default) { attachable.attach() } }.awaitAll()
        handles.map { async(Dispatchers.Default) { it.close() } }.awaitAll()

        assertEquals(0, fake.live)
        assertEquals(1, fake.closes.get())
    }
}
