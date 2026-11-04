package com.itemtracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itemtracker.app.Amazon
import com.itemtracker.app.ImageUtil
import com.itemtracker.app.MainViewModel
import kotlinx.coroutines.launch

/**
 * Show the add item dialog, optionally pre-filled from a shared Amazon product
 *
 * @param viewModel the [MainViewModel] to use
 * @param prefill the shared link and name, or null
 * @param onDismiss called when the dialog is cancelled
 * @param onAdded called with the name after the item is added
 */
@Composable
fun AddItemDialog(
    viewModel: MainViewModel,
    prefill: Amazon.Shared?,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
) {
    var link by remember { mutableStateOf(prefill?.url.orEmpty()) }
    var name by remember { mutableStateOf(prefill?.title?.let { Amazon.shortName(it) }.orEmpty()) }
    var quantity by remember { mutableIntStateOf(1) }
    var image by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetch() {
        val url = link.trim()
        if (url.isEmpty()) return
        loading = true
        status = null
        scope.launch {
            val result = viewModel.fetchAmazon(url)
            loading = false
            if (result == null) {
                status = "Could not get details from Amazon. Add a picture yourself or just use a name."
                return@launch
            }
            if (name.isBlank() && result.title != null) name = Amazon.shortName(result.title)
            if (result.imageBase64 != null) image = result.imageBase64
            status = if (result.imageBase64 == null) "Got the name, but no picture. You can add one yourself." else null
        }
    }

    // Fill in the details right away when a product was shared into the app
    LaunchedEffect(Unit) {
        if (prefill?.url != null && Amazon.looksLikeAmazon(prefill.url)) fetch()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = viewModel.imageFromUri(uri)
            if (picked == null) {
                status = "Could not read that picture"
                return@launch
            }
            image = picked
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Amazon link (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { fetch() }, enabled = link.isNotBlank() && !loading) {
                        Text("Get details")
                    }
                    Spacer(Modifier.width(12.dp))
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                Text(
                    "Tip: in the Amazon app, tap Share → Subscription Tracker to fill this in automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Short name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuantityRow(quantity) { quantity = it }
                ImageRow(
                    image = image,
                    onChoose = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemove = { image = null },
                )
                status?.let { text ->
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    viewModel.addItem(name, link.ifBlank { null }, quantity, image)
                    onAdded(name.trim())
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Show a stepper for how many units one tracked use covers
 *
 * @param value the current quantity
 * @param onChange called with the new quantity
 */
@Composable
fun QuantityRow(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Quantity", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Units per use / order",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }, enabled = value > 1) { Text("−") }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}

/**
 * Show the picture of an item with buttons to choose, change or remove it
 *
 * @param image the base64 picture, or null
 * @param onChoose called when the user wants to pick a picture
 * @param onRemove called when the user removes the picture
 */
@Composable
fun ImageRow(image: String?, onChoose: () -> Unit, onRemove: () -> Unit) {
    val bitmap = remember(image) { image?.let { ImageUtil.toImageBitmap(it) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                .background(if (bitmap != null) Color.White else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp),
                )
            }
            else {
                Text(
                    "No\npicture",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            OutlinedButton(onClick = onChoose) { Text(if (bitmap == null) "Choose picture" else "Change picture") }
            if (bitmap != null) TextButton(onClick = onRemove) { Text("Remove picture") }
        }
    }
    Spacer(Modifier.height(2.dp))
}
