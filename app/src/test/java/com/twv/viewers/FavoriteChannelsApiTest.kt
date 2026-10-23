package com.twv.viewers

import com.twv.viewers.data.GqlStreamsApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FavoriteChannelsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GqlStreamsApi

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = GqlStreamsApi(url = server.url("/gql").toString())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun onlyLiveChannelsAreReturnedFromOneRequest() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{
                  "u0":{"login":"alpha","displayName":"Alpha","profileImageURL":"a",
                    "stream":{"title":"Live","viewersCount":42,"previewImageURL":"p","game":{"displayName":"Go"}}},
                  "u1":{"login":"beta","displayName":"Beta","profileImageURL":null,"stream":null},
                  "u2":null}}"""
            )
        )
        val live = api.fetchLiveChannels(listOf("Alpha", "@beta", "ghost", "alpha"))
        assertEquals(listOf("alpha"), live.map { it.login })
        assertEquals(42, live[0].viewers)
        assertEquals("a", live[0].avatarUrl)

        assertEquals(1, server.requestCount)
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        val vars = sent.getJSONObject("variables")
        assertEquals("alpha", vars.getString("l0"))
        assertEquals("beta", vars.getString("l1"))
        assertEquals("ghost", vars.getString("l2"))
        assertTrue(sent.getString("query").contains("u2: user(login: \$l2)"))
    }

    @Test fun emptyListMakesNoRequest() = runBlocking {
        assertTrue(api.fetchLiveChannels(emptyList()).isEmpty())
        assertEquals(0, server.requestCount)
    }
}
