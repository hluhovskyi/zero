package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryBreadcrumbsTest {

    private val baseInstant = Instant.parse("2024-06-01T12:00:00Z")
    private val clock = object : Clock {
        private var counter = 0L
        override fun now(): Instant = baseInstant.plus(kotlin.time.Duration.parse("${counter++}s"))
    }

    private fun newBreadcrumbs(
        routes: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 128),
    ): Pair<InMemoryBreadcrumbs, MutableSharedFlow<String>> {
        val breadcrumbs = InMemoryBreadcrumbs(
            routes = routes,
            clock = clock,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        return breadcrumbs to routes
    }

    @Test
    fun `log appends and snapshot returns entries in order`() {
        val (breadcrumbs, _) = newBreadcrumbs()

        breadcrumbs.log("first")
        breadcrumbs.log("second")
        breadcrumbs.log("third")

        val messages = breadcrumbs.snapshot().breadcrumbs.map { it.message }
        assertEquals(listOf("first", "second", "third"), messages)
    }

    @Test
    fun `breadcrumb ring evicts oldest when capacity exceeded`() {
        val (breadcrumbs, _) = newBreadcrumbs()

        repeat(250) { i -> breadcrumbs.log("entry-$i") }

        val snapshot = breadcrumbs.snapshot().breadcrumbs
        assertEquals(200, snapshot.size)
        assertEquals("entry-50", snapshot.first().message)
        assertEquals("entry-249", snapshot.last().message)
    }

    @Test
    fun `routes flow drives navigation and consecutive duplicates are deduplicated`() = runBlocking {
        val (breadcrumbs, routes) = newBreadcrumbs()
        val closeable = breadcrumbs.attach()

        routes.emit("transactions/all")
        routes.emit("transactions/all")
        routes.emit("transactions/edit")
        routes.emit("transactions/all")

        val nav = breadcrumbs.snapshot().navigation.map { it.message }
        assertEquals(listOf("transactions/all", "transactions/edit", "transactions/all"), nav)

        closeable.close()
    }

    @Test
    fun `navigation ring evicts oldest when capacity exceeded`() = runBlocking {
        val (breadcrumbs, routes) = newBreadcrumbs()
        val closeable = breadcrumbs.attach()

        repeat(70) { i -> routes.emit("route-$i") }

        val nav = breadcrumbs.snapshot().navigation
        assertEquals(50, nav.size)
        assertEquals("route-20", nav.first().message)
        assertEquals("route-69", nav.last().message)

        closeable.close()
    }

    @Test
    fun `concurrent writes preserve every entry`() = runBlocking {
        val (breadcrumbs, _) = newBreadcrumbs()

        val writers = (0 until 4).map { id ->
            async(Dispatchers.Default) {
                repeat(40) { i -> breadcrumbs.log("$id-$i") }
            }
        }
        writers.awaitAll()

        val snapshot = breadcrumbs.snapshot().breadcrumbs
        assertEquals(160, snapshot.size)
    }
}
