package com.twv.viewers.logic

import com.twv.viewers.Source
import com.twv.viewers.Viewer
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class SortMode { FOLLOWERS, ALPHA, DATE, DURATION, SOURCE, MSGS, LASTMSG }
enum class SortDir { ASC, DESC }

/** Направление по умолчанию, как SORT_DEFAULT_DIR в content.js. */
fun SortMode.defaultDir(): SortDir = if (this == SortMode.ALPHA) SortDir.ASC else SortDir.DESC

data class MonthKey(val year: Int, val month: Int) // month 1..12

data class ViewerFilter(
    val mode: SortMode = SortMode.FOLLOWERS,
    val dir: SortDir = SortMode.FOLLOWERS.defaultDir(),
    val query: String = "",
    val month: MonthKey? = null,
    /** null = все стримеры коллаба. */
    val channel: String? = null,
    val watched: Set<String> = emptySet(),
    val pinWatched: Boolean = true
)

object ViewerSorter {

    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("ru"))

    fun createdTs(v: Viewer): Long =
        v.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    /** Позже обнаружен в канале (channelSince, затем lastSeen) — как isDetectedLater в content.js. */
    private fun isDetectedLater(a: Viewer, b: Viewer): Boolean {
        val fa = if (a.channelSince != 0L) a.channelSince else a.firstSeen
        val fb = if (b.channelSince != 0L) b.channelSince else b.firstSeen
        if (fa != fb) return fa > fb
        return a.lastSeen > b.lastSeen
    }

    /** Один зритель — одна запись (у стримера, где появился последним). */
    fun dedupe(list: Collection<Viewer>): List<Viewer> {
        val best = LinkedHashMap<String, Viewer>()
        for (v in list) {
            val l = v.login.lowercase()
            val cur = best[l]
            if (cur == null || isDetectedLater(v, cur)) best[l] = v
        }
        return best.values.toList()
    }

    private fun compareAscending(mode: SortMode, a: Viewer, b: Viewer): Int = when (mode) {
        SortMode.ALPHA -> collator.compare(a.displayName.ifEmpty { a.login }, b.displayName.ifEmpty { b.login })
        SortMode.DATE -> createdTs(a).compareTo(createdTs(b))
        // Чем позже firstSeen, тем короче время в чате.
        SortMode.DURATION -> b.firstSeen.compareTo(a.firstSeen)
        SortMode.SOURCE -> {
            val diff = (if (a.source == Source.CHAT) 1 else 0) - (if (b.source == Source.CHAT) 1 else 0)
            if (diff != 0) diff else (a.followers ?: -1).compareTo(b.followers ?: -1)
        }
        SortMode.MSGS -> a.msgCount.compareTo(b.msgCount)
        SortMode.LASTMSG -> (a.lastChatAt ?: 0L).compareTo(b.lastChatAt ?: 0L)
        SortMode.FOLLOWERS -> (a.followers ?: -1).compareTo(b.followers ?: -1)
    }

    fun matchesMonth(v: Viewer, key: MonthKey?, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (key == null) return true
        val created = v.createdAt ?: return false
        val inst = runCatching { Instant.parse(created) }.getOrNull() ?: return false
        val d = inst.atZone(zone)
        return d.year == key.year && d.monthValue == key.month
    }

    /** Дедупликация → фильтры (стример, поиск, месяц) → сортировка → закрепление избранных. */
    fun apply(list: Collection<Viewer>, f: ViewerFilter, zone: ZoneId = ZoneId.systemDefault()): List<Viewer> {
        var arr = dedupe(list)
        f.channel?.let { ch -> arr = arr.filter { it.channel.equals(ch, ignoreCase = true) } }
        val q = f.query.trim().lowercase()
        if (q.isNotEmpty()) {
            arr = arr.filter { it.displayName.lowercase().contains(q) || it.login.contains(q) }
        }
        if (f.month != null) arr = arr.filter { matchesMonth(it, f.month, zone) }

        val cmp = Comparator<Viewer> { a, b -> compareAscending(f.mode, a, b) }
        arr = arr.sortedWith(if (f.dir == SortDir.DESC) cmp.reversed() else cmp)

        if (f.pinWatched && f.watched.isNotEmpty()) {
            val (w, rest) = arr.partition { it.login.lowercase() in f.watched }
            arr = w + rest
        }
        return arr
    }

    /** Регистрации по месяцам для графика; месяцы без регистраций между крайними заполняются нулями. */
    fun registrationsByMonth(list: Collection<Viewer>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<MonthKey, Int>> {
        val counts = HashMap<MonthKey, Int>()
        for (v in list) {
            val inst = v.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: continue
            val d = inst.atZone(zone)
            val k = MonthKey(d.year, d.monthValue)
            counts[k] = (counts[k] ?: 0) + 1
        }
        if (counts.isEmpty()) return emptyList()
        val idx = { k: MonthKey -> k.year * 12 + (k.month - 1) }
        val lo = counts.keys.minOf(idx)
        val hi = counts.keys.maxOf(idx)
        return (lo..hi).map { i ->
            val k = MonthKey(i / 12, i % 12 + 1)
            k to (counts[k] ?: 0)
        }
    }
}
