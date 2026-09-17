package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Unlimited Master Clipboard Item.
 * ZERO truncation, ZERO expiration.
 */
data class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDate: String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
    val charCount: Int = text.length,
    val wordCount: Int = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size,
    val lineCount: Int = text.lines().size,
    val isPinned: Boolean = false,
    val sourceApp: String = "Sistem Panosu",
    val isSynced: Boolean = false
) {
    fun toFormattedLog(): String {
        return buildString {
            append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append("📅 TARİH: $formattedDate | 🆔: ${id.take(8)}\n")
            append("📌 SABİT: ${if (isPinned) "EVET" else "HAYIR"} | 📊 KARAKTER: $charCount | 📝 SATIR: $lineCount | 📱 KAYNAK: $sourceApp\n")
            append("────────────────────────────────────────\n")
            append(text)
            append("\n\n")
        }
    }
}

/**
 * Persistent configuration for Kalkan Keyboard & Clipboard Engine.
 */
data class KeyboardSettings(
    val hapticFeedback: Boolean = true,
    val hapticDuration: Int = 25, // ms
    val soundFeedback: Boolean = false,
    val showNumberRow: Boolean = true,
    val turkishSpecialKeys: Boolean = true,
    val autoCapitalization: Boolean = true,
    val cloudSyncUrl: String = "",
    val cloudSyncSecret: String = "",
    val autoSyncOnCopy: Boolean = false,
    val themeName: String = "Koyu Siber (OLED)"
)

/**
 * Statistics overview of the infinite clipboard engine.
 */
data class ClipboardStats(
    val totalClips: Int = 0,
    val pinnedClips: Int = 0,
    val totalCharacters: Long = 0L,
    val totalLines: Long = 0L,
    val jsonFileSizeKb: Double = 0.0,
    val txtFileSizeKb: Double = 0.0,
    val lastSyncTime: String = "Henüz Senkronize Edilmedi"
)
