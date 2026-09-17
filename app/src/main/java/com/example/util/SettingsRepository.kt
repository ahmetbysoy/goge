package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.KeyboardSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kalkan_settings_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<KeyboardSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): KeyboardSettings {
        return KeyboardSettings(
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
            hapticDuration = prefs.getInt(KEY_HAPTIC_DURATION, 25),
            soundFeedback = prefs.getBoolean(KEY_SOUND, false),
            showNumberRow = prefs.getBoolean(KEY_NUMBER_ROW, true),
            turkishSpecialKeys = prefs.getBoolean(KEY_TURKISH_KEYS, true),
            autoCapitalization = prefs.getBoolean(KEY_AUTO_CAPS, true),
            cloudSyncUrl = prefs.getString(KEY_SYNC_URL, "") ?: "",
            cloudSyncSecret = prefs.getString(KEY_SYNC_SECRET, "") ?: "",
            autoSyncOnCopy = prefs.getBoolean(KEY_AUTO_SYNC, false),
            themeName = prefs.getString(KEY_THEME, "Koyu Siber (OLED)") ?: "Koyu Siber (OLED)",
            lastDriveBackupAt = prefs.getString(KEY_LAST_DRIVE_AT, "") ?: "",
            lastDriveFileId = prefs.getString(KEY_LAST_DRIVE_FILE, "") ?: ""
        )
    }

    fun updateSettings(newSettings: KeyboardSettings) {
        prefs.edit()
            .putBoolean(KEY_HAPTIC, newSettings.hapticFeedback)
            .putInt(KEY_HAPTIC_DURATION, newSettings.hapticDuration)
            .putBoolean(KEY_SOUND, newSettings.soundFeedback)
            .putBoolean(KEY_NUMBER_ROW, newSettings.showNumberRow)
            .putBoolean(KEY_TURKISH_KEYS, newSettings.turkishSpecialKeys)
            .putBoolean(KEY_AUTO_CAPS, newSettings.autoCapitalization)
            .putString(KEY_SYNC_URL, newSettings.cloudSyncUrl)
            .putString(KEY_SYNC_SECRET, newSettings.cloudSyncSecret)
            .putBoolean(KEY_AUTO_SYNC, newSettings.autoSyncOnCopy)
            .putString(KEY_THEME, newSettings.themeName)
            .putString(KEY_LAST_DRIVE_AT, newSettings.lastDriveBackupAt)
            .putString(KEY_LAST_DRIVE_FILE, newSettings.lastDriveFileId)
            .apply()

        _settingsFlow.value = newSettings
    }

    fun setLastDriveBackup(timeLabel: String, fileId: String) {
        updateSettings(
            _settingsFlow.value.copy(
                lastDriveBackupAt = timeLabel,
                lastDriveFileId = fileId
            )
        )
    }

    companion object {
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_HAPTIC_DURATION = "haptic_duration"
        private const val KEY_SOUND = "sound_feedback"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_TURKISH_KEYS = "turkish_keys"
        private const val KEY_AUTO_CAPS = "auto_caps"
        private const val KEY_SYNC_URL = "sync_url"
        private const val KEY_SYNC_SECRET = "sync_secret"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_THEME = "theme"
        private const val KEY_LAST_DRIVE_AT = "last_drive_at"
        private const val KEY_LAST_DRIVE_FILE = "last_drive_file"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
