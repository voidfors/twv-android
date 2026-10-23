package com.twv.viewers

import com.twv.viewers.data.GqlClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GqlClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GqlClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = GqlClient(url = server.url("/gql").toString())
    }

    @After fun tearDown() { server.shutdown() }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test fun chattersAreMergedFilteredAndDeduped() = runBlocking {
        enqueue(
            """[{"data":{"user":{"channel":{"chatters":{
              "count": 9,
              "broadcasters":[{"login":"streamer"}],
              "chatbots":[{"login":"SomeBot"}],
              "moderators":[{"login":"Mod1"}],
              "vips":[{"login":"vip1"}],
              "viewers":[{"login":"v1"},{"login":"V1"},{"login":"nightbot"},{"login":"somebot"},{"login":"streamer"}]
            }}}}}]"""
        )
        val s = client.fetchChattersSample("Streamer")
        assertEquals(9, s.count)
        assertEquals(listOf("Mod1", "vip1", "v1"), s.logins)
        assertEquals(listOf("somebot"), s.bots)
        assertTrue(s.excluded.containsAll(listOf("streamer", "somebot", "nightbot")))

        val req = server.takeRequest()
        assertEquals(CLIENT_ID, req.getHeader("Client-Id"))
        val sent = JSONArray(req.body.readUtf8()).getJSONObject(0)
        assertEquals("CommunityTab", sent.getString("operationName"))
        assertEquals("Streamer", sent.getJSONObject("variables").getString("login"))
        assertEquals(
            COMMUNITY_TAB_HASH,
            sent.getJSONObject("extensions").getJSONObject("persistedQuery").getString("sha256Hash")
        )
    }

    @Test fun graphQlErrorGivesEmptySample() = runBlocking {
        enqueue("""[{"errors":[{"message":"boom"}]}]""")
        assertEquals(ChatterSample.EMPTY, client.fetchChattersSample("x"))
    }

    @Test fun httpErrorGivesEmptySample() = runBlocking {
        enqueue("oops", 500)
        assertEquals(ChatterSample.EMPTY, client.fetchChattersSample("x"))
    }

    @Test fun missingChattersGivesEmptySample() = runBlocking {
        enqueue("""[{"data":{"user":null}}]""")
        assertEquals(ChatterSample.EMPTY, client.fetchChattersSample("x"))
    }

    @Test fun metaBatchKeepsOrderAndSkipsFailures() = runBlocking {
        enqueue(
            """[
              {"data":{"user":{"displayName":"Alice","createdAt":"2020-01-01T00:00:00Z","profileImageURL":"http://a/1.png","followers":{"totalCount":42}}}},
              {"data":{"user":null}},
              {"errors":[{"message":"x"}]}
            ]"""
        )
        val m = client.fetchMetaBatch(listOf("alice", "ghost", "bad"))
        assertEquals(setOf("alice"), m.keys)
        assertEquals(Meta("Alice", "http://a/1.png", "2020-01-01T00:00:00Z", 42), m["alice"])

        val sent = JSONArray(server.takeRequest().body.readUtf8())
        assertEquals(3, sent.length())
        assertTrue(sent.getJSONObject(0).getString("query").contains("GetChannelMeta"))
        assertEquals("ghost", sent.getJSONObject(1).getJSONObject("variables").getString("login"))
    }

    @Test fun metaBatchFailureGivesEmptyMap() = runBlocking {
        enqueue("nope", 502)
        assertTrue(client.fetchMetaBatch(listOf("a")).isEmpty())
    }
}
