package com.forge.skeleton.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.forge.skeleton.R

object NotificationHelper {

    const val SERVICE_CHANNEL_ID = "forge_service"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            context.getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }
}
