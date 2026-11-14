package com.gamso.skullkingscore.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.gamso.skullkingscore.di.ApplicationScope
import com.gamso.skullkingscore.domain.repository.AppSettings
import com.gamso.skullkingscore.domain.repository.BackupRepository
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Automatic JSON backup into the folder chosen by the user (access limited to that folder through the Android
 * folder picker). Runs at the end of a game and when the app starts if the last backup is more than a day old.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val games: GameRepository,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /**
     * Starts [run] without waiting for it, in the application scope: the caller can leave its screen right away
     * and the backup is not cancelled with it.
     */
    fun runInBackground() {
        scope.launch { run() }
    }

    suspend fun runIfDue(now: Long = System.currentTimeMillis()) {
        if (isBackupDue(settings.settings.value, now)) run()
    }

    /** Writes a backup now if the feature is enabled; returns `false` on failure. */
    suspend fun run(): Boolean = mutex.withLock {
        val folder = settings.settings.value.backupFolder
        if (!settings.settings.value.autoBackup || folder == null) return true
        val ok = withContext(Dispatchers.IO) {
            try {
                val all = games.getAllGames()
                if (all.isNotEmpty()) write(Uri.parse(folder), backup.exportJson(all))
                true
            } catch (ignored: IOException) {
                false
            } catch (ignored: SecurityException) {
                false
            } catch (ignored: IllegalArgumentException) {
                false
            }
        }
        settings.update {
            it.copy(
                lastBackupAt = if (ok) System.currentTimeMillis() else it.lastBackupAt,
                lastBackupFailed = !ok,
                autoBackup = it.autoBackup,
            )
        }
        ok
    }

    private fun write(tree: Uri, json: String) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val rotation = planBackupRotation(listBackups(tree), LocalDate.now())
        rotation.replaced.forEach { DocumentsContract.deleteDocument(resolver, it) }
        val target = DocumentsContract.createDocument(resolver, parent, "application/json", rotation.fileName)
            ?: throw IOException("Could not create the file")
        resolver.openOutputStream(target)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Could not write the file")
        rotation.obsolete.forEach { DocumentsContract.deleteDocument(resolver, it) }
    }

    /** Backup files in the folder: (document URI, display name). */
    private fun listBackups(tree: Uri): List<Pair<Uri, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val projection =
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val result = mutableListOf<Pair<Uri, String>>()
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (BACKUP_NAME.matches(name)) {
                    result += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)) to name
                }
            }
        }
        return result
    }
}

private const val PREFIX = "skullking-"
private const val KEPT_BACKUPS = 10
private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
private val BACKUP_NAME = Regex("""skullking-\d{4}-\d{2}-\d{2}\.json""")

/**
 * A backup is due when the feature is on and the last one is missing, failed or more than a day old ([now] and
 * the last backup time in milliseconds).
 */
internal fun isBackupDue(settings: AppSettings, now: Long): Boolean {
    val last = settings.lastBackupAt
    val due = last == null || settings.lastBackupFailed || now - last >= ONE_DAY_MS
    return settings.autoBackup && due
}

/**
 * Files to delete around the backup of [today]: [replaced] holds the files of the same day (overwritten by the new
 * one), [obsolete] the oldest ones beyond the [KEPT_BACKUPS] kept with the new one. [T] identifies a file (a
 * document URI in the app).
 */
internal data class BackupRotation<T>(val fileName: String, val replaced: List<T>, val obsolete: List<T>)

/** Plans the rotation from the backups already in the folder: (identifier, display name), in any order. */
internal fun <T> planBackupRotation(existing: List<Pair<T, String>>, today: LocalDate): BackupRotation<T> {
    val name = "$PREFIX$today.json"
    return BackupRotation(
        fileName = name,
        replaced = existing.filter { it.second == name }.map { it.first },
        obsolete = existing.filter { it.second != name }
            .sortedByDescending { it.second }
            .drop(KEPT_BACKUPS - 1)
            .map { it.first },
    )
}
