package com.skullking.score.data.settings

import android.content.Context
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.ScoringMode
import com.skullking.score.domain.repository.AppSettings
import com.skullking.score.domain.repository.SavedSetup
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
            .putBoolean(REMEMBER_SETUP, next.rememberSetup)
            .putString(LAST_MODE, next.lastSetup?.mode?.name)
            .putBoolean(LAST_EXPANSION, next.lastSetup?.expansionEnabled == true)
            .putString(LAST_CARDS, next.lastSetup?.cards?.joinToString(SEPARATOR) { it.name })
            .putBoolean(AUTO_BACKUP, next.autoBackup)
            .putString(BACKUP_FOLDER, next.backupFolder)
            .putLong(LAST_BACKUP_AT, next.lastBackupAt ?: NONE)
            .putBoolean(LAST_BACKUP_FAILED, next.lastBackupFailed)
            .apply()
        state.value = next
    }

    private fun read() = AppSettings(
        keepScreenOn = prefs.getBoolean(KEEP_SCREEN_ON, true),
        rememberSetup = prefs.getBoolean(REMEMBER_SETUP, true),
        lastSetup = readSetup(),
        autoBackup = prefs.getBoolean(AUTO_BACKUP, false),
        backupFolder = prefs.getString(BACKUP_FOLDER, null),
        lastBackupAt = prefs.getLong(LAST_BACKUP_AT, NONE).takeIf { it != NONE },
        lastBackupFailed = prefs.getBoolean(LAST_BACKUP_FAILED, false),
    )

    /** Unknown names (renamed enum values) are skipped: the setup is only a suggestion. */
    private fun readSetup(): SavedSetup? {
        val mode = prefs.getString(LAST_MODE, null)?.let { name -> ScoringMode.entries.firstOrNull { it.name == name } }
            ?: return null
        return SavedSetup(
            mode = mode,
            expansionEnabled = prefs.getBoolean(LAST_EXPANSION, false),
            cards = prefs.getString(LAST_CARDS, null).orEmpty().split(SEPARATOR)
                .mapNotNull { name -> ExpansionCard.entries.firstOrNull { it.name == name } }
                .toSet(),
        )
    }

    private companion object {
        const val FILE = "settings"
        const val KEEP_SCREEN_ON = "keep_screen_on"
        const val REMEMBER_SETUP = "remember_setup"
        const val LAST_MODE = "last_setup_mode"
        const val LAST_EXPANSION = "last_setup_expansion"
        const val LAST_CARDS = "last_setup_cards"
        const val SEPARATOR = ","
        const val AUTO_BACKUP = "auto_backup"
        const val BACKUP_FOLDER = "backup_folder"
        const val LAST_BACKUP_AT = "last_backup_at"
        const val LAST_BACKUP_FAILED = "last_backup_failed"
        const val NONE = -1L
    }
}
