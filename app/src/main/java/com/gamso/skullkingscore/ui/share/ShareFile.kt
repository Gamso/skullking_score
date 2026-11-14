package com.gamso.skullkingscore.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the system share sheet for the file at [uri], granting the chosen app the right to read it. */
fun Context.shareFile(uri: Uri, mimeType: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}
