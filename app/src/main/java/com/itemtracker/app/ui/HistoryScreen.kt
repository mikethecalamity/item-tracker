package com.itemtracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itemtracker.app.Calc
import com.itemtracker.app.Item
import com.itemtracker.app.MainViewModel
import com.itemtracker.app.UsageEntry
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Show the details, estimate and editable history of one item
 *
 * @param viewModel the [MainViewModel] to use
 * @param itemId the id of the item to show
 * @param onBack called to return to the main screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, itemId: String, onBack: () -> Unit) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val item = data.items.firstOrNull { it.id == itemId && !it.deleted }
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editDetails by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<UsageEntry?>(null) }
    var addEntry by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<UsageEntry?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var imageBusy by remember { mutableStateOf(false) }
    var imageStatus by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val image = viewModel.imageFromUri(uri)
            if (image == null) {
                imageStatus = "Could not read that picture"
                return@launch
            }
            viewModel.setImage(item.id, image)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = { editDetails = true }) { Text("Edit") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    ImageRow(
                        image = item.imageBase64,
                        onChoose = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRemove = { viewModel.setImage(item.id, null) },
                    )
                    if (item.amazonUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !imageBusy, onClick = {
                                imageBusy = true; imageStatus = null
                                scope.launch {
                                    val result = viewModel.fetchAmazon(item.amazonUrl)
                                    imageBusy = false
                                    val image = result?.imageBase64
                                    if (image == null) {
                                        imageStatus = "Could not get a picture from Amazon"
                                        return@launch
                                    }
                                    viewModel.setImage(item.id, image)
                                }
                            }) { Text("Get picture from Amazon") }
                            if (imageBusy) {
                                CircularProgressIndicator(
                                    Modifier.padding(start = 4.dp).width(18.dp).height(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    imageStatus?.let { text ->
                        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { SummaryCard(item, settings, onCancelCurrent = { confirmCancel = true }) }

            if (item.amazonUrl != null) {
                item {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.amazonUrl)))
                            }
                            catch (e: Exception) { /* no browser */ }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reorder on Amazon")
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addEntry = true }) { Text("+ Add past use") }
                }
                Text(
                    "Tap an entry to edit it. Swipe left to delete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val history = item.liveHistory
            if (history.isEmpty()) {
                item {
                    Text(
                        "No finished uses yet. Tap the item on the main screen when you start one, " +
                            "and again when it runs out.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history, key = UsageEntry::id) { entry ->
                SwipeEntryRow(
                    entry = entry,
                    onClick = { editEntry = entry },
                    onRequestDelete = { deleteEntry = entry },
                )
            }
        }
    }

    if (editDetails) EditDetailsDialog(item, onDismiss = { editDetails = false }) { name, link, quantity ->
        viewModel.updateItem(item.id, name, link, quantity)
        editDetails = false
    }

    if (addEntry) EntryDialog(
        title = "Add past use",
        initialStart = LocalDate.now().minusDays(28),
        initialEnd = LocalDate.now(),
        initialQuantity = item.quantity,
        onDismiss = { addEntry = false },
    ) { start, end, quantity ->
        viewModel.addEntry(item.id, start, end, quantity)
        addEntry = false
    }

    editEntry?.let { entry ->
        EntryDialog(
            title = "Edit use",
            initialStart = entry.start,
            initialEnd = entry.end,
            initialQuantity = entry.quantity,
            onDismiss = { editEntry = null },
        ) { start, end, quantity ->
            viewModel.updateEntry(item.id, entry.copy(start = start, end = end, quantity = quantity))
            editEntry = null
        }
    }

    deleteEntry?.let { entry ->
        ConfirmDialog(
            title = "Delete this use?",
            text = "${Calc.longDate(entry.start)} → ${Calc.longDate(entry.end)} will be removed from the average.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteEntry = null },
            onConfirm = { viewModel.deleteEntry(item.id, entry.id); deleteEntry = null },
        )
    }

    if (confirmCancel) ConfirmDialog(
        title = "Cancel current use?",
        text = "Use this if you started it by mistake. Nothing is added to the history.",
        confirmLabel = "Cancel use",
        destructive = true,
        onDismiss = { confirmCancel = false },
        onConfirm = { viewModel.cancelCurrent(item.id); confirmCancel = false },
    )
}

