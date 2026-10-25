package com.skullking.score.domain.repository

import com.skullking.score.domain.model.GameDetails

/** Player of the file whose name already exists on the device under another identifier. */
data class NameConflict(val importedUuid: String, val name: String)

data class ImportPreview(
    val formatVersion: Int,
    val newGames: Int,
    val alreadyPresentGames: Int,
    val newPlayers: Int,
    val nameConflicts: List<NameConflict> = emptyList(),
)

/**
 * User choices when importing: the whole file is always imported; only players whose name already exists can be
 * merged with the existing profile (otherwise a new profile is created).
 */
data class ImportOptions(val mergePlayerUuids: Set<String> = emptySet())

data class ImportResult(
    val importedGames: Int,
    val skippedGames: Int,
    val createdPlayers: Int,
    val mergedPlayers: Int = 0,
)

/** The file is not a backup readable by this version of the app. */
open class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Backup produced by a newer version of the app. */
class NewerBackupException(val formatVersion: Int) :
    InvalidBackupException("Unsupported format version: $formatVersion")

interface BackupRepository {
    /** Full JSON (players, games, rounds, events) with a format version number. */
    suspend fun exportJson(games: List<GameDetails>): String

    @Throws(InvalidBackupException::class)
    suspend fun previewImport(json: String): ImportPreview

    /** Merge by game identifier, without duplicates. */
    @Throws(InvalidBackupException::class)
    suspend fun importJson(json: String, options: ImportOptions = ImportOptions()): ImportResult
}
