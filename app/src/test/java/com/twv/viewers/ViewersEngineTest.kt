package com.twv.viewers

import com.twv.viewers.logic.ViewersEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewersEngineTest {

    private class FakeGql : TwitchGql {
        var sample = ChatterSample.EMPTY
        var metas = emptyMap<String, Meta>()
        var calls = 0
        override suspend fun fetchChattersSample(login: String): ChatterSample { calls++; return sample }
        override suspend fun fetchMetaBatch(logins: List<String>): Map<String, Meta> =
            logins.mapNotNull { l -> metas[l]?.let { l to it } }.toMap()
    }

    private class MemCache : MetaCache {
        val data = HashMap<String, CachedMeta>()
        override suspend fun get(login: String) = data[login]
        override suspend fun put(login: String, meta: Meta, updatedAt: Long) { data[login] = CachedMeta(meta, updatedAt) }
    }

    private var now = 1_000_000L

    private fun TestScope.engine(
        gql: TwitchGql, cache: MetaCache = MemCache(),
        group: () -> Set<String> = { emptySet() }, sibs: () -> List<ViewersEngine> = { emptyList() },
        onNew: (Viewer) -> Unit = {}
    ) = ViewersEngine(
        "Chan", gql, cache, this, clock = { now }, groupStreamers = group, siblings = sibs,
        onNewViewer = onNew
    )

    @Test fun collectsViewersAndStopsWhenCountReached() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply { sample = ChatterSample(listOf("a", "b"), count = 3, excluded = listOf("chan")) }
        val e = engine(gql)
        e.collectFullPass()
        assertEquals(setOf("a", "b"), e.viewers.value.map { it.login }.toSet())
        assertEquals(3, e.total.value)
        // 1 первый запрос + 1 раунд из 4: уже 2 + 1 исключённый >= 3
        assertEquals(1 + 4, gql.calls)
        assertTrue(e.viewers.value.all { it.initialScan })
    }

    @Test fun stopsAfterFourZeroGainRounds() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply { sample = ChatterSample(listOf("a"), count = 100) }
        engine(gql).collectFullPass()
        // первый запрос + 1 раунд с приростом 0 (a уже есть) ... всего 4 нулевых раунда по 4 запроса
        assertEquals(1 + 4 * 4, gql.calls)
    }

    @Test fun viewerFadesThenIsRemoved() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply { sample = ChatterSample(listOf("a", "b"), count = 2) }
        val e = engine(gql)
        e.collectFullPass()
        gql.sample = ChatterSample(listOf("a"), count = 1)
        e.collectFullPass()
        assertEquals(1, e.viewers.value.first { it.login == "b" }.missedPasses)
        e.collectFullPass()
        assertEquals(listOf("a"), e.viewers.value.map { it.login })
    }

    @Test fun emptyResponseDoesNotWipeList() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply { sample = ChatterSample(listOf("a"), count = 1) }
        val e = engine(gql)
        e.collectFullPass()
        gql.sample = ChatterSample.EMPTY
        e.collectFullPass()
        assertEquals(listOf("a"), e.viewers.value.map { it.login })
        assertEquals(0, e.viewers.value[0].missedPasses)
    }

    @Test fun chatAuthorIsAddedAndProtectedFromRemoval() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply { sample = ChatterSample(listOf("a"), count = 1) }
        val e = engine(gql)
        e.collectFullPass()
        e.onChat(ChatMessage("Talker", "Talker", "1", null))
        val t = e.viewers.value.first { it.login == "talker" }
        assertEquals(Source.CHAT, t.source)
        assertEquals(1, t.msgCount)
        // два прохода без него — но он только что писал, не удаляется
        e.collectFullPass(); e.collectFullPass()
        assertTrue(e.viewers.value.any { it.login == "talker" })
        // через 3+ минуты без сообщений — затемнение, затем удаление
        now += CHAT_GRACE_MS + 1
        e.collectFullPass(); e.collectFullPass()
        assertFalse(e.viewers.value.any { it.login == "talker" })
    }

    @Test fun botsAndStreamerIgnoredInChat() = runTest(UnconfinedTestDispatcher()) {
        val e = engine(FakeGql())
        e.onChat(ChatMessage("Nightbot", null, null, null))
        e.onChat(ChatMessage("chan", null, null, null))
        assertTrue(e.viewers.value.isEmpty())
    }

    @Test fun metaFromNetworkUpdatesViewerAndCache() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply {
            sample = ChatterSample(listOf("a"), count = 1)
            metas = mapOf("a" to Meta("Alice", "http://x/a.png", "2020-01-01T00:00:00Z", 7))
        }
        val cache = MemCache()
        val e = engine(gql, cache)
        e.collectFullPass()
        val v = e.viewers.value.first()
        assertEquals("Alice", v.displayName)
        assertEquals(7, v.followers)
        assertEquals("Alice", cache.data["a"]?.meta?.displayName)
    }

    @Test fun groupStreamersAreNotViewers() = runTest(UnconfinedTestDispatcher()) {
        val gql = FakeGql().apply { sample = ChatterSample(listOf("a", "partner"), count = 3) }
        val e = engine(gql, group = { setOf("chan", "partner") })
        e.collectFullPass()
        assertEquals(listOf("a"), e.viewers.value.map { it.login })
    }

    @Test fun viewerMovedFromSiblingKeepsFirstSeenAndIsNotNew() = runTest(UnconfinedTestDispatcher()) {
        val g1 = FakeGql().apply { sample = ChatterSample(listOf("mover"), count = 1) }
        val g2 = FakeGql().apply { sample = ChatterSample(listOf("mover"), count = 1) }
        var sib: ViewersEngine? = null
        val newOnes = ArrayList<String>()
        val first = ViewersEngine("one", g1, MemCache(), this, clock = { now })
        sib = first
        val second = ViewersEngine("two", g2, MemCache(), this, clock = { now },
            groupStreamers = { setOf("one", "two") }, siblings = { listOfNotNull(sib) },
            onNewViewer = { newOnes.add(it.login) })
        first.collectFullPass()
        val t0 = first.viewers.value.first().firstSeen
        now += 5_000
        second.collectFullPass()   // первый скан: onNewViewer не вызывается
        now += 5_000
        g2.sample = ChatterSample(listOf("mover", "fresh"), count = 2)
        second.collectFullPass()
        val mover = second.viewers.value.first { it.login == "mover" }
        assertEquals(t0, mover.firstSeen)
        assertTrue(mover.moved)
        assertEquals(listOf("fresh"), newOnes)
    }

    @Test fun sharedChatEchoIsDropped() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGql()
        lateinit var a: ViewersEngine
        lateinit var b: ViewersEngine
        a = ViewersEngine("a", g, MemCache(), this, clock = { now },
            groupStreamers = { setOf("a", "b") }, siblings = { listOf(a, b) })
        b = ViewersEngine("b", g, MemCache(), this, clock = { now },
            groupStreamers = { setOf("a", "b") }, siblings = { listOf(a, b) })
        // a знает свой room-id = 1, b = 2
        a.onChat(ChatMessage("x", null, "1", "1"))
        b.onChat(ChatMessage("y", null, "2", "2"))
        // сообщение написано в чате a, пришло в оба
        a.onChat(ChatMessage("writer", null, "1", "1"))
        b.onChat(ChatMessage("writer", null, "2", "1"))
        assertTrue(a.viewers.value.any { it.login == "writer" })
        assertFalse(b.viewers.value.any { it.login == "writer" })
    }
}
