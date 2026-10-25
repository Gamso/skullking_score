package com.skullking.score.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** Écran maintenu allumé pendant une partie en cours. */
    val keepScreenOn: Boolean = true,
    val autoBackup: Boolean = false,
    /** Dossier de sauvegarde choisi par l'utilisateur (URI d'arbre du sélecteur de dossier). */
    val backupFolder: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupFailed: Boolean = false,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun update(transform: (AppSettings) -> AppSettings)
}
