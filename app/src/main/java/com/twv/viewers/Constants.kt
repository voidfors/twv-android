package com.twv.viewers

const val CLIENT_ID = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"
const val GQL_URL = "https://gql.twitch.tv/gql"
const val COMMUNITY_TAB_HASH = "92168b4434c8f4d32df14510052131c3544b929723d5f8b69bb96c96207e483e"

// Пауза между полными проходами. В расширении 1500 мс; на телефоне берём больше (батарея/трафик).
const val POLL_INTERVAL_MS = 3000L
const val SAMPLE_CONCURRENCY = 4
const val MISS_PASSES_BEFORE_REMOVE = 2
const val MAX_ZERO_NEW_PASSES = 4
const val META_BATCH_SIZE = 20
const val META_FRESH_MS = 30 * 60 * 1000L
const val META_QUEUE_MAX = 1000
const val CHAT_GRACE_MS = 3 * 60 * 1000L
const val MOVE_GRACE_MS = 10 * 60 * 1000L
const val RECENT_GONE_MAX = 5000

const val CHAT_WS_URL = "wss://irc-ws.chat.twitch.tv:443"
const val CHAT_RECONNECT_MIN_MS = 2000L
const val CHAT_RECONNECT_MAX_MS = 30000L
const val CHAT_SILENCE_MS = 7 * 60 * 1000L

val KNOWN_BOTS: Set<String> = setOf(
    "botrixoficial", "nightbot", "wzbot", "jeetbot", "moobot",
    "streamelements", "streamlabs", "fossabot", "wizebot", "deepbot",
    "ankhbot", "commanderroot", "stay_hydrated_bot", "sery_bot", "own3d",
    "lolrecap", "pretzelrocks", "soundalerts", "guacamole_bot",
    "streamholics", "vivbot", "buttsbot", "kofistreambot", "restreambot",
    "songlistbot", "tipeeebot", "ohbot", "coebot", "botisimo",
    "logviewer", "electricalskateboard", "snusbot"
)
