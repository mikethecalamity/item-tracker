package com.itemtracker.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.itemtracker.app.ui.HistoryScreen
import com.itemtracker.app.ui.HomeScreen
import com.itemtracker.app.ui.ItemTrackerTheme
import com.itemtracker.app.ui.SettingsScreen
import com.itemtracker.app.ui.isDark
import kotlinx.coroutines.launch

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val HISTORY_PREFIX = "history:"

/** Host the app, receive shared products and sync when opened */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.handleIntent(intent)
            // Only a fresh open checks; returning to an app that is already open does not
            viewModel.checkForUpdateOnLaunch()
            Reminders.schedule(this)
        }
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = isDark(settings.themeMode)
            // Match the status and navigation bar icons to the chosen theme, not just the system one
            LaunchedEffect(dark) {
                val barStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            ItemTrackerTheme(dark) { AppRoot(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Pick up changes made on another phone (partner mode) whenever the app is opened
        lifecycleScope.launch { viewModel.syncNow() }
    }
}

/**
 * Show the current screen, kept as a saveable route string: home, settings or history:itemId
 *
 * @param viewModel the [MainViewModel] to use
 */
@Composable
private fun AppRoot(viewModel: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(HOME) }
    val pendingShare by viewModel.pendingShare.collectAsStateWithLifecycle()

    val launchUpdate by viewModel.launchUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Ask once for notification permission, since the daily summary is on by default
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(context) &&
            SettingsStore.firstNotificationPermissionAsk(context)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingShare) { if (pendingShare != null) screen = HOME }
    BackHandler(enabled = screen != HOME) { screen = HOME }

    when {
        screen == SETTINGS -> SettingsScreen(viewModel, onBack = { screen = HOME })
        screen.startsWith(HISTORY_PREFIX) ->
            HistoryScreen(viewModel, screen.removePrefix(HISTORY_PREFIX), onBack = { screen = HOME })
        else -> HomeScreen(
            viewModel,
            onOpenHistory = { screen = HISTORY_PREFIX + it },
            onOpenSettings = { screen = SETTINGS },
        )
    }

    launchUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLaunchUpdate,
            title = { Text("Update available") },
            text = {
                Text("Version ${release.version} is available (you have ${BuildConfig.VERSION_NAME}). Update now?")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissLaunchUpdate()
                    viewModel.installUpdate(release)
                }) { Text("Update now") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissLaunchUpdate) { Text("Later") } },
        )
    }
}
