package com.twv.viewers

import com.twv.viewers.data.parseIrcLine
import com.twv.viewers.data.toChatMessage
import org.junit.Assert.*
import org.junit.Test

class IrcParserTest {
    @Test fun parsesPrivmsgWithTags() {
        val line = "@display-name=Vasya;room-id=111;source-room-id=222 :vasya!vasya@vasya.tmi.twitch.tv PRIVMSG #chan :hello"
        val m = parseIrcLine(line)!!
        assertEquals("PRIVMSG", m.command)
        assertEquals("vasya!vasya@vasya.tmi.twitch.tv", m.prefix)
        val msg = m.toChatMessage()!!
        assertEquals("vasya", msg.nick)
        assertEquals("Vasya", msg.displayName)
        assertEquals("111", msg.roomId)
        assertEquals("222", msg.sourceRoomId)
    }

    @Test fun parsesCommandWithoutTagsAndPrefix() {
        val m = parseIrcLine("RECONNECT")!!
        assertEquals("RECONNECT", m.command)
        assertNull(m.toChatMessage())
    }

    @Test fun rejectsBrokenLines() {
        assertNull(parseIrcLine("@only-tags"))
        assertNull(parseIrcLine(":onlyprefix"))
    }

    @Test fun emptyTagValuesBecomeNull() {
        val msg = parseIrcLine("@display-name=;room-id=5 :a!a@a PRIVMSG #c :x")!!.toChatMessage()!!
        assertNull(msg.displayName)
        assertNull(msg.sourceRoomId)
        assertEquals("5", msg.roomId)
    }
}
