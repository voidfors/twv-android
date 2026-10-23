package com.twv.viewers.logic

import com.twv.viewers.Viewer
import java.time.ZoneId

/** Результат для экрана: список с учётом всех фильтров и график регистраций. */
data class ViewerView(
    val shown: List<Viewer>,
    /** График строится по списку без фильтра месяца, чтобы по столбцу можно было выбрать месяц. */
    val chart: List<Pair<MonthKey, Int>>
)

object ViewerPipeline {
    fun build(all: Collection<Viewer>, f: ViewerFilter, zone: ZoneId = ZoneId.systemDefault()): ViewerView {
        val base = ViewerSorter.apply(all, f.copy(month = null), zone)
        val chart = ViewerSorter.registrationsByMonth(base, zone)
        val shown = if (f.month == null) base else base.filter { ViewerSorter.matchesMonth(it, f.month, zone) }
        return ViewerView(shown, chart)
    }
}
