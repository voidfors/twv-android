package com.twv.viewers.data

import com.twv.viewers.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Порт gqlRequest / fetchChattersSample / fetchMetaBatch из background.js.
 * При ошибках fetchChattersSample возвращает пустой сэмпл (как расширение).
 */
class GqlClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val url: String = GQL_URL,
    private val log: (String) -> Unit = {}
) : TwitchGql {

    private val json = "application/json".toMediaType()

    private suspend fun post(body: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("Client-Id", CLIENT_ID)
            .post(body.toRequestBody(json))
            .build()
        http.newCall(req).await().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("GQL HTTP ${resp.code}: ${text.take(300)}")
            text
        }
    }

    override suspend fun fetchChattersSample(login: String): ChatterSample {
        val payload = JSONArray().put(
            JSONObject()
                .put("operationName", "CommunityTab")
                .put("variables", JSONObject().put("login", login))
                .put(
                    "extensions", JSONObject().put(
                        "persistedQuery",
                        JSONObject().put("version", 1).put("sha256Hash", COMMUNITY_TAB_HASH)
                    )
                )
        )
        return try {
            val data = JSONArray(post(payload.toString()))
            val item = data.optJSONObject(0) ?: return ChatterSample.EMPTY
            if (item.has("errors")) {
                log("GraphQL error (CommunityTab) $login: ${item.opt("errors")}")
                return ChatterSample.EMPTY
            }
            val chatters = item.optJSONObject("data")
                ?.optJSONObject("user")
                ?.optJSONObject("channel")
                ?.optJSONObject("chatters")
            if (chatters == null) {
                log("chatters отсутствует: $login")
                return ChatterSample.EMPTY
            }
            parseChatters(login, chatters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("fetchChattersSample $login: $e")
            ChatterSample.EMPTY
        }
    }

    private fun parseChatters(login: String, chatters: JSONObject): ChatterSample {
        val count = chatters.optInt("count", 0)
        val exclude = HashSet(KNOWN_BOTS).apply { add(login.lowercase()) }
        val excludedSeen = LinkedHashSet<String>()
        val bots = ArrayList<String>()

        for (l in logins(chatters.optJSONArray("chatbots"))) {
            val ll = l.lowercase()
            exclude.add(ll); excludedSeen.add(ll); bots.add(ll)
        }
        for (l in logins(chatters.optJSONArray("broadcasters"))) {
            val ll = l.lowercase()
            exclude.add(ll); excludedSeen.add(ll)
        }

        val all = logins(chatters.optJSONArray("moderators")) +
            logins(chatters.optJSONArray("vips")) +
            logins(chatters.optJSONArray("viewers"))

        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (l in all) {
            val ll = l.lowercase()
            if (ll in exclude) { excludedSeen.add(ll); continue }
            if (!seen.add(ll)) continue
            out.add(l)
        }
        return ChatterSample(out, count, bots, excludedSeen.toList())
    }

    private fun logins(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val l = arr.optJSONObject(i)?.optString("login", "").orEmpty()
            if (l.isNotEmpty()) out.add(l)
        }
        return out
    }

    override suspend fun fetchMetaBatch(logins: List<String>): Map<String, Meta> {
        if (logins.isEmpty()) return emptyMap()
        val payload = JSONArray()
        for (l in logins) {
            payload.put(
                JSONObject().put("query", META_QUERY).put("variables", JSONObject().put("login", l))
            )
        }
        return try {
            val data = JSONArray(post(payload.toString()))
            val out = HashMap<String, Meta>()
            logins.forEachIndexed { i, login ->
                val item = data.optJSONObject(i) ?: return@forEachIndexed
                if (item.has("errors")) return@forEachIndexed
                val user = item.optJSONObject("data")?.optJSONObject("user") ?: return@forEachIndexed
                val followers = user.optJSONObject("followers")?.let {
                    if (it.has("totalCount") && !it.isNull("totalCount")) it.optInt("totalCount") else null
                }
                out[login] = Meta(
                    displayName = user.optString("displayName", "").ifEmpty { login },
                    avatarUrl = user.optString("profileImageURL", "").ifEmpty { null },
                    createdAt = user.optString("createdAt", "").ifEmpty { null },
                    followers = followers
                )
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("fetchMetaBatch: $e")
            emptyMap()
        }
    }

    companion object {
        const val META_QUERY = """query GetChannelMeta(${'$'}login: String!) {
    user(login: ${'$'}login) {
      displayName createdAt profileImageURL(width: 70)
      followers { totalCount }
    }
  }"""
    }
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
