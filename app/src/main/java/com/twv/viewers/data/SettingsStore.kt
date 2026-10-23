package com.twv.viewers.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Настройки и избранные. Избранные — это зрители, которых нужно закреплять сверху. */
interface SettingsStore {
    val favorites: Flow<Set<String>>
    /** Избранные каналы (стримеры): если в эфире — показываются наверху списка стримов. */
    val favoriteChannels: Flow<Set<String>>
    val chatEnabled: Flow<Boolean>
    val notifyFavorites: Flow<Boolean>
    /** Последние открытые каналы, новые первыми. */
    val recentChannels: Flow<List<String>>

    suspend fun toggleFavorite(login: String)
    suspend fun toggleFavoriteChannel(login: String)
    suspend fun clearFavoriteChannels()
    suspend fun setChatEnabled(enabled: Boolean)
    suspend fun setNotifyFavorites(enabled: Boolean)
    suspend fun clearFavorites()
    suspend fun pushRecentChannel(login: String)
}

private val Context.dataStore by preferencesDataStore(name = "twv_settings")

class DataStoreSettings(private val context: Context) : SettingsStore {
    private val kFavorites = stringSetPreferencesKey("favorites")
    private val kFavChannels = stringSetPreferencesKey("favorite_channels")
    private val kChat = booleanPreferencesKey("chat_enabled")
    private val kNotify = booleanPreferencesKey("notify_favorites")
    private val kRecent = stringPreferencesKey("recent_channels") // через пробел: логины Twitch без пробелов

    override val favorites: Flow<Set<String>> =
        context.dataStore.data.map { it[kFavorites] ?: emptySet() }

    override val favoriteChannels: Flow<Set<String>> =
        context.dataStore.data.map { it[kFavChannels] ?: emptySet() }

    override val chatEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[kChat] ?: true }

    override val notifyFavorites: Flow<Boolean> =
        context.dataStore.data.map { it[kNotify] ?: false }

    override val recentChannels: Flow<List<String>> =
        context.dataStore.data.map { p -> (p[kRecent] ?: "").split(' ').filter { it.isNotBlank() } }

    override suspend fun toggleFavorite(login: String) {
        val l = login.lowercase()
        context.dataStore.edit { p ->
            val cur = p[kFavorites] ?: emptySet()
            p[kFavorites] = if (l in cur) cur - l else cur + l
        }
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
        context.dataStore.edit { it.remove(kFavorites) }
    }

    override suspend fun pushRecentChannel(login: String) {
        val l = login.lowercase()
        context.dataStore.edit { p ->
            val cur = (p[kRecent] ?: "").split(' ').filter { it.isNotBlank() && it != l }
            p[kRecent] = (listOf(l) + cur).take(10).joinToString(" ")
        }
    }
}
