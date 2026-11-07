package com.twv.viewers.data

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Синхронизация настроек и избранных с Google Drive. Работает, пока включена в настройках
 * и выполнен вход: при старте, через 2 с после любого локального изменения и раз в 5 минут.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SyncManager(
    private val settings: SettingsStore,
    private val remote: RemoteStore,
    private val auth: GoogleAuth,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow("")
    /** Результат последней синхронизации для экрана настроек. */
    val status: StateFlow<String> = _status.asStateFlow()

    fun start() {
        scope.launch {
            combine(settings.syncEnabled, settings.syncAccount) { on, acc -> on && acc != null }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) {
                        _status.value = ""
                        return@collectLatest
                    }
                    coroutineScope {
                        launch { settings.snapshots.debounce(2_000).collect { syncOnce() } }
                        launch {
                            while (isActive) {
                                syncOnce()
                                delay(PERIOD_MS)
                            }
                        }
                    }
                }
        }
    }

    fun syncNow() {
        scope.launch { syncOnce() }
    }

    /** Экран согласия Google, если он нужен; null — вход выполнен без экрана (или ошибка в [status]). */
    suspend fun beginSignIn(): PendingIntent? {
        val r = try {
            auth.authorize()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = "Не удалось войти: ${e.message}"
            return null
        }
        if (r.hasResolution()) return r.pendingIntent
        settings.setSyncAccount(r.toGoogleSignInAccount()?.email ?: DEFAULT_ACCOUNT)
        return null
    }

    suspend fun onSignInResult(data: Intent?) {
        try {
            val r = auth.fromIntent(data)
            settings.setSyncAccount(r.toGoogleSignInAccount()?.email ?: DEFAULT_ACCOUNT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = "Вход отменён или не удался: ${e.message}"
        }
    }

    suspend fun signOut() {
        settings.setSyncAccount(null)
        _status.value = ""
    }

    private suspend fun syncOnce() {
        mutex.withLock {
            try {
                _status.value = "Синхронизация…"
                val token = auth.silentToken()
                if (token == null) {
                    _status.value = "Нужно войти в Google заново"
                    return@withLock
                }
                var local = settings.snapshot()
                val text = remote.read(token)
                val cloud = text?.let { SettingsSnapshot.fromJson(it) ?: error("файл в облаке повреждён") }
                when (decideSync(local, cloud)) {
                    SyncAction.PULL -> settings.applySnapshot(cloud!!)
                    SyncAction.PUSH -> {
                        if (local.updatedAt == 0L) { // данные из версии до синхронизации: ставим метку времени
                            local = local.copy(updatedAt = clock())
                            settings.applySnapshot(local)
                        }
                        remote.write(token, local.toJson())
                    }
                    SyncAction.NONE -> {}
                }
                _status.value = "Синхронизировано в " + LocalTime.now().format(TIME)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = "Ошибка синхронизации: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private companion object {
        const val PERIOD_MS = 5 * 60_000L
        const val DEFAULT_ACCOUNT = "Google-аккаунт"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
