package com.twv.viewers.logic

import com.twv.viewers.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Движок одного канала: порт collectFullPass / mergeSample / handleChatMessage /
 * rememberGone / findInSiblings из background.js.
 *
 * Для коллаба каждому каналу группы создаётся свой движок; связь между ними —
 * через [siblings] и [groupStreamers].
 */
class ViewersEngine(
    login: String,
    private val gql: TwitchGql,
    private val cache: MetaCache,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Логины всех стримеров группы (не зрители). */
    private val groupStreamers: () -> Set<String> = { emptySet() },
    /** Движки остальных каналов группы. */
    private val siblings: () -> List<ViewersEngine> = { emptyList() },
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    /** Вызывается, когда реально новый зритель появился после первого скана (для избранных). */
    private val onNewViewer: (Viewer) -> Unit = {}
) {
    val channel: String = login.lowercase()

    private val lock = Any()
    private val map = LinkedHashMap<String, Viewer>()
    private data class Gone(val firstSeen: Long, val ts: Long)
    private val recentGone = HashMap<String, Gone>()

    @Volatile private var initialScanDone = false
    @Volatile private var botLogins: Set<String> = emptySet()
    @Volatile var roomId: String? = null
        private set

    private val _viewers = MutableStateFlow<List<Viewer>>(emptyList())
    val viewers: StateFlow<List<Viewer>> = _viewers.asStateFlow()

    private val _total = MutableStateFlow(0)
    /** chatters.count из последнего ответа Twitch. */
    val total: StateFlow<Int> = _total.asStateFlow()

    private val collecting = AtomicBoolean(false)
    private var pollJob: Job? = null
    private var chatJob: Job? = null

    private val metaQueue = MetaQueue(
        gql = gql, cache = cache, scope = scope, clock = clock,
        onMeta = ::applyMeta,
        shouldRetry = { l -> synchronized(lock) { map[l]?.let { it.avatarUrl == null } == true } }
    )

    // ---------- жизненный цикл ----------

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                try {
                    collectFullPass()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                delay(pollIntervalMs)
            }
        }
    }

    /** Подключить чат (IRC). Вызывать отдельно, чтобы чат можно было включать/выключать в настройках. */
    fun startChat(irc: IrcClient) {
        chatJob?.cancel()
        chatJob = scope.launch {
            irc.messages(channel).catch { }.collect { onChat(it) }
        }
    }

    fun stopChat() { chatJob?.cancel(); chatJob = null }

    fun stop() {
        pollJob?.cancel(); pollJob = null
        stopChat()
    }

    // ---------- коллаб ----------

    private fun isGroupStreamer(l: String) = l in groupStreamers()

    /** firstSeen зрителя у этого канала (в списке или недавно пропавшего), иначе null. */
    internal fun findFirstSeen(l: String): Long? {
        val now = clock()
        return synchronized(lock) {
            val v = map[l]
            if (v != null) return@synchronized v.firstSeen
            val g = recentGone[l] ?: return@synchronized null
            if (now - g.ts < MOVE_GRACE_MS) {
                g.firstSeen
            } else {
                recentGone.remove(l)
                null
            }
        }
    }

    private fun findInSiblings(l: String): Long? {
        for (s in siblings()) {
            if (s === this) continue
            s.findFirstSeen(l)?.let { return it }
        }
        return null
    }

    private fun rememberGone(l: String, v: Viewer) {
        if (groupStreamers().isEmpty()) return
        val now = clock()
        recentGone[l] = Gone(v.firstSeen.takeIf { it != 0L } ?: now, now)
        if (recentGone.size > RECENT_GONE_MAX) {
            recentGone.entries.removeAll { now - it.value.ts > MOVE_GRACE_MS }
        }
    }

    /** Стример группы мог попасть в список до того, как узнали состав группы. */
    fun purgeGroupStreamers() {
        val g = groupStreamers()
        if (g.isEmpty()) return
        val changed = synchronized(lock) { map.keys.removeAll(g) }
        if (changed) publish()
    }

    // ---------- метаданные ----------

    private fun applyMeta(l: String, m: Meta) {
        val changed = synchronized(lock) {
            val v = map[l] ?: return@synchronized false
            map[l] = v.copy(
                displayName = m.displayName, avatarUrl = m.avatarUrl,
                createdAt = m.createdAt, followers = m.followers
            )
            true
        }
        if (changed) publish()
    }

    // ---------- полный проход ----------

    private fun publish() {
        // TODO: на больших каналах (тысячи зрителей) добавить троттлинг публикации.
        _viewers.value = synchronized(lock) { map.values.toList() }
    }

    private suspend fun mergeSample(logins: List<String>, seen: MutableSet<String>, excluded: MutableSet<String>, now: Long) {
        val group = groupStreamers()
        for (raw in logins) {
            val l = raw.lowercase()
            if (l in group) { excluded.add(l); continue }
            seen.add(l)
            val existing = synchronized(lock) { map[l] }
            if (existing == null) {
                val cached = cache.get(l)
                val prevFirstSeen = findInSiblings(l)
                val fresh = Viewer(
                    login = l,
                    displayName = cached?.meta?.displayName ?: l,
                    avatarUrl = cached?.meta?.avatarUrl,
                    followers = cached?.meta?.followers,
                    createdAt = cached?.meta?.createdAt,
                    source = Source.LIST,
                    channel = channel,
                    firstSeen = prevFirstSeen ?: now,
                    channelSince = now,
                    lastSeen = now,
                    moved = prevFirstSeen != null,
                    // Найден до конца первого скана — «уже был в чате», а не «зашёл».
                    initialScan = !initialScanDone
                )
                val added = synchronized(lock) {
                    if (map.containsKey(l)) false else { map[l] = fresh; true }
                }
                if (added) {
                    if (initialScanDone && prevFirstSeen == null) onNewViewer(fresh)
                    metaQueue.enqueue(l, urgent = false, cached = cached)
                }
            } else {
                synchronized(lock) {
                    val cur = map[l] ?: return@synchronized
                    map[l] = if (cur.source != Source.LIST || cur.missedPasses != 0)
                        cur.copy(lastSeen = now, source = Source.LIST, missedPasses = 0)
                    else cur.copy(lastSeen = now)
                }
            }
        }
    }

    suspend fun collectFullPass() {
        if (!collecting.compareAndSet(false, true)) return
        try {
            val seen = HashSet<String>()
            val excluded = HashSet<String>()
            val bots = HashSet<String>(KNOWN_BOTS)
            var totalCount = 0

            suspend fun absorb(s: ChatterSample) {
                if (s.count > 0) totalCount = s.count
                excluded.addAll(s.excluded)
                s.bots.forEach { bots.add(it.lowercase()) }
                mergeSample(s.logins, seen, excluded, clock())
                publish()
            }

            val first = gql.fetchChattersSample(channel)
            if (first.count > 0) totalCount = first.count
            absorb(first)
            if (seen.isEmpty() && totalCount == 0) return // пустой ответ — список не трогаем

            var zeroNew = 0
            while (true) {
                val before = seen.size
                val samples = coroutineScope {
                    (1..SAMPLE_CONCURRENCY).map { async { gql.fetchChattersSample(channel) } }.awaitAll()
                }
                samples.forEach { absorb(it) }

                zeroNew = if (seen.size - before == 0) zeroNew + 1 else 0
                if (totalCount > 0 && seen.size + excluded.size >= totalCount) break
                if (zeroNew >= MAX_ZERO_NEW_PASSES) break
            }
            if (seen.isEmpty()) return // проход без логинов — удаление пропускаем

            botLogins = bots
            _total.value = totalCount
            sweep(seen, bots)
            initialScanDone = true
            publish()
        } finally {
            collecting.set(false)
        }
    }

    /** Удаление ботов и «ушедших»: 1-й пропуск — затемнение, 2-й — удаление; недавно писавших не трогаем. */
    private fun sweep(seen: Set<String>, bots: Set<String>) {
        val now = clock()
        synchronized(lock) {
            for (l in map.keys.toList()) {
                val v = map[l] ?: continue
                if (l in bots) { map.remove(l); continue }
                if (l in seen) {
                    if (v.missedPasses != 0) map[l] = v.copy(missedPasses = 0)
                    continue
                }
                val lastChat = v.lastChatAt
                if (lastChat != null && now - lastChat < CHAT_GRACE_MS) {
                    if (v.missedPasses != 0 || v.source != Source.CHAT) {
                        map[l] = v.copy(missedPasses = 0, source = Source.CHAT)
                    }
                    continue
                }
                val missed = v.missedPasses + 1
                if (missed >= MISS_PASSES_BEFORE_REMOVE) {
                    map.remove(l)
                    rememberGone(l, v)
                } else {
                    map[l] = v.copy(missedPasses = missed)
                }
            }
        }
    }

    // ---------- чат ----------

    /**
     * Общий чат (Shared Chat): сообщение приходит во все подключённые чаты с тегами
     * room-id (этот канал) и source-room-id (откуда написано). Для чужого канала это «эхо».
     */
    private fun isSharedChatEcho(m: ChatMessage): Boolean {
        val room = m.roomId
        val src = m.sourceRoomId
        if (room != null && roomId == null) roomId = room
        if (src == null || room == null || src == room) return false
        val sibs = siblings().filter { it !== this }
        if (sibs.isEmpty()) return false
        // roomId соседа ещё неизвестен — он тоже получит сообщение и сам определит себя.
        return sibs.any { it.roomId == null || it.roomId == src }
    }

    suspend fun onChat(m: ChatMessage) {
        if (isSharedChatEcho(m)) return
        val l = m.nick.lowercase()
        if (l.isEmpty() || l == channel) return
        if (l in KNOWN_BOTS || l in botLogins) return
        if (isGroupStreamer(l)) return

        val now = clock()
        val updated = synchronized(lock) {
            val cur = map[l] ?: return@synchronized false
            map[l] = cur.copy(
                lastSeen = now, lastChatAt = now, msgCount = cur.msgCount + 1, missedPasses = 0
            )
            true
        }
        if (updated) { publish(); return }

        val cached = cache.get(l)
        val prevFirstSeen = findInSiblings(l)
        val v = Viewer(
            login = l,
            displayName = cached?.meta?.displayName ?: m.displayName ?: l,
            avatarUrl = cached?.meta?.avatarUrl,
            followers = cached?.meta?.followers,
            createdAt = cached?.meta?.createdAt,
            source = Source.CHAT,
            channel = channel,
            firstSeen = prevFirstSeen ?: now,
            channelSince = now,
            lastSeen = now,
            lastChatAt = now,
            msgCount = 1,
            moved = prevFirstSeen != null
        )
        val added = synchronized(lock) {
            if (map.containsKey(l)) false else { map[l] = v; true }
        }
        if (!added) return
        if (prevFirstSeen == null) onNewViewer(v)
        metaQueue.enqueue(l, urgent = true, cached = cached)
        publish()
    }
}
