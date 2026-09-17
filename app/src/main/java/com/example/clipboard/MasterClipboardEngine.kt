package com.example.clipboard

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.model.ClipboardItem
import com.example.model.ClipboardStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Master Clipboard Engine
 * - Zero character truncation (Infinite string length supported)
 * - Zero automatic expiration (Never deletes until user manually chooses)
 * - Instant persistent write to disk (JSON & Stream TXT)
 * - Concurrency protected via ReentrantReadWriteLock
 */
class MasterClipboardEngine private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rwLock = ReentrantReadWriteLock()

    private val _itemsFlow = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val itemsFlow: StateFlow<List<ClipboardItem>> = _itemsFlow.asStateFlow()

    private val _statsFlow = MutableStateFlow(ClipboardStats())
    val statsFlow: StateFlow<ClipboardStats> = _statsFlow.asStateFlow()

    private val storageDir: File by lazy {
        val externalDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val dir = if (externalDocs != null) {
            File(externalDocs, "KalkanClipboard")
        } else {
            File(context.filesDir, "KalkanClipboard")
        }
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val masterJsonFile: File by lazy { File(storageDir, "clipboard_master.json") }
    private val streamTxtFile: File by lazy { File(storageDir, "clipboard_stream.txt") }
    private val exportMdFile: File by lazy { File(storageDir, "clipboard_export.md") }

    var onNewClipAddedListener: ((ClipboardItem) -> Unit)? = null

    init {
        loadFromDisk()
    }

    /**
     * Loads existing master clips from local JSON storage.
     */
    private fun loadFromDisk() {
        scope.launch {
            rwLock.read {
                try {
                    if (masterJsonFile.exists() && masterJsonFile.length() > 0) {
                        val content = masterJsonFile.readText(Charsets.UTF_8)
                        val jsonArray = JSONArray(content)
                        val loaded = mutableListOf<ClipboardItem>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            loaded.add(
                                ClipboardItem(
                                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                    text = obj.getString("text"),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                    formattedDate = obj.optString("formattedDate", ""),
                                    charCount = obj.optInt("charCount", 0),
                                    wordCount = obj.optInt("wordCount", 0),
                                    lineCount = obj.optInt("lineCount", 1),
                                    isPinned = obj.optBoolean("isPinned", false),
                                    sourceApp = obj.optString("sourceApp", "Sistem Panosu"),
                                    isSynced = obj.optBoolean("isSynced", false)
                                )
                            )
                        }
                        // Sort: pinned first, then newest timestamp
                        val sorted = loaded.sortedWith(
                            compareByDescending<ClipboardItem> { it.isPinned }
                                .thenByDescending { it.timestamp }
                        )
                        _itemsFlow.value = sorted
                        updateStats(sorted)
                        Log.d(TAG, "Master clipboard loaded ${sorted.size} items from disk.")
                    } else {
                        // Seed initial welcome guide clip if brand new
                        val welcomeItem = ClipboardItem(
                            text = "🛡️ KALKAN KLAVYE & SONSUZ PANO MOTORU AKTİF!\n\n" +
                                    "• Bu panoda karakter sınırı yoktur (100.000+ karakter desteklenir).\n" +
                                    "• Kopyaladığın hiçbir metin süre aşımıyla SİLİNMEZ.\n" +
                                    "• Tüm kopyalananlar anında 'clipboard_master.json' ve 'clipboard_stream.txt' dosyalarına kaydedilir.\n" +
                                    "• Sabitlemek istediğin öğelerin yıldızına tıkla, kaybolmasın!",
                            isPinned = true,
                            sourceApp = "Kalkan Sistem"
                        )
                        _itemsFlow.value = listOf(welcomeItem)
                        saveToDiskInternal(listOf(welcomeItem), appendToStream = welcomeItem)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading master clipboard from disk", e)
                }
            }
        }
    }

    /**
     * Add or update clip.
     * Guaranteed ZERO truncation.
     */
    fun addClip(rawText: String, sourceApp: String = "Sistem Panosu"): Boolean {
        if (rawText.isBlank()) return false

        var newlyAdded: ClipboardItem? = null

        rwLock.write {
            val currentList = _itemsFlow.value.toMutableList()

            // Check if identical text already exists
            val existingIndex = currentList.indexOfFirst { it.text == rawText }
            if (existingIndex != -1) {
                val existing = currentList.removeAt(existingIndex)
                // Bump to top with fresh timestamp but keep pin status
                val updated = existing.copy(
                    timestamp = System.currentTimeMillis(),
                    formattedDate = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                )
                currentList.add(0, updated)
                newlyAdded = updated
            } else {
                val newItem = ClipboardItem(
                    text = rawText,
                    sourceApp = sourceApp
                )
                currentList.add(0, newItem)
                newlyAdded = newItem
            }

            val sorted = currentList.sortedWith(
                compareByDescending<ClipboardItem> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
            _itemsFlow.value = sorted
            updateStats(sorted)

            // Persist to disk asynchronously
            scope.launch {
                saveToDiskInternal(sorted, appendToStream = newlyAdded)
            }
        }

        newlyAdded?.let { item ->
            onNewClipAddedListener?.invoke(item)
        }

        return true
    }

    fun togglePin(id: String) {
        rwLock.write {
            val current = _itemsFlow.value.map { item ->
                if (item.id == id) item.copy(isPinned = !item.isPinned) else item
            }
            val sorted = current.sortedWith(
                compareByDescending<ClipboardItem> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
            _itemsFlow.value = sorted
            updateStats(sorted)
            scope.launch {
                saveToDiskInternal(sorted, appendToStream = null)
            }
        }
    }

    fun deleteClip(id: String) {
        rwLock.write {
            val filtered = _itemsFlow.value.filterNot { it.id == id }
            _itemsFlow.value = filtered
            updateStats(filtered)
            scope.launch {
                saveToDiskInternal(filtered, appendToStream = null)
            }
        }
    }

    fun clearUnpinned() {
        rwLock.write {
            val onlyPinned = _itemsFlow.value.filter { it.isPinned }
            _itemsFlow.value = onlyPinned
            updateStats(onlyPinned)
            scope.launch {
                saveToDiskInternal(onlyPinned, appendToStream = null)
            }
        }
    }

    fun markSynced(id: String) {
        rwLock.write {
            val updated = _itemsFlow.value.map {
                if (it.id == id) it.copy(isSynced = true) else it
            }
            _itemsFlow.value = updated
        }
    }

    private fun saveToDiskInternal(list: List<ClipboardItem>, appendToStream: ClipboardItem?) {
        rwLock.write {
            try {
                // 1. Write Master JSON
                val jsonArray = JSONArray()
                list.forEach { item ->
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("text", item.text)
                        put("timestamp", item.timestamp)
                        put("formattedDate", item.formattedDate)
                        put("charCount", item.charCount)
                        put("wordCount", item.wordCount)
                        put("lineCount", item.lineCount)
                        put("isPinned", item.isPinned)
                        put("sourceApp", item.sourceApp)
                        put("isSynced", item.isSynced)
                    }
                    jsonArray.put(obj)
                }

                masterJsonFile.writeText(jsonArray.toString(2), Charsets.UTF_8)

                // 2. Append to Stream TXT
                if (appendToStream != null) {
                    FileWriter(streamTxtFile, true).use { writer ->
                        writer.append(appendToStream.toFormattedLog())
                    }
                }

                // 3. Export to Markdown
                exportMdFile.writeText(buildMarkdownExport(list), Charsets.UTF_8)

                updateStats(list)
                Log.d(TAG, "Successfully persisted ${list.size} clips to disk.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to disk", e)
            }
        }
    }

    private fun buildMarkdownExport(list: List<ClipboardItem>): String {
        return buildString {
            append("# 🛡️ Kalkan Klavye - Master Pano Veritabanı\n\n")
            append("> Toplam Öğe: ${list.size} | Son Güncelleme: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
            list.forEachIndexed { index, item ->
                append("### [${index + 1}] ${item.formattedDate} ${if (item.isPinned) "⭐ [SABİTLENDİ]" else ""}\n")
                append("- **Karakter Sayısı**: ${item.charCount} | **Satır**: ${item.lineCount} | **Kaynak**: ${item.sourceApp}\n\n")
                append("```\n")
                append(item.text)
                append("\n```\n\n---\n\n")
            }
        }
    }

    private fun updateStats(list: List<ClipboardItem>) {
        val totalChars = list.sumOf { it.charCount.toLong() }
        val totalLines = list.sumOf { it.lineCount.toLong() }
        val pinnedCount = list.count { it.isPinned }
        val jsonSize = if (masterJsonFile.exists()) masterJsonFile.length() / 1024.0 else 0.0
        val txtSize = if (streamTxtFile.exists()) streamTxtFile.length() / 1024.0 else 0.0

        _statsFlow.value = ClipboardStats(
            totalClips = list.size,
            pinnedClips = pinnedCount,
            totalCharacters = totalChars,
            totalLines = totalLines,
            jsonFileSizeKb = jsonSize,
            txtFileSizeKb = txtSize
        )
    }

    fun getMasterJsonFilePath(): String = masterJsonFile.absolutePath
    fun getStreamTxtFilePath(): String = streamTxtFile.absolutePath
    fun getStorageDirectoryPath(): String = storageDir.absolutePath

    companion object {
        private const val TAG = "MasterClipboardEngine"

        @Volatile
        private var instance: MasterClipboardEngine? = null

        fun getInstance(context: Context): MasterClipboardEngine {
            return instance ?: synchronized(this) {
                instance ?: MasterClipboardEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
