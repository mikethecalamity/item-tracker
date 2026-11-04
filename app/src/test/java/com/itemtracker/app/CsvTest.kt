package com.itemtracker.app

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

private const val HEADER =
    "name,amazon_url,quantity,in_use_since,subscription,last_delivery,use_start,use_end,use_quantity"
private const val OLD_HEADER = "name,amazon_url,quantity,in_use_since,use_start,use_end,use_quantity"
private const val URL = "https://a.co/d/abc"
private const val IMAGE = "aW1hZ2U="
private val START = LocalDate.of(2026, 9, 1)
private val END = LocalDate.of(2026, 9, 29)
private val IN_USE_SINCE = LocalDate.of(2026, 10, 1)
private val LAST_DELIVERY = LocalDate.of(2026, 9, 28)

/** Tests for [Csv] */
internal class CsvTest {

    @Test
    fun exportTest() {
        val pods = Item(
            name = "Coffee pods",
            amazonUrl = URL,
            quantity = 2,
            activeStart = IN_USE_SINCE,
            history = listOf(UsageEntry(start = START, end = END, quantity = 2)),
            subscription = "2 months",
            lastDelivery = LAST_DELIVERY,
        )
        val deleted = Item(name = "Gone", deleted = true)

        assertThat(Csv.export(AppData(listOf(pods, deleted)))).isEqualTo(
            "$HEADER\r\nCoffee pods,$URL,2,2026-10-01,2 months,2026-09-28,2026-09-01,2026-09-29,2\r\n",
        )
    }

    @Test
    fun exportTestNoHistoryAndEscaping() {
        val item = Item(name = "Soap, \"big\" pack")

        assertThat(Csv.export(AppData(listOf(item)))).isEqualTo("$HEADER\r\n\"Soap, \"\"big\"\" pack\",,1,,,,,,\r\n")
    }

    @Test
    fun parseTest() {
        assertThat(Csv.parse("a,b\r\nc,d\n")).containsExactly(listOf("a", "b"), listOf("c", "d"))
    }

    @Test
    fun parseTestQuotes() {
        assertThat(Csv.parse("﻿\"x, \"\"y\"\"\",\"line\nbreak\"\n\n,\n")).containsExactly(
            listOf("x, \"y\"", "line\nbreak"),
        )
    }

    @Test
    fun parseTestNoTrailingNewline() {
        assertThat(Csv.parse("a,b")).containsExactly(listOf("a", "b"))
    }

    @Test
    fun importItemsTest() {
        val existing = listOf(Item(name = "coffee pods", imageBase64 = IMAGE))
        val csv = "$HEADER\nCoffee pods,$URL,2,2026-10-01,2 months,2026-09-28,2026-09-01,2026-09-29,2\n" +
            "Coffee pods,$URL,2,2026-10-01,2 months,2026-09-28,2026-08-01,2026-08-31,\nSoap,,,,every day,,,,\n"

        val items = Csv.importItems(csv, existing)

        assertThat(items.map(Item::name)).containsExactly("Coffee pods", "Soap")
        assertThat(items.first())
            .returns(URL, Item::amazonUrl)
            .returns(IMAGE, Item::imageBase64)
            .returns(2, Item::quantity)
            .returns(IN_USE_SINCE, Item::activeStart)
            .returns("2 months", Item::subscription)
            .returns(LAST_DELIVERY, Item::lastDelivery)
        val history = items.first().history.map { entry -> Triple(entry.start, entry.end, entry.quantity) }
        assertThat(history).containsExactly(
            Triple(START, END, 2),
            Triple(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 1),
        )
        assertThat(items.last())
            .returns(null, Item::amazonUrl)
            .returns(null, Item::imageBase64)
            .returns(1, Item::quantity)
            .returns(null, Item::subscription)
            .returns(emptyList<UsageEntry>(), Item::history)
    }

    @Test
    fun importItemsTestOldFormat() {
        val items = Csv.importItems("$OLD_HEADER\nSoap,,1,2026-10-01,2026-09-01,2026-09-29,1\n", emptyList())

        assertThat(items).singleElement()
            .returns(IN_USE_SINCE, Item::activeStart)
            .returns(null, Item::subscription)
            .returns(null, Item::lastDelivery)
        assertThat(items.single().history.map(UsageEntry::end)).containsExactly(END)
    }

    @Test
    fun importItemsTestEmpty() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { Csv.importItems("", emptyList()) }
            .withMessage("The file is empty")
    }

    @Test
    fun importItemsTestNoNameColumn() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { Csv.importItems("title,quantity\nx,1\n", emptyList()) }
            .withMessageStartingWith("Missing a \"name\" column")
    }

    @Test
    fun importItemsTestBadDate() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { Csv.importItems("$HEADER\nSoap,,1,,,,9/1/2026,2026-09-29,1\n", emptyList()) }
            .withMessage("Row 2: \"9/1/2026\" in use_start is not a date like 2026-01-31")
    }

    @Test
    fun importItemsTestMissingEndDate() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { Csv.importItems("$HEADER\nSoap,,1,,,,2026-09-01,,1\n", emptyList()) }
            .withMessage("Row 2: needs both use_start and use_end")
    }

    @Test
    fun importItemsTestEndBeforeStart() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { Csv.importItems("$HEADER\nSoap,,1,,,,2026-09-29,2026-09-01,1\n", emptyList()) }
            .withMessage("Row 2: use_end is before use_start")
    }
}
