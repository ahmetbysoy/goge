package com.example.sync

import android.content.Context
import android.util.Log
import com.example.clipboard.MasterClipboardEngine
import com.example.model.ClipboardItem
import com.example.util.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Drive backup over REST (no heavy Drive SDK).
 *
 * Layout in the user's Google Drive:
 *   My Drive / KalkanKlavye /
 *     clipboard_master.json   ← live snapshot (overwrite on each full backup)
 *     history/kalkan_YYYYMMDD_HHMMSS.json  ← optional timestamped copies
 *
 * Why Drive file, not Sheets:
 * - Unlimited clipboard texts blow past Sheets cell limits.
 * - One JSON file round-trips perfectly with MasterClipboardEngine.
 */
class GoogleDriveBackup private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val accountManager = GoogleAccountManager.getInstance(context)
    private val clipboardEngine = MasterClipboardEngine.getInstance(context)
    private val settingsRepo = SettingsRepository.getInstance(context)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    init {
        // Auto Drive snapshot when signed in + toggle on (no history spam).
        clipboardEngine.addOnNewClipListener { _ ->
            val settings = settingsRepo.settingsFlow.value
            if (settings.autoSyncOnCopy && accountManager.isSignedIn) {
                if (_state.value !is SyncState.Syncing) {
                    backupNow(keepHistory = false)
                }
            }
        }
    }

    fun backupNow(keepHistory: Boolean = true) {
        scope.launch {
            if (!accountManager.isSignedIn) {
                _state.value = SyncState.Error("Önce Google hesabınla giriş yap.")
                return@launch
            }
            try {
                _state.value = SyncState.Syncing
                val token = accountManager.getAccessToken()
                val folderId = ensureAppFolder(token)
                val master = File(clipboardEngine.getMasterJsonFilePath())
                if (!master.exists()) {
                    _state.value = SyncState.Error("Yedeklenecek yerel pano yok.")
                    return@launch
                }
                val json = master.readText(Charsets.UTF_8)
                val fileId = upsertFile(
                    token = token,
                    folderId = folderId,
                    fileName = MASTER_FILE_NAME,
                    mimeType = "application/json",
                    content = json
                )
                if (keepHistory) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val historyFolder = ensureChildFolder(token, folderId, HISTORY_FOLDER_NAME)
                    upsertFile(
                        token = token,
                        folderId = historyFolder,
                        fileName = "kalkan_$stamp.json",
                        mimeType = "application/json",
                        content = json,
                        forceCreate = true
                    )
                }
                settingsRepo.setLastDriveBackup(
                    timeLabel = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()),
                    fileId = fileId
                )
                _state.value = SyncState.Success(
                    "Drive'a yedeklendi (${master.length() / 1024} KB) · ${clipboardEngine.itemsFlow.value.size} kayıt"
                )
            } catch (e: Exception) {
                Log.e(TAG, "backup failed", e)
                _state.value = SyncState.Error(userMessage(e))
            }
        }
    }

    fun restoreNow(merge: Boolean = true) {
        scope.launch {
            if (!accountManager.isSignedIn) {
                _state.value = SyncState.Error("Önce Google hesabınla giriş yap.")
                return@launch
            }
            try {
                _state.value = SyncState.Syncing
                val token = accountManager.getAccessToken()
                val folderId = findAppFolder(token)
                    ?: run {
                        _state.value = SyncState.Error("Drive'da KalkanKlavye klasörü bulunamadı. Önce yedek al.")
                        return@launch
                    }
                val fileId = findFileInFolder(token, folderId, MASTER_FILE_NAME)
                    ?: run {
                        _state.value = SyncState.Error("clipboard_master.json bulunamadı.")
                        return@launch
                    }
                val json = downloadFile(token, fileId)
                val imported = parseClipboardJson(json)
                if (imported.isEmpty()) {
                    _state.value = SyncState.Error("Buluttaki yedek boş veya okunamadı.")
                    return@launch
                }
                val count = clipboardEngine.importItems(imported, merge = merge)
                _state.value = SyncState.Success(
                    if (merge) "Drive'dan birleştirildi: +$count öğe"
                    else "Drive'dan geri yüklendi: $count öğe"
                )
            } catch (e: Exception) {
                Log.e(TAG, "restore failed", e)
                _state.value = SyncState.Error(userMessage(e))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Drive REST helpers
    // -------------------------------------------------------------------------

    private fun ensureAppFolder(token: String): String {
        findAppFolder(token)?.let { return it }
        val meta = JSONObject()
            .put("name", APP_FOLDER_NAME)
            .put("mimeType", "application/vnd.google-apps.folder")
        val body = meta.toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url("$DRIVE/files?fields=id,name")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            return JSONObject(raw).getString("id")
        }
    }

    private fun findAppFolder(token: String): String? {
        val q = "name = '$APP_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = "$DRIVE/files?q=${enc(q)}&spaces=drive&fields=files(id,name)&pageSize=5"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            val files = JSONObject(raw).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).getString("id")
        }
    }

    private fun ensureChildFolder(token: String, parentId: String, name: String): String {
        val q = "name = '$name' and '$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = "$DRIVE/files?q=${enc(q)}&fields=files(id)&pageSize=1"
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            val files = JSONObject(raw).optJSONArray("files")
            if (files != null && files.length() > 0) return files.getJSONObject(0).getString("id")
        }
        val meta = JSONObject()
            .put("name", name)
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", JSONArray().put(parentId))
        val create = Request.Builder()
            .url("$DRIVE/files?fields=id")
            .addHeader("Authorization", "Bearer $token")
            .post(meta.toString().toRequestBody(JSON))
            .build()
        client.newCall(create).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            return JSONObject(raw).getString("id")
        }
    }

    private fun findFileInFolder(token: String, folderId: String, name: String): String? {
        val q = "name = '$name' and '$folderId' in parents and trashed = false"
        val url = "$DRIVE/files?q=${enc(q)}&fields=files(id,name)&pageSize=5"
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            val files = JSONObject(raw).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).getString("id")
        }
    }

    /**
     * Create or update a file by name inside [folderId].
     * Uses multipart upload for create and media PATCH for update.
     */
    private fun upsertFile(
        token: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        content: String,
        forceCreate: Boolean = false
    ): String {
        val existing = if (forceCreate) null else findFileInFolder(token, folderId, fileName)
        if (existing != null) {
            // media upload update
            val body = content.toRequestBody(mimeType.toMediaType())
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existing?uploadType=media")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", mimeType)
                .patch(body)
                .build()
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw HttpException(res.code, raw)
                return existing
            }
        }

        val metadata = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put(folderId))
        val boundary = "kalkan_${System.currentTimeMillis()}"
        val related = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
            append(content)
            append("\r\n--$boundary--")
        }.toRequestBody("multipart/related; boundary=$boundary".toMediaType())

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name")
            .addHeader("Authorization", "Bearer $token")
            .post(related)
            .build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            return JSONObject(raw).getString("id")
        }
    }

    private fun downloadFile(token: String, fileId: String): String {
        val req = Request.Builder()
            .url("$DRIVE/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, raw)
            return raw
        }
    }

    private fun parseClipboardJson(raw: String): List<ClipboardItem> {
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            // maybe wrapped
            val obj = JSONObject(raw)
            obj.optJSONArray("items") ?: JSONArray()
        }
        val out = ArrayList<ClipboardItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("text", "")
            if (text.isBlank()) continue
            out.add(
                ClipboardItem(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    text = text,
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    formattedDate = o.optString("formattedDate", ""),
                    charCount = o.optInt("charCount", text.length),
                    wordCount = o.optInt("wordCount", 0),
                    lineCount = o.optInt("lineCount", text.lines().size),
                    isPinned = o.optBoolean("isPinned", false),
                    sourceApp = o.optString("sourceApp", "Drive Yedek"),
                    isSynced = true
                )
            )
        }
        return out
    }

    private fun userMessage(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            e is HttpException && e.code == 401 ->
                "Oturum düşmüş. Google'dan çıkıp tekrar giriş yap."
            e is HttpException && e.code == 403 ->
                "Drive izni yok. Girişte Drive erişimini onayla."
            msg.contains("Network", true) || msg.contains("Unable to resolve", true) ->
                "İnternet bağlantısı yok."
            else -> "Drive hatası: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())

    class HttpException(val code: Int, val body: String) :
        RuntimeException("HTTP $code: ${body.take(200)}")

    companion object {
        private const val TAG = "GoogleDriveBackup"
        private const val DRIVE = "https://www.googleapis.com/drive/v3"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
        const val APP_FOLDER_NAME = "KalkanKlavye"
        const val HISTORY_FOLDER_NAME = "history"
        const val MASTER_FILE_NAME = "clipboard_master.json"

        @Volatile
        private var instance: GoogleDriveBackup? = null

        fun getInstance(context: Context): GoogleDriveBackup {
            return instance ?: synchronized(this) {
                instance ?: GoogleDriveBackup(context.applicationContext).also { instance = it }
            }
        }
    }
}
