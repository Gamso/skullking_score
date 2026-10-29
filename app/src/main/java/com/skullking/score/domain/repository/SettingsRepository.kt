package com.skullking.score.domain.repository

import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.ScoringMode
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** Screen kept on while a game is in progress. */
    val keepScreenOn: Boolean = true,
    /** A new game starts with the scoring mode and expansion of the previous one. */
    val rememberSetup: Boolean = true,
    /** Setup of the last game created, kept even when [rememberSetup] is off. */
    val lastSetup: SavedSetup? = null,
    val autoBackup: Boolean = false,
    /** Backup folder chosen by the user (tree URI from the folder picker). */
    val backupFolder: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupFailed: Boolean = false,
)

/** Setup of a new game: scoring mode and expansion cards. */
data class SavedSetup(val mode: ScoringMode, val expansionEnabled: Boolean, val cards: Set<ExpansionCard>)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun update(transform: (AppSettings) -> AppSettings)
}
