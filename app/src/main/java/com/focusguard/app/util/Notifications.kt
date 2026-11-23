package com.focusguard.app.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.R
import com.focusguard.app.ui.MainActivity

object Notifications {

    private const val CHANNEL_FOCUS = "focus"
    private const val ID_FOCUS_COMPLETE = 1001

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_FOCUS,
            context.getString(R.string.channel_focus),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_focus_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // checked via hasNotificationPermission()
    fun showFocusComplete(context: Context, minutes: Int) {
        if (!Permissions.hasNotificationPermission(context)) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Focus session complete")
            .setContentText("Nice work — ${formatMinutes(minutes)} of focus. Your apps are unlocked.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_FOCUS_COMPLETE, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }
}
