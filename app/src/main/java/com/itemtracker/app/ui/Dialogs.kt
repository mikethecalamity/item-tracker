package com.itemtracker.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Show a calendar limited to a date range
 *
 * @param title the heading above the calendar
 * @param initial the date selected when the calendar opens
 * @param minDate the first selectable date, or null for no limit
 * @param maxDate the last selectable date, or null for no limit
 * @param onDismiss called when the calendar is cancelled
 * @param onPick called with the chosen date
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(
    title: String,
    initial: LocalDate,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = LocalDate.now(),
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val minMs = minDate?.toUtcMillis()
    val maxMs = maxDate?.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (minMs == null || utcTimeMillis >= minMs) && (maxMs == null || utcTimeMillis <= maxMs)

            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(it.toLocalDateUtc()) } },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/**
 * Show a confirmation dialog
 *
 * @param title the dialog title
 * @param text the explanation
 * @param confirmLabel the confirm button label
 * @param destructive whether to color the confirm button as a warning
 * @param onDismiss called when the dialog is cancelled
 * @param onConfirm called when the user confirms
 * @param extraButton an optional extra button shown before Cancel
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    extraButton: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(confirmLabel, color = color)
            }
        },
        dismissButton = {
            Row {
                extraButton?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
