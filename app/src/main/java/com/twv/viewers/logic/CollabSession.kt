package com.twv.viewers.logic

import com.twv.viewers.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Группа каналов (E4, ручной выбор): по одному [ViewersEngine] на канал.
 * Один канал — обычный режим; два и больше — коллаб: стримеры группы исключаются
 * из зрителей, переходы между каналами не считаются новыми, эхо общего чата отбрасывается.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollabSession(
    private val gql: TwitchGql,
    private val cache: MetaCache,
    private val irc: IrcClient,
    private val scope: CoroutineScope,
    private val chatEnabled: () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val onNewViewer: (Viewer) -> Unit = {}
) {
    private val lock = Any()
    private val engines = LinkedHashMap<String, ViewersEngine>()

    private val _channels = MutableStateFlow<List<String>>(emptyList())
    val channels: StateFlow<List<String>> = _channels.asStateFlow()

    /** Зрители всех каналов группы (дубликаты между каналами убирает ViewerSorter.apply). */
    val viewers: Flow<List<Viewer>> = _channels.flatMapLatest { chs ->
        val flows = synchronized(lock) { chs.mapNotNull { engines[it]?.viewers } }
        if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { arr -> arr.toList().flatten() }
    }

    /** Сумма chatters.count по каналам группы. */
    val total: Flow<Int> = _channels.flatMapLatest { chs ->
        val flows = synchronized(lock) { chs.mapNotNull { engines[it]?.total } }
        if (flows.isEmpty()) flowOf(0) else combine(flows) { arr -> arr.sum() }
    }

    private fun isCollab() = _channels.value.size > 1

    /** @return false, если логин пустой или канал уже в группе. */
    fun addChannel(rawLogin: String): Boolean {
        val login = rawLogin.trim().removePrefix("@").lowercase()
        if (login.isEmpty() || !LOGIN_RE.matches(login)) return false
        val engine: ViewersEngine
        synchronized(lock) {
            if (engines.containsKey(login)) return false
            engine = ViewersEngine(
                login = login, gql = gql, cache = cache, scope = scope, clock = clock,
                groupStreamers = { if (isCollab()) _channels.value.toSet() else emptySet() },
                siblings = { synchronized(lock) { engines.values.toList() } },
                pollIntervalMs = pollIntervalMs,
                onNewViewer = onNewViewer
            )
            engines[login] = engine
            _channels.value = _channels.value + login
        }
        engine.start()
        if (chatEnabled()) engine.startChat(irc)
        // Новый стример группы мог уже лежать в списках соседей как обычный зритель.
        synchronized(lock) { engines.values.toList() }.forEach { it.purgeGroupStreamers() }
        return true
    }

    fun removeChannel(login: String) {
        val l = login.lowercase()
        val engine = synchronized(lock) {
            val e = engines.remove(l) ?: return
            _channels.value = _channels.value - l
            e
        }
        engine.stop()
    }

    /** Включить/выключить чат у всех каналов (настройка). */
    fun setChat(enabled: Boolean) {
        val list = synchronized(lock) { engines.values.toList() }
        list.forEach { if (enabled) it.startChat(irc) else it.stopChat() }
    }

    fun stop() {
        val list = synchronized(lock) { engines.values.toList().also { engines.clear() } }
        _channels.value = emptyList()
        list.forEach { it.stop() }
    }

    private companion object {
        // Логин Twitch: 4–25 символов, буквы, цифры, подчёркивание. Нижнюю границу не проверяем.
        val LOGIN_RE = Regex("^[a-z0-9_]{1,25}$")
    }
}
