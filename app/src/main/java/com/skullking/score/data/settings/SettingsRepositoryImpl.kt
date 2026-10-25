package com.skullking.score.data.settings

import android.content.Context
import com.skullking.score.domain.repository.AppSettings
import com.skullking.score.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SettingsRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        prefs.edit()
            .putBoolean(KEEP_SCREEN_ON, next.keepScreenOn)
            .putBoolean(AUTO_BACKUP, next.autoBackup)
            .putString(BACKUP_FOLDER, next.backupFolder)
            .putLong(LAST_BACKUP_AT, next.lastBackupAt ?: NONE)
            .putBoolean(LAST_BACKUP_FAILED, next.lastBackupFailed)
            .apply()
        state.value = next
    }

    private fun read() = AppSettings(
        keepScreenOn = prefs.getBoolean(KEEP_SCREEN_ON, true),
        autoBackup = prefs.getBoolean(AUTO_BACKUP, false),
        backupFolder = prefs.getString(BACKUP_FOLDER, null),
        lastBackupAt = prefs.getLong(LAST_BACKUP_AT, NONE).takeIf { it != NONE },
        lastBackupFailed = prefs.getBoolean(LAST_BACKUP_FAILED, false),
    )

    private companion object {
        const val FILE = "settings"
        const val KEEP_SCREEN_ON = "keep_screen_on"
        const val AUTO_BACKUP = "auto_backup"
        const val BACKUP_FOLDER = "backup_folder"
        const val LAST_BACKUP_AT = "last_backup_at"
        const val LAST_BACKUP_FAILED = "last_backup_failed"
        const val NONE = -1L
    }
}
