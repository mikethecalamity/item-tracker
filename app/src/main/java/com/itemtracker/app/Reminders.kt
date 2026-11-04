package com.itemtracker.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Schedule and post the daily summary notification */
object Reminders {
    private const val WORK_NAME = "daily-summary"
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1
    private val CHECK_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * Schedule the daily check at about 9 AM, keeping an existing schedule
     *
     * @param context the [Context] to use
     */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        val todayAtCheck = now.toLocalDate().atTime(CHECK_TIME)
        val nextCheck = if (now.isBefore(todayAtCheck)) todayAtCheck else todayAtCheck.plusDays(1)
        val request = PeriodicWorkRequestBuilder<SummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, nextCheck).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
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

    /**
     * Run the daily check in the background
     *
     * @param context the [Context] to use
     * @param parameters the [WorkerParameters] to use
     */
    class SummaryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val settings = SettingsStore.load(applicationContext)
            post(applicationContext, Alerts.forToday(Store.loadLocal(applicationContext).items, settings))
            return Result.success()
        }
    }
}
