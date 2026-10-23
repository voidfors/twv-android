package com.twv.viewers.logic

import com.twv.viewers.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Порт enqueueMeta / pumpMeta из background.js: двухуровневая очередь (priority + обычная),
 * пачки по META_BATCH_SIZE, один запрос одновременно, один повтор при неудаче,
 * кэш свежее META_FRESH_MS сеть не дёргает.
 */
class MetaQueue(
    private val gql: TwitchGql,
    private val cache: MetaCache,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onMeta: (login: String, meta: Meta) -> Unit,
    private val shouldRetry: (login: String) -> Boolean = { true }
) {
    private val lock = Any()
    private val priority = ArrayDeque<String>()
    private val normal = ArrayDeque<String>()
    private val queued = HashSet<String>()
    private val retried = HashSet<String>()
    private var inFlight = false

    /** cached — то, что уже прочитано из кэша для этого логина (или null). */
    fun enqueue(login: String, urgent: Boolean, cached: CachedMeta?) {
        val hasCache = cached?.meta?.avatarUrl != null
        synchronized(lock) {
            if (login in queued) return
            if (hasCache && clock() - cached!!.updatedAt < META_FRESH_MS) return
            queued.add(login)
            if (urgent) {
                priority.addFirst(login)
            } else if (!hasCache) {
                priority.addLast(login)
            } else {
                normal.addLast(login)
                if (normal.size > META_QUEUE_MAX) queued.remove(normal.removeFirst())
            }
        }
        pump()
    }

    fun pending(): Int = synchronized(lock) { priority.size + normal.size }

    private fun pump() {
        val batch: List<String>
        synchronized(lock) {
            if (inFlight || (priority.isEmpty() && normal.isEmpty())) return
            val b = ArrayList<String>(META_BATCH_SIZE)
            while (b.size < META_BATCH_SIZE && (priority.isNotEmpty() || normal.isNotEmpty())) {
                b.add(if (priority.isNotEmpty()) priority.removeFirst() else normal.removeFirst())
            }
            queued.removeAll(b.toSet())
            inFlight = true
            batch = b
        }
        scope.launch {
            try {
                val metas = gql.fetchMetaBatch(batch)
                val now = clock()
                for (l in batch) {
                    val meta = metas[l]
                    if (meta == null) {
                        // Один повтор, если запрос не удался, а зритель всё ещё без данных.
                        val retry = shouldRetry(l)
                        synchronized(lock) {
                            if (retry && retried.add(l)) { priority.addLast(l); queued.add(l) }
                        }
                        continue
                    }
                    onMeta(l, meta)
                    // Сеть — источник истины: успешный ответ всегда обновляет кэш.
                    cache.put(l, meta, now)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                synchronized(lock) { inFlight = false }
                pump()
            }
        }
    }
}
