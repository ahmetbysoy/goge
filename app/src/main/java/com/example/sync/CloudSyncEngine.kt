package com.example.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val error: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
}

class CloudSyncEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val clipboardEngine = MasterClipboardEngine.getInstance(context)
    private val settingsRepo = SettingsRepository.getInstance(context)

    init {
        // Register instant push trigger whenever a new clip is added
        clipboardEngine.onNewClipAddedListener = { item ->
            val settings = settingsRepo.settingsFlow.value
            if (settings.autoSyncOnCopy && settings.cloudSyncUrl.isNotBlank()) {
                pushSingleClip(item, settings.cloudSyncUrl, settings.cloudSyncSecret)
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Push a single clip to the configured endpoint immediately.
     */
    fun pushSingleClip(item: ClipboardItem, targetUrl: String, authToken: String = "") {
        if (targetUrl.isBlank()) return

        scope.launch {
            if (!isNetworkAvailable()) {
                _syncState.value = SyncState.Error("İnternet bağlantısı yok. Yerel kuyruğa alındı.")
                return@launch
            }

            try {
                _syncState.value = SyncState.Syncing

                val payload = JSONObject().apply {
                    put("event", "CLIP_COPIED")
                    put("id", item.id)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    put("formattedDate", item.formattedDate)
                    put("charCount", item.charCount)
                    put("lineCount", item.lineCount)
                    put("isPinned", item.isPinned)
                    put("sourceApp", item.sourceApp)
                }

                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .post(body)
                    .header("User-Agent", "Kalkan-Keyboard-Sync/1.0")

                if (authToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $authToken")
                    requestBuilder.header("X-Kalkan-Auth", authToken)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        clipboardEngine.markSynced(item.id)
                        _syncState.value = SyncState.Success("Öğe buluta aktarıldı (${item.charCount} karakter)")
                        Log.d(TAG, "Sync success for clip ${item.id}")
                    } else {
                        _syncState.value = SyncState.Error("Bulut sunucu hatası: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Push single clip failed", e)
                _syncState.value = SyncState.Error("Senkron hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}")
            }
        }
    }

    /**
     * Push complete master JSON dump file to cloud endpoint.
     */
    fun pushFullBackup(targetUrl: String, authToken: String = "") {
        if (targetUrl.isBlank()) {
            _syncState.value = SyncState.Error("Bulut Senkronizasyon URL'si girilmedi!")
            return
        }

        scope.launch {
            if (!isNetworkAvailable()) {
                _syncState.value = SyncState.Error("İnternet bağlantısı yok!")
                return@launch
            }

            try {
                _syncState.value = SyncState.Syncing
                val masterJsonFile = File(clipboardEngine.getMasterJsonFilePath())
                if (!masterJsonFile.exists()) {
                    _syncState.value = SyncState.Error("Yedeklenecek yerel veri bulunamadı!")
                    return@launch
                }

                val jsonContent = masterJsonFile.readText(Charsets.UTF_8)
                val body = jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .post(body)
                    .header("User-Agent", "Kalkan-Keyboard-Sync/1.0")
                    .header("X-Kalkan-Sync-Type", "FULL_BACKUP")

                if (authToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $authToken")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        _syncState.value = SyncState.Success("Tüm pano veritabanı başarıyla yedeklendi! (${masterJsonFile.length() / 1024} KB)")
                    } else {
                        _syncState.value = SyncState.Error("Yedekleme hatası: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Push full backup failed", e)
                _syncState.value = SyncState.Error("Hata: ${e.localizedMessage ?: "Bağlantı kesildi"}")
            }
        }
    }

    /**
     * Test ping to the sync server.
     */
    fun testPing(targetUrl: String, authToken: String = "", onResult: (Boolean, String) -> Unit) {
        scope.launch {
            if (targetUrl.isBlank()) {
                onResult(false, "URL boş olamaz!")
                return@launch
            }
            try {
                val pingJson = JSONObject().apply {
                    put("event", "PING")
                    put("source", "KalkanKeyboard")
                    put("timestamp", System.currentTimeMillis())
                }
                val body = pingJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(targetUrl)
                    .post(body)
                    .apply {
                        if (authToken.isNotBlank()) {
                            header("Authorization", "Bearer $authToken")
                        }
                    }
                    .build()

                client.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        onResult(true, "Bağlantı Başarılı! (HTTP ${res.code})")
                    } else {
                        onResult(false, "Sunucu yanıt verdi ama durum: HTTP ${res.code}")
                    }
                }
            } catch (e: Exception) {
                onResult(false, "Bağlantı kurulamadı: ${e.localizedMessage}")
            }
        }
    }

    companion object {
        private const val TAG = "CloudSyncEngine"

        @Volatile
        private var instance: CloudSyncEngine? = null

        fun getInstance(context: Context): CloudSyncEngine {
            return instance ?: synchronized(this) {
                instance ?: CloudSyncEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
