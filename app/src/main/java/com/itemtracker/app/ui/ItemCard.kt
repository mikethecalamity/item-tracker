package com.itemtracker.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itemtracker.app.Calc
import com.itemtracker.app.ImageUtil
import com.itemtracker.app.Item
import com.itemtracker.app.Settings
import java.time.LocalDate

/**
 * Show one item tile with its picture or name, average, in-use state, due badge and corner buttons
 *
 * @param item the [Item] to show
 * @param settings the [Settings] to use
 * @param onTap called when the tile is tapped
 * @param onLongPress called when the tile is long-pressed
 * @param onHistory called when the history button is tapped
 * @param onDelete called when the delete button is tapped
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    item: Item,
    settings: Settings,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (item.isActive) InUseGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val bitmap = remember(item.imageBase64) { item.imageBase64?.let { ImageUtil.toImageBitmap(it) } }
    val today = LocalDate.now()
    val estimate = Calc.estimate(item, settings, today)
    val dueSoon = estimate != null && estimate.daysUntilOrder <= settings.dueSoonDays

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(shape)
            .border(if (item.isActive) 4.dp else 2.dp, borderColor, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.White),
                    )
                }
                else {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                Calc.averageLabel(item),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                item.activeStart?.let { "in use since " + Calc.shortDate(it, today) } ?: "not in use",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.isActive) InUseGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // Room for the corner buttons
            Spacer(Modifier.height(30.dp))
        }

        if (dueSoon && estimate != null) {
            Text(
                if (estimate.daysUntilOrder <= 0) "Order now" else "Order " + Calc.shortDate(estimate.orderDate, today),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DueOrange)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        IconButton(
            onClick = onHistory,
            modifier = Modifier.align(Alignment.BottomStart).padding(2.dp).size(36.dp),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "History", modifier = Modifier.size(20.dp))
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(36.dp),
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
