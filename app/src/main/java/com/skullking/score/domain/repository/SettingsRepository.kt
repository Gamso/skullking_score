package com.skullking.score.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** Screen kept on while a game is in progress. */
    val keepScreenOn: Boolean = true,
    val autoBackup: Boolean = false,
    /** Backup folder chosen by the user (tree URI from the folder picker). */
    val backupFolder: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupFailed: Boolean = false,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun update(transform: (AppSettings) -> AppSettings)
}
