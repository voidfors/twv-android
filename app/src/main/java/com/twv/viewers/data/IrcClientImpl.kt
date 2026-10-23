package com.twv.viewers.data

import com.twv.viewers.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicLong

/**
 * Порт startChat / armChatWatchdog / scheduleChatReconnect из background.js:
 * анонимный read-only IRC, PING/PONG, RECONNECT, watchdog на тишину, backoff 2..30 с.
 */
class IrcClientImpl(
    private val http: OkHttpClient = OkHttpClient(),
    private val url: String = CHAT_WS_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    private val silenceMs: Long = CHAT_SILENCE_MS,
    private val watchdogCheckMs: Long = 60_000L
) : IrcClient {

    override fun messages(login: String): Flow<ChatMessage> = channelFlow {
        val channel = login.lowercase()
        val backoff = AtomicLong(CHAT_RECONNECT_MIN_MS)

        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            val lastRx = AtomicLong(clock())

            val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoff.set(CHAT_RECONNECT_MIN_MS)
                    lastRx.set(clock())
                    webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                    webSocket.send("PASS SCHMOOPIIE")
                    webSocket.send("NICK justinfan" + (10000 + (Math.random() * 89999).toInt()))
                    webSocket.send("JOIN #$channel")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastRx.set(clock())
                    for (line in text.split("\r\n")) {
                        if (line.isEmpty()) continue
                        if (line.startsWith("PING")) {
                            webSocket.send("PONG :tmi.twitch.tv"); continue
                        }
                        val m = parseIrcLine(line) ?: continue
                        when (m.command) {
                            "PRIVMSG" -> m.toChatMessage()?.let { trySend(it) }
                            "RECONNECT" -> webSocket.close(1000, null)
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason); closed.complete(Unit)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
            })

            val watchdog = launch {
                while (true) {
                    delay(watchdogCheckMs)
                    if (clock() - lastRx.get() >= silenceMs) {
                        ws.cancel(); closed.complete(Unit); break
                    }
                }
            }

            try {
                closed.await()
            } finally {
                watchdog.cancel()
                ws.cancel()
            }
            if (!isActive) break
            val d = backoff.get()
            backoff.set(minOf(d * 2, CHAT_RECONNECT_MAX_MS))
            delay(d)
        }
        awaitClose { }
    }
}
