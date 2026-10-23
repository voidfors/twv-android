package com.twv.viewers

import com.twv.viewers.logic.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class ViewerSorterTest {
    private fun v(
        login: String, followers: Int? = null, created: String? = null, channel: String = "a",
        firstSeen: Long = 0, channelSince: Long = 0, lastSeen: Long = 0, msgs: Int = 0,
        lastChat: Long? = null, source: Source = Source.LIST, name: String = login
    ) = Viewer(
        login = login, displayName = name, followers = followers, createdAt = created, source = source,
        channel = channel, firstSeen = firstSeen, channelSince = channelSince, lastSeen = lastSeen,
        lastChatAt = lastChat, msgCount = msgs
    )

    private val utc = ZoneOffset.UTC

    @Test fun followersDescendingByDefaultAndNullsLast() {
        val f = ViewerFilter()
        val r = ViewerSorter.apply(listOf(v("x"), v("b", 5), v("c", 50)), f, utc)
        assertEquals(listOf("c", "b", "x"), r.map { it.login })
    }

    @Test fun alphaAscendingDefault() {
        assertEquals(SortDir.ASC, SortMode.ALPHA.defaultDir())
        val f = ViewerFilter(SortMode.ALPHA, SortDir.ASC)
        val r = ViewerSorter.apply(listOf(v("b"), v("a"), v("c")), f, utc)
        assertEquals(listOf("a", "b", "c"), r.map { it.login })
    }

    @Test fun durationDescendingPutsOldestFirstSeenFirst() {
        val f = ViewerFilter(SortMode.DURATION, SortDir.DESC)
        val r = ViewerSorter.apply(listOf(v("new", firstSeen = 900), v("old", firstSeen = 100)), f, utc)
        assertEquals(listOf("old", "new"), r.map { it.login })
    }

    @Test fun lastMsgWithoutMessagesGoesLastWhenDescending() {
        val f = ViewerFilter(SortMode.LASTMSG, SortDir.DESC)
        val r = ViewerSorter.apply(listOf(v("silent"), v("talker", lastChat = 10)), f, utc)
        assertEquals(listOf("talker", "silent"), r.map { it.login })
    }

    @Test fun dedupeKeepsChannelWhereDetectedLater() {
        val a = v("u", channel = "a", channelSince = 100)
        val b = v("u", channel = "b", channelSince = 200)
        val r = ViewerSorter.dedupe(listOf(a, b))
        assertEquals(1, r.size)
        assertEquals("b", r[0].channel)
    }

    @Test fun channelFilterAndSearch() {
        val list = listOf(v("alice", channel = "a"), v("bob", channel = "b"), v("alina", channel = "b"))
        val r = ViewerSorter.apply(list, ViewerFilter(channel = "b", query = "ali"), utc)
        assertEquals(listOf("alina"), r.map { it.login })
    }

    @Test fun monthFilter() {
        val list = listOf(
            v("jan", created = "2020-01-15T10:00:00Z"),
            v("feb", created = "2020-02-01T00:00:00Z"),
            v("none")
        )
        val r = ViewerSorter.apply(list, ViewerFilter(month = MonthKey(2020, 2)), utc)
        assertEquals(listOf("feb"), r.map { it.login })
    }

    @Test fun watchedPinnedOnTop() {
        val list = listOf(v("big", 100), v("small", 1))
        val r = ViewerSorter.apply(list, ViewerFilter(watched = setOf("small")), utc)
        assertEquals(listOf("small", "big"), r.map { it.login })
    }

    @Test fun registrationsZeroFilled() {
        val list = listOf(v("a", created = "2020-01-10T00:00:00Z"), v("b", created = "2020-03-10T00:00:00Z"))
        val r = ViewerSorter.registrationsByMonth(list, utc)
        assertEquals(listOf(MonthKey(2020, 1) to 1, MonthKey(2020, 2) to 0, MonthKey(2020, 3) to 1), r)
    }
}
