package com.twv.viewers

import com.twv.viewers.logic.MonthKey
import com.twv.viewers.logic.ViewerFilter
import com.twv.viewers.logic.ViewerPipeline
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class ViewerPipelineTest {
    private fun v(login: String, created: String?, followers: Int) = Viewer(
        login = login, displayName = login, followers = followers, createdAt = created,
        channel = "chan", firstSeen = 1, channelSince = 1, lastSeen = 1
    )

    private val all = listOf(
        v("a", "2024-01-10T10:00:00Z", 5),
        v("b", "2024-01-20T10:00:00Z", 50),
        v("c", "2024-03-05T10:00:00Z", 500),
        v("d", null, 1)
    )

    @Test fun chartIgnoresMonthFilterButListDoesNot() {
        val f = ViewerFilter(month = MonthKey(2024, 1))
        val r = ViewerPipeline.build(all, f, ZoneOffset.UTC)
        assertEquals(listOf("b", "a"), r.shown.map { it.login }) // по фолловерам, убывание
        // График без фильтра месяца: янв=2, фев=0 (заполнен нулём), март=1.
        assertEquals(
            listOf(MonthKey(2024, 1) to 2, MonthKey(2024, 2) to 0, MonthKey(2024, 3) to 1),
            r.chart
        )
    }

    @Test fun noMonthShowsEveryoneAndSearchNarrowsChart() {
        val r = ViewerPipeline.build(all, ViewerFilter(), ZoneOffset.UTC)
        assertEquals(4, r.shown.size)
        val q = ViewerPipeline.build(all, ViewerFilter(query = "c"), ZoneOffset.UTC)
        assertEquals(listOf(MonthKey(2024, 3) to 1), q.chart)
    }
}
