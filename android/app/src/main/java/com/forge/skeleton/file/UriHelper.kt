package com.forge.skeleton.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object UriHelper {

    suspend fun copyToCache(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val ext = resolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "bin"
        val target = File(context.cacheDir, "${UUID.randomUUID()}.$ext")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open uri" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }
}
