package com.forge.skeleton.crash

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    fun init(context: Context) {
        val logFile = File(context.filesDir, "crash.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(logFile, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(logFile: File, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        logFile.appendText("[$stamp] thread=${thread.name}\n$trace\n")
    }
}
