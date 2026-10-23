package com.twv.viewers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twv.viewers.AppContainer
import com.twv.viewers.Viewer
import com.twv.viewers.logic.CollabSession
import com.twv.viewers.logic.FavoriteAlerts
import com.twv.viewers.logic.MonthKey
import com.twv.viewers.logic.SortDir
import com.twv.viewers.logic.SortMode
import com.twv.viewers.logic.ViewerFilter
import com.twv.viewers.logic.ViewerPipeline
import com.twv.viewers.logic.defaultDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewersUi(
    val shown: List<Viewer> = emptyList(),
    val filter: ViewerFilter = ViewerFilter(),
    val channels: List<String> = emptyList(),
    val total: Int = 0,
    val loadedCount: Int = 0,
    val chart: List<Pair<MonthKey, Int>> = emptyList()
)

class ViewersViewModel(private val c: AppContainer, firstChannel: String) : ViewModel() {

    @Volatile private var chatOn = true
    @Volatile private var notifyOn = false

    private val alerts = FavoriteAlerts(
        favorites = { favorites.value }, enabled = { notifyOn }, sink = c.notifier::notify
    )

    private val session = CollabSession(
        gql = c.gql, cache = c.metaCache, irc = c.irc, scope = viewModelScope,
        chatEnabled = { chatOn }, onNewViewer = alerts::onNew
    )
    private val filter = MutableStateFlow(ViewerFilter())

    val chatEnabled: StateFlow<Boolean> = c.settings.chatEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val favorites: StateFlow<Set<String>> = c.settings.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val ui: StateFlow<ViewersUi> = combine(
        session.viewers, filter, favorites, session.channels, session.total
    ) { all, f, fav, chs, total ->
        val effective = f.copy(watched = fav)
        val view = ViewerPipeline.build(all, effective)
        ViewersUi(
            shown = view.shown,
            chart = view.chart,
            filter = effective,
            channels = chs,
            total = total,
            loadedCount = all.size
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewersUi())

    init {
        viewModelScope.launch {
            chatOn = c.settings.chatEnabled.first()
            session.addChannel(firstChannel)
            c.settings.pushRecentChannel(firstChannel)
        }
        viewModelScope.launch { c.settings.notifyFavorites.collect { notifyOn = it } }
        viewModelScope.launch {
            c.settings.chatEnabled.collect { on -> chatOn = on; session.setChat(on) }
        }
    }

    fun setSort(mode: SortMode) = filter.update {
        // Повторный тап по активной сортировке меняет направление.
        if (it.mode == mode) it.copy(dir = if (it.dir == SortDir.ASC) SortDir.DESC else SortDir.ASC)
        else it.copy(mode = mode, dir = mode.defaultDir())
    }

    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setMonth(m: MonthKey?) = filter.update { it.copy(month = m) }
    fun setChannelFilter(login: String?) = filter.update { it.copy(channel = login) }

    fun addChannel(login: String): Boolean = session.addChannel(login)

    fun removeChannel(login: String) {
        session.removeChannel(login)
        filter.update { if (it.channel.equals(login, true)) it.copy(channel = null) else it }
    }

    fun toggleFavorite(login: String) {
        viewModelScope.launch { c.settings.toggleFavorite(login) }
    }

    fun setChatEnabled(on: Boolean) {
        viewModelScope.launch { c.settings.setChatEnabled(on) }
    }

    override fun onCleared() {
        session.stop()
    }
}
