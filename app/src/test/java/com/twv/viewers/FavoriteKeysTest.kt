package com.twv.viewers

import com.twv.viewers.data.FavoriteKeys
import org.junit.Assert.*
import org.junit.Test

class FavoriteKeysTest {
    @Test fun plainLoginsAreLowercased() {
        assertEquals(setOf("viewer1"), FavoriteKeys.logins(setOf("Viewer1", " viewer1 ")))
    }

    @Test fun oldPerChannelEntriesAreMigratedToSharedLogins() {
        val entries = setOf("alpha:Viewer1", "beta:viewer2", "gamma:viewer1", "legacy_login")
        assertEquals(setOf("viewer1", "viewer2", "legacy_login"), FavoriteKeys.logins(entries))
    }

    @Test fun blankEntriesAreDropped() {
        assertTrue(FavoriteKeys.logins(setOf("", "alpha:", "  ")).isEmpty())
    }
}
