package com.twv.viewers

import com.twv.viewers.data.SettingsSnapshot
import com.twv.viewers.data.SyncAction
import com.twv.viewers.data.decideSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSnapshotTest {
    private val a = SettingsSnapshot(
        favorites = setOf("bob", "alice"), favoriteChannels = setOf("streamer"),
        chatEnabled = false, notifyFavorites = true,
        streamLanguages = setOf("ru", "en"), streamTags = listOf("Just Chatting", "IRL"),
        updatedAt = 1000L
    )

    @Test fun jsonRoundTrip() {
        assertEquals(a, SettingsSnapshot.fromJson(a.toJson()))
    }

    @Test fun brokenJsonGivesNull() {
        assertNull(SettingsSnapshot.fromJson("not json"))
    }

    @Test fun missingFieldsUseDefaults() {
        assertEquals(SettingsSnapshot(), SettingsSnapshot.fromJson("{}"))
    }

    @Test fun newerWins() {
        val old = a.copy(updatedAt = 500L)
        assertEquals(SyncAction.PULL, decideSync(old, a))
        assertEquals(SyncAction.PUSH, decideSync(a, old))
        assertEquals(SyncAction.NONE, decideSync(a, a.copy()))
    }

    @Test fun noRemotePushes() {
        assertEquals(SyncAction.PUSH, decideSync(a, null))
    }
}
