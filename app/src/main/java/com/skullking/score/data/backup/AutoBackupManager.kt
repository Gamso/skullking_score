package com.skullking.score.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.skullking.score.domain.repository.BackupRepository
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sauvegarde JSON automatique dans le dossier choisi par l'utilisateur (accès limité à ce dossier par le sélecteur
 * de dossier Android). Lancée à la fin d'une partie et au démarrage de l'appli quand la dernière date d'un jour.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val games: GameRepository,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    suspend fun runIfDue(now: Long = System.currentTimeMillis()) {
        val current = settings.settings.value
        val last = current.lastBackupAt
        val due = last == null || current.lastBackupFailed || now - last >= ONE_DAY_MS
        if (current.autoBackup && due) run()
    }

    /** Écrit une sauvegarde maintenant si la fonction est active ; renvoie `false` en cas d'échec. */
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
        val existing = listBackups(tree)
        val name = "$PREFIX${LocalDate.now()}.json"
        existing.filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
        val target = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: throw IOException("Création impossible")
        resolver.openOutputStream(target)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Écriture impossible")
        existing.filter { it.second != name }
            .sortedByDescending { it.second }
            .drop(KEPT_BACKUPS - 1)
            .forEach { DocumentsContract.deleteDocument(resolver, it.first) }
    }

    /** Fichiers de sauvegarde du dossier : (URI du document, nom affiché). */
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

    private companion object {
        const val PREFIX = "skullking-"
        const val KEPT_BACKUPS = 10
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        val BACKUP_NAME = Regex("""skullking-\d{4}-\d{2}-\d{2}\.json""")
    }
}
