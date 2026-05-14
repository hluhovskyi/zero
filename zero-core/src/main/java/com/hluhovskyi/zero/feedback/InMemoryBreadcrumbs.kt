package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.Closeable
import java.util.ArrayDeque

private const val NAV_CAPACITY = 50
private const val LOG_CAPACITY = 200

class InMemoryBreadcrumbs(
    private val routes: Flow<String>,
    private val clock: Clock,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : Breadcrumbs,
    Attachable {

    private val lock = Any()
    private val navigation = ArrayDeque<Breadcrumbs.Entry>(NAV_CAPACITY)
    private val breadcrumbs = ArrayDeque<Breadcrumbs.Entry>(LOG_CAPACITY)

    override fun log(message: String) {
        val entry = Breadcrumbs.Entry(clock.now(), message)
        synchronized(lock) {
            if (breadcrumbs.size == LOG_CAPACITY) breadcrumbs.removeFirst()
            breadcrumbs.addLast(entry)
        }
    }

    override fun snapshot(): Breadcrumbs.Snapshot = synchronized(lock) {
        Breadcrumbs.Snapshot(
            navigation = navigation.toList(),
            breadcrumbs = breadcrumbs.toList(),
        )
    }

    override fun attach(): Closeable = Closeables.of {
        routes
            .distinctUntilChanged()
            .onEach { route ->
                val entry = Breadcrumbs.Entry(clock.now(), route)
                synchronized(lock) {
                    if (navigation.size == NAV_CAPACITY) navigation.removeFirst()
                    navigation.addLast(entry)
                }
            }
            .launchIn(scope)
    }
}
