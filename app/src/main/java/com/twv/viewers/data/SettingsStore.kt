package com.twv.viewers.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    /** Синхронизация с Google Drive включена (по умолчанию да; работает после входа в аккаунт). */
    val syncEnabled: Flow<Boolean>
    /** Аккаунт Google, выбранный для синхронизации; null — вход не выполнен. Только на устройстве. */
    val syncAccount: Flow<String?>
    /** Всё синхронизируемое (настройки и избранные) вместе с меткой времени изменения. */
    val snapshots: Flow<SettingsSnapshot>
    suspend fun snapshot(): SettingsSnapshot
    /** Записывает копию из облака; метка времени берётся из неё, чтобы не вызвать ответную отправку. */
    suspend fun applySnapshot(s: SettingsSnapshot)
    suspend fun setSyncEnabled(enabled: Boolean)
    suspend fun setSyncAccount(account: String?)
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
    private val kSyncEnabled = booleanPreferencesKey("sync_enabled")
    private val kSyncAccount = stringPreferencesKey("sync_account")
    private val kUpdatedAt = longPreferencesKey("updated_at") // время последнего изменения синхронизируемых данных

    /** Правка синхронизируемых данных: заодно обновляет метку времени. */
    private suspend fun editSynced(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it); it[kUpdatedAt] = System.currentTimeMillis() }
    }

    private fun Preferences.toSnapshot() = SettingsSnapshot(
        favorites = allFavorites(),
        favoriteChannels = this[kFavChannels] ?: emptySet(),
        chatEnabled = this[kChat] ?: true,
        notifyFavorites = this[kNotify] ?: false,
        streamLanguages = this[kLangs] ?: emptySet(),
        streamTags = (this[kTags] ?: "").split('\n').filter { it.isNotBlank() },
        updatedAt = this[kUpdatedAt] ?: 0L
    )

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

    override val syncEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[kSyncEnabled] ?: true }

    override val syncAccount: Flow<String?> =
        context.dataStore.data.map { it[kSyncAccount] }

    override val snapshots: Flow<SettingsSnapshot> =
        context.dataStore.data.map { it.toSnapshot() }.distinctUntilChanged()

    override suspend fun snapshot(): SettingsSnapshot = snapshots.first()

    override suspend fun applySnapshot(s: SettingsSnapshot) {
        context.dataStore.edit { p ->
            p[kFavorites] = FavoriteKeys.logins(s.favorites)
            p.remove(kLegacyFavorites); p.remove(kByChannel)
            p[kFavChannels] = s.favoriteChannels.map { it.lowercase() }.toSet()
            p[kChat] = s.chatEnabled
            p[kNotify] = s.notifyFavorites
            p[kLangs] = s.streamLanguages.map { it.lowercase() }.toSet()
            p[kTags] = s.streamTags.joinToString("\n")
            p[kUpdatedAt] = s.updatedAt
        }
    }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[kSyncEnabled] = enabled }
    }

    override suspend fun setSyncAccount(account: String?) {
        context.dataStore.edit { if (account == null) it.remove(kSyncAccount) else it[kSyncAccount] = account }
    }

    override suspend fun toggleFavorite(login: String) {
        val l = FavoriteKeys.normalize(login)
        if (l.isEmpty()) return
        editSynced { p ->
            val cur = p.allFavorites()
            p[kFavorites] = if (l in cur) cur - l else cur + l
            p.remove(kLegacyFavorites); p.remove(kByChannel) // миграция завершена
        }
    }

    override suspend fun setStreamLanguages(codes: Set<String>) {
        editSynced { it[kLangs] = codes.map { c -> c.lowercase() }.toSet() }
    }

    override suspend fun setStreamTags(tags: List<String>) {
        editSynced { it[kTags] = tags.map { t -> t.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n") }
    }

    override suspend fun toggleFavoriteChannel(login: String) {
        val l = login.lowercase()
        editSynced { p ->
            val cur = p[kFavChannels] ?: emptySet()
            p[kFavChannels] = if (l in cur) cur - l else cur + l
        }
    }

    override suspend fun clearFavoriteChannels() {
        editSynced { it.remove(kFavChannels) }
    }

    override suspend fun setChatEnabled(enabled: Boolean) {
        editSynced { it[kChat] = enabled }
    }

    override suspend fun setNotifyFavorites(enabled: Boolean) {
        editSynced { it[kNotify] = enabled }
    }

    override suspend fun clearFavorites() {
        editSynced { it.remove(kFavorites); it.remove(kLegacyFavorites); it.remove(kByChannel) }
    }

    override suspend fun pushRecentChannel(login: String) {
        val l = login.lowercase()
        context.dataStore.edit { p ->
            val cur = (p[kRecent] ?: "").split(' ').filter { it.isNotBlank() && it != l }
            p[kRecent] = (listOf(l) + cur).take(10).joinToString(" ")
        }
    }
}
