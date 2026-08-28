package com.iratrack.app.notifications

import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL = "usage_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "IraTrack usage alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Local anomaly notifications from IraTrack."
                }
            )
        }
    }

    fun notifyAnomaly(context: Context, provider: String, percent: Int) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Unusual AI usage detected")
            .setContentText("$provider usage is $percent% above the local baseline.")
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(provider.hashCode(), notification)
    }
}
