package com.twv.viewers

import com.twv.viewers.logic.FavoriteAlerts
import org.junit.Assert.*
import org.junit.Test

class FavoriteAlertsTest {
    private fun v(login: String) = Viewer(
        login = login, displayName = login, channel = "chan", firstSeen = 1, channelSince = 1, lastSeen = 1
    )

    @Test fun notifiesOnlyFavoritesOnceWhenEnabled() {
        val got = ArrayList<String>()
        var enabled = false
        val a = FavoriteAlerts({ setOf("fav") }, { enabled }, { got.add(it.login) })

        a.onNew(v("fav"))           // настройка выключена
        assertTrue(got.isEmpty())

        enabled = true
        a.onNew(v("other"))         // не избранный
        a.onNew(v("FAV"))           // регистр не важен
        a.onNew(v("fav"))           // повтор за сессию
        assertEquals(listOf("FAV"), got)
    }
}
