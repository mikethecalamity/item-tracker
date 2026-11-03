package com.itemtracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.itemtracker.app.ui.HistoryScreen
import com.itemtracker.app.ui.HomeScreen
import com.itemtracker.app.ui.ItemTrackerTheme
import com.itemtracker.app.ui.SettingsScreen
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
        if (savedInstanceState == null) viewModel.handleIntent(intent)
        setContent {
            ItemTrackerTheme { AppRoot(viewModel) }
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
}