@Composable
private fun SummaryCard(item: Item, settings: com.itemtracker.app.Settings, onCancelCurrent: () -> Unit) {
    val today = LocalDate.now()
    val avg = Calc.averageDays(item)
    val perUnit = Calc.averageDaysPerUnit(item)
    val estimate = Calc.estimate(item, settings, today)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabeledValue("Average", if (avg == null) "— (no finished uses yet)" else Calc.roundedLabel(avg))
            if (avg != null && perUnit != null) {
                Text(
                    "Exact: %.1f days".format(avg) +
                        if (item.quantity > 1) " (%.1f per unit × %d)".format(perUnit, item.quantity) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LabeledValue("Quantity", item.quantity.toString())
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (item.activeStart != null) {
                LabeledValue("In use since", Calc.longDate(item.activeStart))
                if (estimate != null) {
                    LabeledValue("Est. run-out", Calc.longDate(estimate.runOut))
                    LabeledValue(
                        "Est. order date",
                        Calc.longDate(estimate.orderDate) + when {
                            estimate.daysUntilOrder < 0 -> " (overdue)"
                            estimate.daysUntilOrder == 0L -> " (today)"
                            else -> " (in ${estimate.daysUntilOrder} days)"
                        },
                        highlight = estimate.daysUntilOrder <= settings.dueSoonDays,
                    )
                }
                else {
                    Text(
                        "An order date appears after the first finished use.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onCancelCurrent, contentPadding = PaddingValues(0.dp)) {
                    Text("Started by mistake? Cancel current use")
                }
            }
            else {
                Text(
                    "Not in use. Tap the item on the main screen to start it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, highlight: Boolean = false) {
    Row {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) DueOrange else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeEntryRow(entry: UsageEntry, onClick: () -> Unit, onRequestDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRequestDelete()
            // Always snap back; the confirmation dialog does the actual delete
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        val days = Calc.daysBetween(entry.start, entry.end)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${Calc.longDate(entry.start)} → ${Calc.longDate(entry.end)}")
                Text(
                    "$days days" + if (entry.quantity > 1) " · qty ${entry.quantity}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Calc.roundedLabel(days.toDouble()), fontWeight = FontWeight.SemiBold)
        }
    }
    HorizontalDivider()
}

@Composable
private fun EditDetailsDialog(item: Item, onDismiss: () -> Unit, onSave: (String, String?, Int) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var link by remember { mutableStateOf(item.amazonUrl.orEmpty()) }
    var quantity by remember { mutableIntStateOf(item.quantity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Short name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    link,
                    { link = it },
                    label = { Text("Amazon link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuantityRow(quantity) { quantity = it }
                if (quantity != item.quantity) {
                    Text(
                        "The average will scale with the new quantity. Past uses keep the quantity they had.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, link.ifBlank { null }, quantity) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Date being picked in the entry dialog */
private enum class DateField { START, END }

@Composable
private fun EntryDialog(
    title: String,
    initialStart: LocalDate,
    initialEnd: LocalDate,
    initialQuantity: Int,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate, Int) -> Unit,
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var quantity by remember { mutableIntStateOf(initialQuantity) }
    var picking by remember { mutableStateOf<DateField?>(null) }
    val valid = !end.isBefore(start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Started", Modifier.weight(1f))
                    OutlinedButton(onClick = { picking = DateField.START }) { Text(Calc.longDate(start)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ran out", Modifier.weight(1f))
                    OutlinedButton(onClick = { picking = DateField.END }) { Text(Calc.longDate(end)) }
                }
                QuantityRow(quantity) { quantity = it }
                val days = Calc.daysBetween(start, end)
                Text(
                    if (valid) "$days days → ${Calc.roundedLabel(days.toDouble())}"
                    else "End date is before the start date.",
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(start, end, quantity) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picking) {
        null -> Unit
        DateField.START -> DatePick(
            title = "Started on",
            initial = start,
            maxDate = LocalDate.now(),
            onDismiss = { picking = null },
            onPick = { start = it; picking = null },
        )
        DateField.END -> DatePick(
            title = "Ran out on",
            initial = end,
            minDate = start,
            maxDate = LocalDate.now(),
            onDismiss = { picking = null },
            onPick = { end = it; picking = null },
        )
    }
}
