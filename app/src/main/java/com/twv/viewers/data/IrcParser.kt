package com.twv.viewers.data

import com.twv.viewers.ChatMessage

data class IrcLine(
    val tags: Map<String, String>,
    val prefix: String,
    val command: String,
    val rest: String
)

/** Порт parseIrcLine из background.js. */
fun parseIrcLine(line: String): IrcLine? {
    var rest = line
    val tags = HashMap<String, String>()
    if (rest.startsWith("@")) {
        val sp = rest.indexOf(' ')
        if (sp == -1) return null
        rest.substring(1, sp).split(';').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) tags[kv.substring(0, i)] = kv.substring(i + 1)
        }
        rest = rest.substring(sp + 1)
    }
    var prefix = ""
    if (rest.startsWith(":")) {
        val sp = rest.indexOf(' ')
        if (sp == -1) return null
        prefix = rest.substring(1, sp)
        rest = rest.substring(sp + 1)
    }
    val sp = rest.indexOf(' ')
    val command = if (sp == -1) rest else rest.substring(0, sp)
    return IrcLine(tags, prefix, command, rest)
}

/** PRIVMSG -> ChatMessage, иначе null. */
fun IrcLine.toChatMessage(): ChatMessage? {
    if (command != "PRIVMSG") return null
    val nick = prefix.substringBefore('!')
    if (nick.isEmpty()) return null
    return ChatMessage(
        nick = nick,
        displayName = tags["display-name"]?.ifEmpty { null },
        roomId = tags["room-id"]?.ifEmpty { null },
        sourceRoomId = tags["source-room-id"]?.ifEmpty { null }
    )
}
