package com.itemtracker.app

import java.time.LocalDate
import java.util.UUID

/**
 * Record one finished use of an item, from the day it was started to the day it ran out
 *
 * @property quantity how many units the use covered, so averages can be computed per unit
 * @property updatedAt last change time in epoch milliseconds, used when merging with the sync file
 * @property deleted whether the entry was deleted; kept as a tombstone so deletes reach the sync file
 */
data class UsageEntry(
    val id: String = UUID.randomUUID().toString(),
    val start: LocalDate,
    val end: LocalDate,
    val quantity: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/**
 * Hold a tracked household item and its usage history
 *
 * @property imageBase64 small base64 JPEG picture, stored inline so it syncs with the data
 * @property activeStart the day the current use started, or null when the item is not in use
 * @property updatedAt last change time in epoch milliseconds, used when merging with the sync file
 * @property deleted whether the item was deleted; kept as a tombstone so deletes reach the sync file
 */
data class Item(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amazonUrl: String? = null,
    val imageBase64: String? = null,
    val quantity: Int = 1,
    val activeStart: LocalDate? = null,
    val history: List<UsageEntry> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val isActive: Boolean get() = activeStart != null

    /** Get the history entries that are not deleted, newest first */
    val liveHistory: List<UsageEntry>
        get() = history.filterNot(UsageEntry::deleted).sortedByDescending(UsageEntry::start)
}

/** Hold all items, including deleted tombstones */
data class AppData(val items: List<Item> = emptyList()) {
    /** Get the items that are not deleted */
    val liveItems: List<Item> get() = items.filterNot(Item::deleted)
}

/**
 * Hold the user settings
 *
 * @property orderDay day of the month orders happen on, clamped to the last day of shorter months
 * @property graceDays how many days the user can go without an item before the next order day
 * @property dueSoonDays show the due badge when the order date is this many days away or less
 * @property syncUri document URI of the sync file, or null when sync is off
 */
data class Settings(
    val orderDay: Int = 1,
    val graceDays: Int = 7,
    val dueSoonDays: Int = 7,
    val syncUri: String? = null,
)
