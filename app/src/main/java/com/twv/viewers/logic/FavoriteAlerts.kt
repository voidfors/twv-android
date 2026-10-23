package com.twv.viewers.logic

import com.twv.viewers.Viewer
import java.util.Collections

/**
 * Решает, уведомлять ли об новом зрителе: только избранные, только при включённой настройке,
 * и не больше одного раза за сессию на логин (чтобы возвраты в чат не засыпали уведомлениями).
 */
class FavoriteAlerts(
    private val favorites: () -> Set<String>,
    private val enabled: () -> Boolean,
    private val sink: (Viewer) -> Unit
) {
    private val alerted: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun onNew(v: Viewer) {
        if (!enabled()) return
        val l = v.login.lowercase()
        if (l !in favorites()) return
        if (!alerted.add(l)) return
        sink(v)
    }
}
