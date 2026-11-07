package com.twv.viewers.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Хранилище одного JSON-файла в облаке. */
interface RemoteStore {
    /** Содержимое файла или null, если его ещё нет. */
    suspend fun read(token: String): String?
    suspend fun write(token: String, text: String)
}

/**
 * Файл twv-settings.json в скрытой папке приложения на Google Drive (scope drive.appdata):
 * в обычном Drive его не видно, другие приложения до него не дотянутся.
 */
class DriveAppDataStore(private val http: OkHttpClient) : RemoteStore {
    private val json = "application/json; charset=utf-8".toMediaType()
    @Volatile private var fileId: String? = null

    override suspend fun read(token: String): String? = withContext(Dispatchers.IO) {
        val id = findId(token) ?: return@withContext null
        val req = Request.Builder().url("$FILES/$id?alt=media").auth(token).build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) { fileId = null; return@use null }
            check(r)
            r.body?.string()
        }
    }

    override suspend fun write(token: String, text: String) {
        withContext(Dispatchers.IO) {
            val id = findId(token) ?: create(token)
            val req = Request.Builder().url("$UPLOAD/$id?uploadType=media")
                .auth(token).patch(text.toRequestBody(json)).build()
            http.newCall(req).execute().use { check(it) }
        }
    }

    private fun findId(token: String): String? {
        fileId?.let { return it }
        val url = FILES.toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name='$NAME'")
            .addQueryParameter("fields", "files(id)")
            .build()
        http.newCall(Request.Builder().url(url).auth(token).build()).execute().use { r ->
            check(r)
            val files: JSONArray = JSONObject(r.body?.string().orEmpty()).optJSONArray("files") ?: return null
            return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }?.also { fileId = it }
        }
    }

    private fun create(token: String): String {
        val meta = JSONObject().put("name", NAME).put("parents", JSONArray().put("appDataFolder")).toString()
        val req = Request.Builder().url(FILES).auth(token).post(meta.toRequestBody(json)).build()
        http.newCall(req).execute().use { r ->
            check(r)
            val id = JSONObject(r.body?.string().orEmpty()).getString("id")
            fileId = id
            return id
        }
    }

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")

    private fun check(r: okhttp3.Response) {
        if (r.isSuccessful) return
        if (r.code == 401) fileId = null
        val hint = when (r.code) {
            401 -> "токен отклонён"
            403 -> "нет доступа (включён ли Google Drive API в проекте?)"
            else -> r.message
        }
        throw IOException("HTTP ${r.code}: $hint")
    }

    private companion object {
        const val NAME = "twv-settings.json"
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}
