package com.itemtracker.app

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Export and import items as CSV
 *
 * One row per past use; items with no history get one row with blank use columns. Dates are YYYY-MM-DD. Images are
 * not included.
 */
object Csv {
    private const val NAME = "name"
    private const val AMAZON_URL = "amazon_url"
    private const val QUANTITY = "quantity"
    private const val IN_USE_SINCE = "in_use_since"
    private const val USE_START = "use_start"
    private const val USE_END = "use_end"
    private const val USE_QUANTITY = "use_quantity"
    private const val LINE_END = "\r\n"
    private val HEADER = listOf(NAME, AMAZON_URL, QUANTITY, IN_USE_SINCE, USE_START, USE_END, USE_QUANTITY)

    /**
     * Export the items that are not deleted
     *
     * @param data the [AppData] to export
     * @return the CSV text
     */
    fun export(data: AppData): String {
        val text = StringBuilder().append(HEADER.joinToString(",")).append(LINE_END)
        data.liveItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Item::name)).forEach { item ->
            val itemCells = listOf(
                item.name, item.amazonUrl.orEmpty(), item.quantity.toString(), item.activeStart?.toString().orEmpty(),
            )
            val entries = item.liveHistory.sortedBy(UsageEntry::start)
            if (entries.isEmpty()) {
                text.append(row(itemCells + listOf("", "", ""))).append(LINE_END)
            }
            entries.forEach { entry ->
                val entryCells = listOf(entry.start.toString(), entry.end.toString(), entry.quantity.toString())
                text.append(row(itemCells + entryCells)).append(LINE_END)
            }
        }
        return text.toString()
    }

    /**
     * Split CSV text into rows of cells, handling quotes, escaped quotes and newlines inside quotes
     *
     * @param text the CSV text
     * @return the rows, without blank rows
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        val content = text.removePrefix("﻿")
        var index = 0
        while (index < content.length) {
            val character = content[index]
            if (inQuotes) {
                val escapedQuote = character == '"' && index + 1 < content.length && content[index + 1] == '"'
                when {
                    escapedQuote -> {
                        cell.append('"')
                        index++
                    }
                    character == '"' -> inQuotes = false
                    else -> cell.append(character)
                }
            }
            else {
                when (character) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(cell.toString())
                        cell.clear()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(cell.toString())
                        cell.clear()
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> cell.append(character)
                }
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.filter { cells -> cells.any(String::isNotBlank) }
    }

    /**
     * Build items from CSV text
     *
     * @param text the CSV text
     * @param existing the current items, used only to keep pictures for items with the same name
     * @return the new items
     * @throws IllegalArgumentException if the CSV is empty, has no name column or holds an invalid row
     */
    fun importItems(text: String, existing: List<Item>): List<Item> {
        val rows = parse(text)
        if (rows.isEmpty()) throw IllegalArgumentException("The file is empty")
        val headers = rows.first().map { header -> header.trim().lowercase() }
        val columns = HEADER.associateWith(headers::indexOf)
        if (columns.getValue(NAME) == -1) {
            throw IllegalArgumentException("Missing a \"name\" column, expected: ${HEADER.joinToString(",")}")
        }
        val imagesByName = existing.filter { item -> !item.deleted && item.imageBase64 != null }
            .associate { item -> item.name.lowercase() to item.imageBase64 }
        val rowsByName = LinkedHashMap<String, MutableList<CsvRow>>()
        rows.drop(1).forEachIndexed { index, cells ->
            // +2: one for the header, one because spreadsheet rows count from 1
            val csvRow = CsvRow(index + 2, cells, columns)
            val name = csvRow.cell(NAME)
            if (name.isNotEmpty()) rowsByName.getOrPut(name) { mutableListOf() }.add(csvRow)
        }
        return rowsByName.map { (name, itemRows) ->
            val first = itemRows.first()
            Item(
                name = name,
                amazonUrl = first.cell(AMAZON_URL).ifEmpty { null },
                imageBase64 = imagesByName[name.lowercase()],
                quantity = first.cell(QUANTITY).toIntOrNull()?.coerceAtLeast(1) ?: 1,
                activeStart = first.date(IN_USE_SINCE),
                history = itemRows.mapNotNull(CsvRow::toEntry),
            )
        }
    }

    private fun row(cells: List<String>): String = cells.joinToString(",", transform = ::escape)

    private fun escape(cell: String): String =
        if (cell.any { character -> character in ",\"\n\r" }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    /** One parsed data row, with its spreadsheet line number for error messages */
    private class CsvRow(val line: Int, val cells: List<String>, val columns: Map<String, Int>) {
        fun cell(column: String): String = cells.getOrNull(columns.getValue(column))?.trim().orEmpty()

        fun date(column: String): LocalDate? {
            val value = cell(column)
            if (value.isEmpty()) return null
            return try {
                LocalDate.parse(value)
            }
            catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Row $line: \"$value\" in $column is not a date like 2026-01-31", e)
            }
        }

        fun toEntry(): UsageEntry? {
            val start = date(USE_START)
            val end = date(USE_END)
            return when {
                start == null && end == null -> null
                start == null || end == null ->
                    throw IllegalArgumentException("Row $line: needs both $USE_START and $USE_END")
                end.isBefore(start) -> throw IllegalArgumentException("Row $line: $USE_END is before $USE_START")
                else -> {
                    val quantity = cell(USE_QUANTITY).toIntOrNull()?.coerceAtLeast(1) ?: 1
                    UsageEntry(start = start, end = end, quantity = quantity)
                }
            }
        }
    }
}
