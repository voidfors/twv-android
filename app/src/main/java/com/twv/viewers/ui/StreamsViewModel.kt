package com.twv.viewers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twv.viewers.StreamItem
import com.twv.viewers.StreamsApi
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
    val empty: Boolean = false
)

class StreamsViewModel(private val api: StreamsApi, private val searchDebounceMs: Long = 400L) : ViewModel() {
    private val _ui = MutableStateFlow(StreamsUi())
    val ui: StateFlow<StreamsUi> = _ui.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        job?.cancel()
        job = viewModelScope.launch { delay(searchDebounceMs); load(reset = true) }
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch { load(reset = true) }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.nextCursor == null) return
        job = viewModelScope.launch { load(reset = false) }
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
