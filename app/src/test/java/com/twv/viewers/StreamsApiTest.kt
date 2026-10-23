package com.twv.viewers

import com.twv.viewers.data.GqlStreamsApi
import com.twv.viewers.data.StreamsException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StreamsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GqlStreamsApi

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = GqlStreamsApi(url = server.url("/gql").toString(), pageSize = 2)
    }

    @After fun tearDown() { server.shutdown() }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test fun topStreamsAreParsedWithCursor() = runBlocking {
        enqueue(
            """{"data":{"streams":{"edges":[
              {"cursor":"c1","node":{"title":"T1","viewersCount":500,"previewImageURL":"p1",
                "game":{"displayName":"Chess"},"broadcaster":{"login":"Alpha","displayName":"Alpha","profileImageURL":"a1"}}},
              {"cursor":"c2","node":{"title":"T2","viewersCount":300,"previewImageURL":null,
                "game":null,"broadcaster":{"login":"beta","displayName":"","profileImageURL":null}}}
            ],"pageInfo":{"hasNextPage":true}}}}"""
        )
        val page = api.fetchStreams(cursor = "c0", query = null)
        assertEquals(listOf("alpha", "beta"), page.items.map { it.login })
        assertEquals("Chess", page.items[0].game)
        assertEquals("beta", page.items[1].displayName)
        assertNull(page.items[1].previewUrl)
        assertEquals("c2", page.nextCursor)

        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(2, sent.getJSONObject("variables").getInt("first"))
        assertEquals("c0", sent.getJSONObject("variables").getString("after"))
    }

    @Test fun lastPageHasNoCursor() = runBlocking {
        enqueue("""{"data":{"streams":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""")
        val page = api.fetchStreams(null, "")
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test fun searchReturnsOnlineAndOfflineAndMissing() = runBlocking {
        enqueue(
            """{"data":{"user":{"login":"alpha","displayName":"Alpha","profileImageURL":"a",
              "stream":{"title":"Live","viewersCount":42,"previewImageURL":"p","game":{"displayName":"Go"}}}}}"""
        )
        val online = api.fetchStreams(null, "@Alpha").items.single()
        assertEquals(42, online.viewers)
        assertEquals("Live", online.title)
        assertEquals("alpha", JSONObject(server.takeRequest().body.readUtf8())
            .getJSONObject("variables").getString("login"))

        enqueue("""{"data":{"user":{"login":"beta","displayName":"Beta","profileImageURL":null,"stream":null}}}""")
        val offline = api.fetchStreams(null, "beta").items.single()
        assertEquals(0, offline.viewers)
        assertEquals("", offline.title)

        enqueue("""{"data":{"user":null}}""")
        assertTrue(api.fetchStreams(null, "nobody").items.isEmpty())
    }

    @Test fun errorsBecomeReadableExceptions() = runBlocking {
        enqueue("""{"errors":[{"message":"failed integrity check"}],"data":null}""")
        val e1 = runCatching { api.fetchStreams(null, null) }.exceptionOrNull()
        assertTrue(e1 is StreamsException)
        assertTrue(e1!!.message!!.contains("failed integrity check"))

        enqueue("oops", code = 500)
        val e2 = runCatching { api.fetchStreams(null, null) }.exceptionOrNull()
        assertTrue(e2 is StreamsException)
        assertTrue(e2!!.message!!.contains("500"))

        enqueue("not json")
        assertTrue(runCatching { api.fetchStreams(null, null) }.exceptionOrNull() is StreamsException)
    }
}
