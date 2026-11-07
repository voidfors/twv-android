package com.twv.viewers.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Всё, что синхронизируется с Google Drive: настройки и избранные (зрители и каналы).
 * Недавние каналы и сам переключатель синхронизации остаются только на устройстве.
 * [updatedAt] — время последнего локального изменения (мс), по нему выбирается более новая копия.
 */
data class SettingsSnapshot(
    val favorites: Set<String> = emptySet(),
    val favoriteChannels: Set<String> = emptySet(),
    val chatEnabled: Boolean = true,
    val notifyFavorites: Boolean = false,
    val streamLanguages: Set<String> = emptySet(),
    val streamTags: List<String> = emptyList(),
    val updatedAt: Long = 0L
) {
    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("updatedAt", updatedAt)
        put("favorites", JSONArray(favorites.sorted()))
        put("favoriteChannels", JSONArray(favoriteChannels.sorted()))
        put("chatEnabled", chatEnabled)
        put("notifyFavorites", notifyFavorites)
        put("streamLanguages", JSONArray(streamLanguages.sorted()))
        put("streamTags", JSONArray(streamTags))
    }.toString()

    companion object {
        /** null, если текст не разобрался (повреждённый файл). */
        fun fromJson(text: String): SettingsSnapshot? = runCatching {
            val o = JSONObject(text)
            fun strings(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it) }
            }
            SettingsSnapshot(
                favorites = strings("favorites").toSet(),
                favoriteChannels = strings("favoriteChannels").toSet(),
                chatEnabled = o.optBoolean("chatEnabled", true),
                notifyFavorites = o.optBoolean("notifyFavorites", false),
                streamLanguages = strings("streamLanguages").toSet(),
                streamTags = strings("streamTags"),
                updatedAt = o.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }
}

enum class SyncAction { NONE, PUSH, PULL }

/** Побеждает более новая копия (по updatedAt). Нет копии в облаке — загружаем свою. */
fun decideSync(local: SettingsSnapshot, remote: SettingsSnapshot?): SyncAction = when {
    remote == null -> SyncAction.PUSH
    remote.updatedAt > local.updatedAt -> SyncAction.PULL
    remote.updatedAt < local.updatedAt -> SyncAction.PUSH
    else -> SyncAction.NONE
}
