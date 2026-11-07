package com.twv.viewers.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Доступ к скрытой папке приложения на Drive (scope drive.appdata) через Google Identity.
 * Нужен OAuth-клиент типа Android (пакет + SHA-1 ключа подписи) в Google Cloud.
 */
class GoogleAuth(private val context: Context) {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
        .build()

    private val client get() = Identity.getAuthorizationClient(context)

    suspend fun authorize(): AuthorizationResult = client.authorize(request).await()

    /** Токен без экрана согласия; null — согласие не выдано (нужно войти заново). */
    suspend fun silentToken(): String? {
        val r = authorize()
        return if (r.hasResolution()) null else r.accessToken
    }

    fun fromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }

    private companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}
