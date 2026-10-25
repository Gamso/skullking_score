package com.skullking.score.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Accès aux fichiers pour l'export (partage Android) et l'import (sélecteur de documents). */
@Singleton
class FileGateway @Inject constructor(@ApplicationContext private val context: Context) {

    /** Écrit un fichier dans le cache et renvoie une URI partageable par FileProvider. */
    suspend fun writeShareable(fileName: String, content: String, withBom: Boolean): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { out ->
            // Le BOM UTF-8 fait ouvrir correctement les accents dans les tableurs.
            if (withBom) out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Écrit une image dans le cache et renvoie une URI partageable. */
    suspend fun writeShareableBytes(fileName: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Enregistre une image dans `Pictures/SkullKing` (Android 10 et plus, sans permission de stockage). */
    suspend fun saveImageToGallery(fileName: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SkullKing")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (ignored: IOException) {
            resolver.delete(uri, null, null)
            false
        } catch (ignored: IllegalStateException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Impossible d'ouvrir le fichier")
        bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }
}
