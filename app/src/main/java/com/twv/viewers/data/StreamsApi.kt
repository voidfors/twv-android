package com.twv.viewers.data

import com.twv.viewers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * B2: список стримов и поиск. В расширении таких запросов нет, поэтому ВСЕ непроверенные
 * GQL-запросы лежат только в этом файле. Формы ответов выведены из знаний о схеме Twitch GQL
 * и не сверялись с живым ответом: проверка — один раз на устройстве при первом запуске.
 * Любая ошибка выбрасывается как [StreamsException] с понятным текстом для экрана.
 */
class StreamsException(message: String, cause: Throwable? = null) : IOException(message, cause)

class GqlStreamsApi(
    private val http: OkHttpClient = OkHttpClient(),
    private val url: String = GQL_URL,
    private val pageSize: Int = 30
) : StreamsApi {

    private val json = "application/json".toMediaType()

    override suspend fun fetchStreams(cursor: String?, query: String?): Page<StreamItem> {
        val q = query.orEmpty().trim().removePrefix("@").lowercase()
        return try {
            if (q.isEmpty()) topStreams(cursor) else searchByLogin(q)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: StreamsException) {
            throw e
        } catch (e: Exception) {
            throw StreamsException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private suspend fun post(query: String, variables: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
        val req = Request.Builder().url(url).header("Client-Id", CLIENT_ID)
            .post(body.toRequestBody(json)).build()
        http.newCall(req).await().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw StreamsException("HTTP ${resp.code}")
            val root = JSONObject(text)
            root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotEmpty() }
                ?.let { throw StreamsException("Twitch: $it") }
            root.optJSONObject("data") ?: throw StreamsException("Пустой ответ Twitch")
        }
    }

    private suspend fun topStreams(cursor: String?): Page<StreamItem> {
        val vars = JSONObject().put("first", pageSize)
        if (cursor != null) vars.put("after", cursor)
        val streams = post(STREAMS_QUERY, vars).optJSONObject("streams")
            ?: throw StreamsException("В ответе нет списка стримов")
        val edges = streams.optJSONArray("edges") ?: throw StreamsException("В ответе нет списка стримов")
        val items = ArrayList<StreamItem>(edges.length())
        var last: String? = null
        for (i in 0 until edges.length()) {
            val e = edges.optJSONObject(i) ?: continue
            last = e.optString("cursor").ifEmpty { last }
            val n = e.optJSONObject("node") ?: continue
            val b = n.optJSONObject("broadcaster") ?: continue
            val login = b.optString("login").lowercase()
            if (login.isEmpty()) continue
            items.add(
                StreamItem(
                    login = login,
                    displayName = b.optString("displayName").ifEmpty { login },
                    avatarUrl = b.optString("profileImageURL").ifEmpty { null },
                    title = n.optString("title"),
                    game = n.optJSONObject("game")?.optString("displayName")?.ifEmpty { null },
                    viewers = n.optInt("viewersCount", 0),
                    previewUrl = n.optString("previewImageURL").ifEmpty { null }
                )
            )
        }
        val hasNext = streams.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
        return Page(items, if (hasNext) last else null)
    }

    /** Поиск по логину: один канал; офлайн-канал возвращается с viewers = 0 и пустым title. */
    private suspend fun searchByLogin(login: String): Page<StreamItem> {
        val user = post(USER_QUERY, JSONObject().put("login", login)).optJSONObject("user")
            ?: return Page(emptyList(), null)
        val stream = user.optJSONObject("stream")
        val item = StreamItem(
            login = user.optString("login").ifEmpty { login }.lowercase(),
            displayName = user.optString("displayName").ifEmpty { login },
            avatarUrl = user.optString("profileImageURL").ifEmpty { null },
            title = stream?.optString("title").orEmpty(),
            game = stream?.optJSONObject("game")?.optString("displayName")?.ifEmpty { null },
            viewers = stream?.optInt("viewersCount", 0) ?: 0,
            previewUrl = stream?.optString("previewImageURL")?.ifEmpty { null }
        )
        return Page(listOf(item), null)
    }

    companion object {
        const val STREAMS_QUERY = """query Streams(${'$'}first: Int!, ${'$'}after: Cursor) {
  streams(first: ${'$'}first, after: ${'$'}after, options: {sort: VIEWER_COUNT}) {
    edges { cursor node {
      title viewersCount previewImageURL(width: 320, height: 180)
      game { displayName }
      broadcaster { login displayName profileImageURL(width: 70) }
    } }
    pageInfo { hasNextPage }
  }
}"""
        const val USER_QUERY = """query UserStream(${'$'}login: String!) {
  user(login: ${'$'}login) {
    login displayName profileImageURL(width: 70)
    stream { title viewersCount previewImageURL(width: 320, height: 180) game { displayName } }
  }
}"""
    }
}
