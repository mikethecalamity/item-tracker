package com.itemtracker.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Serialize, store, back up and merge the app data */
object Store {
    private const val DATA_FILE = "data.json"
    private const val BACKUP_DIRECTORY = "backups"
    private const val BACKUPS_KEPT = 10
    private val BACKUP_STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * Convert the data to JSON
     *
     * @param data the [AppData] to convert
     * @return the JSON text
     */
    fun toJson(data: AppData): String {
        val itemsJson = JSONArray()
        data.items.forEach { item ->
            val historyJson = JSONArray()
            item.history.forEach { entry ->
                historyJson.put(JSONObject().apply {
                    put("id", entry.id)
                    put("start", entry.start.toString())
                    put("end", entry.end.toString())
                    put("quantity", entry.quantity)
                    put("updatedAt", entry.updatedAt)
                    put("deleted", entry.deleted)
                })
            }
            itemsJson.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("amazonUrl", item.amazonUrl ?: JSONObject.NULL)
                put("imageBase64", item.imageBase64 ?: JSONObject.NULL)
                put("quantity", item.quantity)
                put("activeStart", item.activeStart?.toString() ?: JSONObject.NULL)
                put("subscription", item.subscription ?: JSONObject.NULL)
                put("lastDelivery", item.lastDelivery?.toString() ?: JSONObject.NULL)
                put("updatedAt", item.updatedAt)
                put("deleted", item.deleted)
                put("history", historyJson)
            })
        }
        return JSONObject().apply {
            put("version", 1)
            put("items", itemsJson)
        }.toString()
    }

    /**
     * Read data from JSON
     *
     * @param text the JSON text
     * @return the data, empty when the text is blank
     * @throws IllegalArgumentException if the text is not Subscription Tracker data
     */
    fun fromJson(text: String): AppData {
        if (text.isBlank()) return AppData()
        try {
            val itemsJson = JSONObject(text).optJSONArray("items") ?: JSONArray()
            return AppData((0 until itemsJson.length()).map { index -> itemFromJson(itemsJson.getJSONObject(index)) })
        }
        catch (e: JSONException) {
            throw IllegalArgumentException("Not Subscription Tracker data", e)
        }
        catch (e: RuntimeException) {
            throw IllegalArgumentException("Not Subscription Tracker data", e)
        }
    }

    /**
     * Load the data saved on this phone
     *
     * @param context the [Context] to use
     * @return the data, empty when nothing was saved or the file is unreadable
     */
    fun loadLocal(context: Context): AppData {
        val file = File(context.filesDir, DATA_FILE)
        if (!file.exists()) return AppData()
        return try {
            fromJson(file.readText())
        }
        catch (e: IllegalArgumentException) {
            AppData()
        }
    }

    /**
     * Save the data on this phone, writing to a temporary file first so a crash cannot leave half a file
     *
     * @param context the [Context] to use
     * @param data the [AppData] to save
     */
    fun saveLocal(context: Context, data: AppData) {
        val file = File(context.filesDir, DATA_FILE)
        val temporaryFile = File(context.filesDir, "$DATA_FILE.tmp")
        temporaryFile.writeText(toJson(data))
        if (temporaryFile.renameTo(file)) return
        file.writeText(temporaryFile.readText())
        temporaryFile.delete()
    }

    /**
     * Save a backup copy of the data and keep only the newest backups
     *
     * @param context the [Context] to use
     * @param data the [AppData] to back up
     * @return the backup file
     */
    fun backup(context: Context, data: AppData): File {
        val directory = File(context.filesDir, BACKUP_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "backup-${LocalDateTime.now().format(BACKUP_STAMP_FORMAT)}.json")
        file.writeText(toJson(data))
        directory.listFiles()?.sortedByDescending(File::getName)?.drop(BACKUPS_KEPT)?.forEach(File::delete)
        return file
    }

    /**
     * Find the newest backup
     *
     * @param context the [Context] to use
     * @return the backup file, or null when there is none
     */
    fun latestBackup(context: Context): File? =
        File(context.filesDir, BACKUP_DIRECTORY).listFiles()?.maxByOrNull(File::getName)

    /**
     * Read a document, such as the sync file in Google Drive
     *
     * @param context the [Context] to use
     * @param uri the document URI
     * @return the text, empty when the document is empty
     */
    fun readUri(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes().toString(Charsets.UTF_8) }
            ?: ""

    /**
     * Replace the contents of a document
     *
     * @param context the [Context] to use
     * @param uri the document URI
     * @param text the new contents
     * @throws IllegalStateException if the document cannot be opened for writing
     */
    fun writeUri(context: Context, uri: Uri, text: String) {
        // "wt" truncates; a few providers only accept "w"
        val output = try {
            context.contentResolver.openOutputStream(uri, "wt")
        }
        catch (e: IllegalArgumentException) {
            context.contentResolver.openOutputStream(uri, "w")
        }
        ?: throw IllegalStateException("Cannot open the sync file for writing")
        output.use { stream -> stream.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Combine two copies of the data so neither side loses changes
     *
     * For every item and every history entry, the most recently changed version wins. Deletes win too, because
     * they are kept as tombstones with their own timestamp.
     *
     * @param first the data on this phone
     * @param second the data in the sync file
     * @return the merged data
     */
    fun merge(first: AppData, second: AppData): AppData {
        val firstById = first.items.associateBy(Item::id)
        val secondById = second.items.associateBy(Item::id)
        val ids = LinkedHashSet<String>().apply {
            addAll(firstById.keys)
            addAll(secondById.keys)
        }
        return AppData(ids.map { id -> mergeItem(firstById[id], secondById[id]) })
    }

    private fun itemFromJson(json: JSONObject): Item {
        val historyJson = json.optJSONArray("history") ?: JSONArray()
        val history = (0 until historyJson.length()).map { index ->
            val entryJson = historyJson.getJSONObject(index)
            UsageEntry(
                id = entryJson.getString("id"),
                start = LocalDate.parse(entryJson.getString("start")),
                end = LocalDate.parse(entryJson.getString("end")),
                quantity = entryJson.optInt("quantity", 1),
                updatedAt = entryJson.optLong("updatedAt", 0L),
                deleted = entryJson.optBoolean("deleted", false),
            )
        }
        return Item(
            id = json.getString("id"),
            name = json.optString("name", "Item"),
            amazonUrl = json.optStringOrNull("amazonUrl"),
            imageBase64 = json.optStringOrNull("imageBase64"),
            quantity = json.optInt("quantity", 1),
            activeStart = json.optStringOrNull("activeStart")?.let(LocalDate::parse),
            subscription = json.optStringOrNull("subscription"),
            lastDelivery = json.optStringOrNull("lastDelivery")?.let(LocalDate::parse),
            history = history,
            updatedAt = json.optLong("updatedAt", 0L),
            deleted = json.optBoolean("deleted", false),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun mergeItem(first: Item?, second: Item?): Item = when {
        first == null -> requireNotNull(second)
        second == null -> first
        else -> {
            val newer = if (second.updatedAt > first.updatedAt) second else first
            newer.copy(history = mergeEntries(first.history, second.history))
        }
    }

    private fun mergeEntries(first: List<UsageEntry>, second: List<UsageEntry>): List<UsageEntry> {
        val byId = LinkedHashMap<String, UsageEntry>()
        (first + second).forEach { entry ->
            val existing = byId[entry.id]
            if (existing == null || entry.updatedAt > existing.updatedAt) byId[entry.id] = entry
        }
        return byId.values.toList()
    }
}
