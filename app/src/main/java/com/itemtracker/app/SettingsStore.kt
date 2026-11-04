package com.itemtracker.app

import android.content.Context

/** Load and save the [Settings] in shared preferences, for the app and the background check */
object SettingsStore {
    private const val PREFERENCES = "settings"
    private const val ORDER_DAY = "orderDay"
    private const val GRACE_DAYS = "graceDays"
    // Saved under its original name so existing values carry over
    private const val HEADS_UP_DAYS = "dueSoonDays"
    private const val SYNC_URI = "syncUri"
    private const val THEME_MODE = "themeMode"
    private const val DELIVERY_ALERTS = "deliveryAlerts"
    private const val RUN_OUT_ALERTS = "runOutAlerts"
    private const val ASKED_NOTIFICATION_PERMISSION = "askedNotificationPermission"

    /**
     * Load the settings
     *
     * @param context the [Context] to use
     * @return the saved settings, with defaults for anything never saved
     */
    fun load(context: Context): Settings {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val themeName = preferences.getString(THEME_MODE, null)
        return Settings(
            orderDay = preferences.getInt(ORDER_DAY, 1),
            graceDays = preferences.getInt(GRACE_DAYS, 7),
            headsUpDays = preferences.getInt(HEADS_UP_DAYS, 7),
            syncUri = preferences.getString(SYNC_URI, null),
            themeMode = ThemeMode.entries.firstOrNull { mode -> mode.name == themeName } ?: ThemeMode.SYSTEM,
            deliveryAlerts = preferences.getBoolean(DELIVERY_ALERTS, true),
            runOutAlerts = preferences.getBoolean(RUN_OUT_ALERTS, true),
        )
    }

    /**
     * Save the settings
     *
     * @param context the [Context] to use
     * @param settings the [Settings] to save
     */
    fun save(context: Context, settings: Settings) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putInt(ORDER_DAY, settings.orderDay)
            .putInt(GRACE_DAYS, settings.graceDays)
            .putInt(HEADS_UP_DAYS, settings.headsUpDays)
            .putString(SYNC_URI, settings.syncUri)
            .putString(THEME_MODE, settings.themeMode.name)
            .putBoolean(DELIVERY_ALERTS, settings.deliveryAlerts)
            .putBoolean(RUN_OUT_ALERTS, settings.runOutAlerts)
            .apply()
    }

    /**
     * Record that the notification permission was asked for, and report whether it had been asked before
     *
     * @param context the [Context] to use
     * @return true the first time, false afterwards
     */
    fun firstNotificationPermissionAsk(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(ASKED_NOTIFICATION_PERMISSION, false)) return false
        preferences.edit().putBoolean(ASKED_NOTIFICATION_PERMISSION, true).apply()
        return true
    }
}
