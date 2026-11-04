package com.twv.viewers.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Избранные зрители общие для всех каналов: хранятся просто логины (нижний регистр).
 * Зритель, добавленный в избранное на одном канале, избранный и на всех остальных.
 */
object FavoriteKeys {
    fun normalize(login: String) = login.trim().lowercase()

    /**
     * Приводит записи любого формата к логинам. Старый формат "канал:логин" (избранные по каналам)
     * превращается в "логин" — так ничего из ранее добавленного не теряется.
     * В логинах Twitch нет ':', поэтому разделитель однозначен.
     */
    fun logins(entries: Collection<String>): Set<String> =
        entries.mapNotNull { e ->
            normalize(e.substringAfter(':')).takeIf { it.isNotEmpty() }
        }.toSet()
}

/** Настройки и избранные. Избранные — это зрители, которых нужно закреплять сверху. */
interface SettingsStore {
    /** Логины избранных зрителей (нижний регистр), общие для всех каналов. */
    val favorites: Flow<Set<String>>
    /** Избранные каналы (стримеры): если в эфире — показываются наверху списка стримов. */
    val favoriteChannels: Flow<Set<String>>
    val chatEnabled: Flow<Boolean>
    val notifyFavorites: Flow<Boolean>
    /** Последние открытые каналы, новые первыми. */
    val recentChannels: Flow<List<String>>
    /** Языки трансляций для списка стримов (коды ISO, нижний регистр). Пусто = любые. */
    val streamLanguages: Flow<Set<String>>
    /** Теги для списка стримов. Пусто = любые. */
    val streamTags: Flow<List<String>>

    /** Добавляет зрителя в общие избранные или убирает, если он уже там. */
    suspend fun toggleFavorite(login: String)
    suspend fun setStreamLanguages(codes: Set<String>)
    suspend fun setStreamTags(tags: List<String>)
    suspend fun toggleFavoriteChannel(login: String)
    suspend fun clearFavoriteChannels()
    suspend fun setChatEnabled(enabled: Boolean)
    suspend fun setNotifyFavorites(enabled: Boolean)
    suspend fun clearFavorites()
    suspend fun pushRecentChannel(login: String)
}

private val Context.dataStore by preferencesDataStore(name = "twv_settings")

class DataStoreSettings(private val context: Context) : SettingsStore {
    // Общие избранные. Два старых ключа читаются для миграции: "favorites" (логины) и
    // "favorites_by_channel" ("канал:логин"); при первой же записи всё сливается в kFavorites.
    private val kFavorites = stringSetPreferencesKey("favorites_global")
    private val kLegacyFavorites = stringSetPreferencesKey("favorites")
    private val kByChannel = stringSetPreferencesKey("favorites_by_channel")

    private fun androidx.datastore.preferences.core.Preferences.allFavorites(): Set<String> =
        FavoriteKeys.logins((this[kFavorites] ?: emptySet()) + (this[kLegacyFavorites] ?: emptySet()) + (this[kByChannel] ?: emptySet()))
    private val kLangs = stringSetPreferencesKey("stream_languages")
    private val kTags = stringPreferencesKey("stream_tags") // через перевод строки
    private val kFavChannels = stringSetPreferencesKey("favorite_channels")
    private val kChat = booleanPreferencesKey("chat_enabled")
    private val kNotify = booleanPreferencesKey("notify_favorites")
    private val kRecent = stringPreferencesKey("recent_channels") // через пробел: логины Twitch без пробелов

    override val favorites: Flow<Set<String>> =
        context.dataStore.data.map { it.allFavorites() }

    override val favoriteChannels: Flow<Set<String>> =
        context.dataStore.data.map { it[kFavChannels] ?: emptySet() }

    override val chatEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[kChat] ?: true }

    override val notifyFavorites: Flow<Boolean> =
        context.dataStore.data.map { it[kNotify] ?: false }

    override val recentChannels: Flow<List<String>> =
        context.dataStore.data.map { p -> (p[kRecent] ?: "").split(' ').filter { it.isNotBlank() } }

    override val streamLanguages: Flow<Set<String>> =
        context.dataStore.data.map { it[kLangs] ?: emptySet() }

    override val streamTags: Flow<List<String>> =
        context.dataStore.data.map { p -> (p[kTags] ?: "").split('\n').filter { it.isNotBlank() } }

    override suspend fun toggleFavorite(login: String) {
        val l = FavoriteKeys.normalize(login)
        if (l.isEmpty()) return
        context.dataStore.edit { p ->
            val cur = p.allFavorites()
            p[kFavorites] = if (l in cur) cur - l else cur + l
            p.remove(kLegacyFavorites); p.remove(kByChannel) // миграция завершена
        }
    }

    override suspend fun setStreamLanguages(codes: Set<String>) {
        context.dataStore.edit { it[kLangs] = codes.map { c -> c.lowercase() }.toSet() }
    }

    override suspend fun setStreamTags(tags: List<String>) {
        context.dataStore.edit { it[kTags] = tags.map { t -> t.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n") }
    }

    override suspend fun toggleFavoriteChannel(login: String) {
        val l = login.lowercase()
        context.dataStore.edit { p ->
            val cur = p[kFavChannels] ?: emptySet()
            p[kFavChannels] = if (l in cur) cur - l else cur + l
        }
    }

    override suspend fun clearFavoriteChannels() {
        context.dataStore.edit { it.remove(kFavChannels) }
    }

    override suspend fun setChatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[kChat] = enabled }
    }

    override suspend fun setNotifyFavorites(enabled: Boolean) {
        context.dataStore.edit { it[kNotify] = enabled }
    }

    override suspend fun clearFavorites() {
        context.dataStore.edit { it.remove(kFavorites); it.remove(kLegacyFavorites); it.remove(kByChannel) }
    }

    override suspend fun pushRecentChannel(login: String) {
        val l = login.lowercase()
        context.dataStore.edit { p ->
            val cur = (p[kRecent] ?: "").split(' ').filter { it.isNotBlank() && it != l }
            p[kRecent] = (listOf(l) + cur).take(10).joinToString(" ")
        }
    }
}
