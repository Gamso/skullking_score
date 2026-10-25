package com.skullking.score.domain.repository

import com.skullking.score.domain.model.GameDetails

/** Joueur du fichier dont le nom existe déjà sur l'appareil sous un autre identifiant. */
data class NameConflict(val importedUuid: String, val name: String)

data class ImportPreview(
    val formatVersion: Int,
    val newGames: Int,
    val alreadyPresentGames: Int,
    val newPlayers: Int,
    val nameConflicts: List<NameConflict> = emptyList(),
)

/**
 * Choix de l'utilisateur à l'import : parties retenues (`null` = toutes les nouvelles) et joueurs du fichier à
 * fusionner avec le profil existant de même nom (les autres homonymes créent un nouveau profil).
 */
data class ImportOptions(val selectedGameUuids: Set<String>? = null, val mergePlayerUuids: Set<String> = emptySet())

data class ImportResult(
    val importedGames: Int,
    val skippedGames: Int,
    val createdPlayers: Int,
    val mergedPlayers: Int = 0,
)

/** Le fichier n'est pas une sauvegarde lisible par cette version de l'application. */
open class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Sauvegarde produite par une version plus récente de l'application. */
class NewerBackupException(val formatVersion: Int) :
    InvalidBackupException("Version de format non prise en charge : $formatVersion")

interface BackupRepository {
    /** JSON complet (joueurs, parties, manches, événements) avec un numéro de version de format. */
    suspend fun exportJson(games: List<GameDetails>): String

    @Throws(InvalidBackupException::class)
    suspend fun previewImport(json: String): ImportPreview

    /** Fusion par identifiant de partie, sans doublon. */
    @Throws(InvalidBackupException::class)
    suspend fun importJson(json: String, options: ImportOptions = ImportOptions()): ImportResult
}
