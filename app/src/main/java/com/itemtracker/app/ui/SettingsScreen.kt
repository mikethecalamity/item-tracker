package com.itemtracker.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itemtracker.app.BuildConfig
import com.itemtracker.app.MainViewModel
import com.itemtracker.app.Updater
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Show the ordering, sync and CSV settings
 *
 * @param viewModel the [MainViewModel] to use
 * @param onBack called to return to the main screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    val createSync = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.createSyncFile(uri)
    }
    val openSync = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.linkSyncFile(uri)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Ordering") {
                NumberSetting(
                    label = "Order day of the month",
                    description = "Orders are estimated for this day. Months that are shorter use their last day.",
                    value = settings.orderDay, range = 1..31,
                ) { viewModel.updateSettings(settings.copy(orderDay = it)) }
                NumberSetting(
                    label = "Days you can go without",
                    description = "If an item runs out this many days or fewer before an order day, " +
                        "wait for that order day. Otherwise order a month earlier.",
                    value = settings.graceDays, range = 0..30,
                ) { viewModel.updateSettings(settings.copy(graceDays = it)) }
                NumberSetting(
                    label = "\"Due soon\" badge",
                    description = "Show the badge when the order date is this many days away or less.",
                    value = settings.dueSoonDays, range = 0..60,
                ) { viewModel.updateSettings(settings.copy(dueSoonDays = it)) }
            }

            Section("Google Drive sync") {
                Text(
                    "Keeps your data in a file in Google Drive. The Google Drive app must be installed; " +
                        "pick \"Drive\" in the file picker. To share with a partner, both phones choose the same file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.syncUri == null) {
                    OutlinedButton(
                        onClick = { createSync.launch("item-tracker-sync.json") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create a new sync file")
                    }
                    OutlinedButton(onClick = { openSync.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Use an existing sync file")
                    }
                }
                else {
                    Text(
                        "Linked to: " + Uri.parse(settings.syncUri).lastPathSegment.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    syncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        OutlinedButton(onClick = { scope.launch { viewModel.syncNow() } }) { Text("Sync now") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { openSync.launch(arrayOf("*/*")) }) { Text("Change file") }
                    }
                    TextButton(onClick = { confirmUnlink = true }) { Text("Stop syncing") }
                }
            }

            Section("Export & import") {
                OutlinedButton(
                    onClick = { exportCsv.launch("item-tracker-${LocalDate.now()}.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export CSV") }
                OutlinedButton(
                    onClick = { importCsv.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import CSV (replaces all data)") }
                Text(
                    "Columns: name, amazon_url, quantity, in_use_since, use_start, use_end, use_quantity. " +
                        "Dates as YYYY-MM-DD, one row per past use. Pictures aren't included in CSV; " +
                        "items with the same name keep their picture on import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (viewModel.hasBackup()) {
                    TextButton(onClick = { confirmRestore = true }) { Text("Restore data from before the last import") }
                }
            }

            UpdateSection(updateState, onCheck = viewModel::checkForUpdate, onInstall = viewModel::installUpdate)
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Replace all data?",
            text = "Everything in the app will be replaced with the CSV's contents. " +
                "A backup of your current data is saved first.",
            confirmLabel = "Import",
            destructive = true,
            onDismiss = { pendingImport = null },
            onConfirm = { viewModel.importCsv(uri); pendingImport = null },
        )
    }
    if (confirmRestore) ConfirmDialog(
        title = "Restore backup?",
        text = "Your current data will be replaced with the backup made before the last import.",
        confirmLabel = "Restore",
        destructive = true,
        onDismiss = { confirmRestore = false },
        onConfirm = { viewModel.restoreLatestBackup(); confirmRestore = false },
    )
    if (confirmUnlink) ConfirmDialog(
        title = "Stop syncing?",
        text = "Your data stays on this phone and the file stays in Drive. You can link it again any time.",
        confirmLabel = "Stop syncing",
        onDismiss = { confirmUnlink = false },
        onConfirm = { viewModel.stopSync()
            confirmUnlink = false },
    )
}

@Composable
private fun UpdateSection(
    state: MainViewModel.UpdateState,
    onCheck: () -> Unit,
    onInstall: (Updater.Release) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            MainViewModel.UpdateState.Idle -> TextButton(onClick = onCheck) { Text("Check for updates") }
            MainViewModel.UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            MainViewModel.UpdateState.UpToDate -> TextButton(onClick = onCheck) { Text("Up to date. Check again") }
            is MainViewModel.UpdateState.Available -> Button(onClick = { onInstall(state.release) }) {
                Text("Update to ${state.release.version}")
            }
            MainViewModel.UpdateState.Downloading -> Text("Downloading…", style = MaterialTheme.typography.bodySmall)
            MainViewModel.UpdateState.NeedsPermission -> {
                Text(
                    "Allow \"Install unknown apps\" for Item Tracker, then come back and check again.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onCheck) { Text("Check for updates") }
            }
            is MainViewModel.UpdateState.Failed -> {
                Text(state.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onCheck) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NumberSetting(label: String, description: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Text("−")
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}
