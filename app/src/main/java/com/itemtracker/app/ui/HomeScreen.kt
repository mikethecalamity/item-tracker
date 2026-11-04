package com.itemtracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.itemtracker.app.Amazon
import com.itemtracker.app.Calc
import com.itemtracker.app.Item
import com.itemtracker.app.MainViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Show the item grid and handle starting, stopping, dating and deleting items
 *
 * @param viewModel the [MainViewModel] to use
 * @param onOpenHistory called with the item id when its history button is tapped
 * @param onOpenSettings called when the settings button is tapped
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onOpenHistory: (String) -> Unit, onOpenSettings: () -> Unit) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingShare by viewModel.pendingShare.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var toStop by remember { mutableStateOf<Item?>(null) }
    var stopOnOtherDay by remember { mutableStateOf<Item?>(null) }
    var toDelete by remember { mutableStateOf<Item?>(null) }
    var pickStartFor by remember { mutableStateOf<Item?>(null) }
    var addPrefill by remember { mutableStateOf<Amazon.Shared?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(pendingShare) {
        pendingShare?.let { addPrefill = it; showAdd = true; viewModel.consumeShare() }
    }

    val sortedItems = Calc.sorted(data.liveItems, settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription Tracker") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addPrefill = null; showAdd = true },
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Add item", modifier = Modifier.size(32.dp)) }
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (sortedItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No items yet.\n\nTap + to add one, or use Share → Subscription Tracker " +
                        "on a product in the Amazon app.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sortedItems, key = Item::id) { item ->
                    ItemCard(
                        item = item,
                        settings = settings,
                        onTap = {
                            if (item.isActive) {
                                toStop = item
                            }
                            else {
                                viewModel.start(item.id)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        "Started ${item.name} today",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.cancelCurrent(item.id)
                                }
                            }
                        },
                        onLongPress = { pickStartFor = item },
                        onHistory = { onOpenHistory(item.id) },
                        onDelete = { toDelete = item },
                    )
                }
            }
        }
    }

    toStop?.let { item ->
        val start = item.activeStart ?: LocalDate.now()
        val days = Calc.daysBetween(start, LocalDate.now())
        ConfirmDialog(
            title = "Stop ${item.name}?",
            text = "Ran out today?\n\nIn use since ${Calc.longDate(start)}: $days day${if (days == 1L) "" else "s"} " +
                "(${Calc.roundedLabel(days.toDouble())}). This will be added to the average.",
            confirmLabel = "Stop",
            onDismiss = { toStop = null },
            onConfirm = { viewModel.stop(item.id); toStop = null },
            extraButton = {
                TextButton(onClick = { stopOnOtherDay = item; toStop = null }) { Text("Other day…") }
            },
        )
    }

    stopOnOtherDay?.let { item ->
        DatePick(
            title = "When did ${item.name} run out?",
            initial = LocalDate.now(),
            minDate = item.activeStart,
            maxDate = LocalDate.now(),
            onDismiss = { stopOnOtherDay = null },
            onPick = { viewModel.stop(item.id, it); stopOnOtherDay = null },
        )
    }

    pickStartFor?.let { item ->
        DatePick(
            title = if (item.isActive) "Change start date for ${item.name}" else "When did you start ${item.name}?",
            initial = item.activeStart ?: LocalDate.now(),
            maxDate = LocalDate.now(),
            onDismiss = { pickStartFor = null },
            onPick = { viewModel.start(item.id, it); pickStartFor = null },
        )
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.name}?",
            text = "This removes the item and all of its history.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { toDelete = null },
            onConfirm = { viewModel.deleteItem(item.id); toDelete = null },
        )
    }

    if (showAdd) {
        AddItemDialog(
            viewModel = viewModel,
            prefill = addPrefill,
            onDismiss = { showAdd = false; addPrefill = null },
            onAdded = { name ->
                showAdd = false; addPrefill = null
                scope.launch { snackbar.showSnackbar("Added $name. Tap it when you start using one.") }
            },
        )
    }
}
