package com.forge.skeleton.file

import android.content.Intent
import android.net.Uri
import android.os.Build

object IntentHelper {

    fun handleShareIntent(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> extractStream(intent)?.let { listOf(it) } ?: emptyList()
        Intent.ACTION_SEND_MULTIPLE -> extractStreamList(intent) ?: emptyList()
        else -> emptyList()
    }

    private fun extractStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun extractStreamList(intent: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
}
