package com.itemtracker.app

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val ITEM_ID = "item-1"
private const val ENTRY_ID = "entry-1"
private val ENTRY = UsageEntry(
    id = ENTRY_ID, start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 29), quantity = 2, updatedAt = 100,
)
private val ITEM = Item(
    id = ITEM_ID,
    name = "Coffee pods",
    amazonUrl = "https://a.co/d/abc",
    imageBase64 = "aW1hZ2U=",
    quantity = 2,
    activeStart = LocalDate.of(2026, 10, 1),
    history = listOf(ENTRY),
    updatedAt = 100,
)

/** Tests for [Store] */
internal class StoreTest {

    @Test
    fun toJsonTestRoundTrip() {
        val data = AppData(listOf(ITEM, Item(id = "item-2", name = "Soap", deleted = true, updatedAt = 5)))

        assertThat(Store.fromJson(Store.toJson(data))).isEqualTo(data)
    }

    @Test
    fun fromJsonTestBlank() {
        assertThat(Store.fromJson("  ")).isEqualTo(AppData())
    }

    @Test
    fun fromJsonTestNotTrackerData() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { Store.fromJson("""{"items":[{"name":"no id"}]}""") }
            .withMessage("Not Subscription Tracker data")
    }

    @Test
    fun fromJsonTestNotJson() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy { Store.fromJson("hello") }
    }

    @Test
    fun mergeTestNewerItemWins() {
        val renamed = ITEM.copy(name = "Pods", updatedAt = 200, history = emptyList())

        val merged = Store.merge(AppData(listOf(ITEM)), AppData(listOf(renamed)))

        assertThat(merged.items).singleElement()
            .returns("Pods", Item::name)
            .returns(listOf(ENTRY), Item::history)
    }

    @Test
    fun mergeTestKeepsItemsFromBothSides() {
        val other = Item(id = "item-2", name = "Soap")

        val merged = Store.merge(AppData(listOf(ITEM)), AppData(listOf(other)))

        assertThat(merged.items.map(Item::id)).containsExactly(ITEM_ID, "item-2")
    }

    @Test
    fun mergeTestNewerEntryAndDeleteWin() {
        val deletedEntry = ENTRY.copy(deleted = true, updatedAt = 300)
        val newEntry = UsageEntry(id = "entry-2", start = LocalDate.of(2026, 8, 1), end = LocalDate.of(2026, 8, 30))

        val merged = Store.merge(
            AppData(listOf(ITEM.copy(history = listOf(ENTRY, newEntry)))),
            AppData(listOf(ITEM.copy(history = listOf(deletedEntry)))),
        )

        assertThat(merged.items).singleElement().returns(listOf(deletedEntry, newEntry), Item::history)
    }

    @Test
    fun mergeTestDeletedItemWins() {
        val deleted = ITEM.copy(deleted = true, updatedAt = 200)

        val merged = Store.merge(AppData(listOf(deleted)), AppData(listOf(ITEM)))

        assertThat(merged.liveItems).isEmpty()
    }
}
