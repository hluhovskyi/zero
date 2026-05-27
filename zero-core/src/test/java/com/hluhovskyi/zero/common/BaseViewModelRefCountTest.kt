package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseViewModelRefCountTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun io(): CoroutineDispatcher = dispatcher
        override fun cpu(): CoroutineDispatcher = dispatcher
        override fun main(): CoroutineDispatcher = dispatcher
    }

    private inner class Subject : BaseViewModel(dispatchers) {
        var attachOnMainCalls = 0
        val liveScope: Boolean
            get() = scope.coroutineContext[Job]!!.isActive

        override fun attachOnMain() {
            attachOnMainCalls++
        }
    }

    @Test
    fun `attachOnMain runs once across multiple attach and scope closes only on last release`() {
        val subject = Subject()

        val a = subject.attach()
        val b = subject.attach()

        assertEquals(1, subject.attachOnMainCalls)
        assertTrue(subject.liveScope)

        a.close()
        assertTrue(subject.liveScope)

        b.close()
        assertFalse(subject.liveScope)
    }
}
