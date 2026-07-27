package com.forge.skeleton

import android.app.Application
import com.forge.skeleton.crash.CrashLogger
import com.forge.skeleton.notification.NotificationHelper
import com.forge.skeleton.settings.AppSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.getInstance(this)
        NotificationHelper.createChannels(this)
        CrashLogger.init(this)
    }
}
