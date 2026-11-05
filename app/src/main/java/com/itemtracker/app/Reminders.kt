package com.itemtracker.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedule and post the daily summary notification
 *
 * Uses a plain inexact daily alarm rather than a background-job library, so the app needs no boot, wake-lock or
 * foreground-service permissions. Alarms are cleared when the phone restarts and set again when the app is opened.
 */
object Reminders {
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1
    private val CHECK_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * Schedule the daily check at about 9 AM, replacing any earlier schedule
     *
     * @param context the [Context] to use
     */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        val todayAtCheck = now.toLocalDate().atTime(CHECK_TIME)
        val nextCheck = if (now.isBefore(todayAtCheck)) todayAtCheck else todayAtCheck.plusDays(1)
        val firstMillis = nextCheck.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val trigger = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SummaryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java)
            .setInexactRepeating(AlarmManager.RTC, firstMillis, AlarmManager.INTERVAL_DAY, trigger)
    }

    /**
     * Check whether the app may post notifications
     *
     * @param context the [Context] to use
     * @return true when allowed
     */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Post the summary, or nothing when there are no alerts or notifications are not allowed
     *
     * @param context the [Context] to use
     * @param alerts the alert lines
     */
    fun post(context: Context, alerts: List<String>) {
        if (alerts.isEmpty() || !canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (alerts.size == 1) "1 subscription item needs attention"
        else "${alerts.size} subscription items need attention"
        val style = NotificationCompat.InboxStyle()
        alerts.forEach(style::addLine)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(alerts.first())
            .setStyle(if (alerts.size == 1) NotificationCompat.BigTextStyle().bigText(alerts.first()) else style)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
        catch (e: SecurityException) {
            // Permission was withdrawn between the check and the post; nothing to show
        }
    }

    /** Run the daily check when the alarm fires */
    class SummaryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val settings = SettingsStore.load(context)
            post(context, Alerts.forToday(Store.loadLocal(context).items, settings))
        }
    }
}
