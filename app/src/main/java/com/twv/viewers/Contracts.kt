package com.twv.viewers

import kotlinx.coroutines.flow.Flow

/** Откуда зритель попал в список: из GQL-списка чаттеров или по сообщению в чате. */
enum class Source { LIST, CHAT }

/** Зритель одного канала. Поля повторяют запись viewersMap из background.js расширения. */
data class Viewer(
    val login: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val followers: Int? = null,
    val createdAt: String? = null,
    val source: Source = Source.LIST,
    val channel: String,
    val firstSeen: Long,
    val channelSince: Long,
    val lastSeen: Long,
    val lastChatAt: Long? = null,
    val msgCount: Int = 0,
    val missedPasses: Int = 0,
    val initialScan: Boolean = false,
    val moved: Boolean = false
)

data class StreamItem(
    val login: String,
    val displayName: String,
    val avatarUrl: String?,
    val title: String,
    val game: String?,
    val viewers: Int,
    val previewUrl: String?
)

data class Meta(
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: String?,
    val followers: Int?
)

data class CachedMeta(val meta: Meta, val updatedAt: Long)

/** Один ответ CommunityTab. logins — без ботов/стримера; excluded — исключённые, реально встреченные. */
data class ChatterSample(
    val logins: List<String>,
    val count: Int,
    val bots: List<String> = emptyList(),
    val excluded: List<String> = emptyList()
) {
    companion object { val EMPTY = ChatterSample(emptyList(), 0) }
}

data class ChatMessage(
    val nick: String,
    val displayName: String?,
    val roomId: String?,
    val sourceRoomId: String?
)

data class Streamer(val login: String, val displayName: String, val avatarUrl: String?)
data class Page<T>(val items: List<T>, val nextCursor: String?)

/** GQL, который реально есть в расширении: CommunityTab и GetChannelMeta. */
interface TwitchGql {
    suspend fun fetchChattersSample(login: String): ChatterSample
    /** Нет в Map = не удалось получить. */
    suspend fun fetchMetaBatch(logins: List<String>): Map<String, Meta>
}

/** Список стримов и поиск. В расширении этих запросов нет — отдельная задача B2. */
interface StreamsApi {
    suspend fun fetchStreams(cursor: String?, query: String?): Page<StreamItem>
}

interface IrcClient {
    /** Анонимное чтение чата, с переподключением. Отмена Flow закрывает сокет. */
    fun messages(login: String): Flow<ChatMessage>
}

interface MetaCache {
    suspend fun get(login: String): CachedMeta?
    suspend fun put(login: String, meta: Meta, updatedAt: Long)
}
