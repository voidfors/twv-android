package com.twv.viewers

import com.twv.viewers.logic.CollabSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollabSessionTest {

    private class FakeGql(val samples: Map<String, ChatterSample>) : TwitchGql {
        override suspend fun fetchChattersSample(login: String) = samples[login] ?: ChatterSample.EMPTY
        override suspend fun fetchMetaBatch(logins: List<String>): Map<String, Meta> = emptyMap()
    }

    private class MemCache : MetaCache {
        override suspend fun get(login: String): CachedMeta? = null
        override suspend fun put(login: String, meta: Meta, updatedAt: Long) {}
    }

    private object NoIrc : IrcClient {
        override fun messages(login: String): Flow<ChatMessage> = emptyFlow()
    }

    @Test fun addAndRemoveChannelsValidatesLogin() = runTest(UnconfinedTestDispatcher()) {
        val s = CollabSession(FakeGql(emptyMap()), MemCache(), NoIrc, backgroundScope, pollIntervalMs = 1_000_000L)
        assertFalse(s.addChannel(""))
        assertFalse(s.addChannel("bad name"))
        assertTrue(s.addChannel("@Foo"))
        assertFalse(s.addChannel("foo"))
        assertEquals(listOf("foo"), s.channels.value)
        s.removeChannel("FOO")
        assertTrue(s.channels.value.isEmpty())
        s.stop()
    }

    @Test fun groupStreamersAreExcludedFromViewersAndTotalsAreSummed() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql(
            mapOf(
                "a" to ChatterSample(listOf("x", "b"), count = 2),
                "b" to ChatterSample(listOf("y", "a"), count = 2)
            )
        )
        val s = CollabSession(gql, MemCache(), NoIrc, backgroundScope, pollIntervalMs = 1_000_000L)
        assertTrue(s.addChannel("a"))
        assertTrue(s.addChannel("b"))

        val logins = s.viewers.first().map { it.login }.toSet()
        assertEquals(setOf("x", "y"), logins)
        assertEquals(4, s.total.first())
        s.stop()
    }
}
