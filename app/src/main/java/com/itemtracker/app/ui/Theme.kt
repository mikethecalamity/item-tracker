package com.itemtracker.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.itemtracker.app.ThemeMode

/** Border color of an item in use */
val InUseGreen = Color(0xFF2E9E44)
/** Background color of the due badge */
val DueOrange = Color(0xFFE65100)
/** Warning badge and border color */
val WarningRed = Color(0xFFD32F2F)

/**
 * Decide whether the app is dark
 *
 * @param themeMode the chosen [ThemeMode]
 * @return true for the dark theme
 */
@Composable
fun isDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Apply the app colors, using the wallpaper colors on Android 12 and newer
 *
 * @param dark whether to use the dark theme
 * @param content the screen content
 */
@Composable
fun ItemTrackerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
