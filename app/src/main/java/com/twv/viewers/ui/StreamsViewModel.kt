package com.twv.viewers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twv.viewers.StreamItem
import com.twv.viewers.StreamsApi
import com.twv.viewers.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StreamsUi(
    val items: List<StreamItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    /** Текст ошибки запроса; остальное приложение при этом работает. */
    val error: String? = null,
    val nextCursor: String? = null,
    val empty: Boolean = false,
    /** Логины избранных каналов. */
    val favorites: Set<String> = emptySet(),
    /** Избранные, которые сейчас в эфире (по зрителям, больше — выше). */
    val liveFavorites: List<StreamItem> = emptyList()
)

class StreamsViewModel(
    private val api: StreamsApi,
    private val settings: SettingsStore,
    private val searchDebounceMs: Long = 400L
) : ViewModel() {
    private val _ui = MutableStateFlow(StreamsUi())
    val ui: StateFlow<StreamsUi> = _ui.asStateFlow()
    private var job: Job? = null
    private var favJob: Job? = null

    init {
        reload()
        viewModelScope.launch {
            settings.favoriteChannels.collect { favs ->
                _ui.update { it.copy(favorites = favs) }
                refreshFavorites()
            }
        }
    }

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        job?.cancel()
        job = viewModelScope.launch { delay(searchDebounceMs); load(reset = true) }
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch { load(reset = true) }
        refreshFavorites()
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.nextCursor == null) return
        job = viewModelScope.launch { load(reset = false) }
    }

    fun toggleFavorite(login: String) {
        viewModelScope.launch { settings.toggleFavoriteChannel(login) }
    }

    /** Обновить, кто из избранных в эфире. Ошибка сети не стирает прошлый результат. */
    fun refreshFavorites() {
        favJob?.cancel()
        val favs = _ui.value.favorites
        if (favs.isEmpty()) {
            _ui.update { it.copy(liveFavorites = emptyList()) }
            return
        }
        favJob = viewModelScope.launch {
            try {
                val live = api.fetchLiveChannels(favs.toList())
                _ui.update { it.copy(liveFavorites = live.sortedByDescending { s -> s.viewers }) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // оставляем то, что уже показано
            }
        }
    }

    private suspend fun load(reset: Boolean) {
        val before = _ui.value
        _ui.update { it.copy(loading = true, error = null) }
        try {
            val page = api.fetchStreams(if (reset) null else before.nextCursor, before.query)
            _ui.update {
                val merged = if (reset) page.items else (it.items + page.items).distinctBy { s -> s.login }
                it.copy(items = merged, nextCursor = page.nextCursor, loading = false, empty = merged.isEmpty())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    error = "Не удалось загрузить стримы: ${e.message ?: "неизвестная ошибка"}. " +
                        "Можно ввести логин канала вручную."
                )
            }
        }
    }
}
