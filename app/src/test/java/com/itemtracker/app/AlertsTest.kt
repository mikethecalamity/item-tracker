package com.itemtracker.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 3)
private val SETTINGS = Settings(orderDay = 8, graceDays = 7, headsUpDays = 7)
private val PAST_USE_END = LocalDate.of(2026, 8, 1)

/** Tests for [Alerts] */
internal class AlertsTest {

    @Test
    fun forTodayTestSurplus() {
        val item = item(56).copy(
            subscription = "1 month",
            activeStart = LocalDate.of(2026, 10, 20),
            lastDelivery = LocalDate.of(2026, 10, 8),
        )

        assertThat(Alerts.forToday(listOf(item), SETTINGS, TODAY))
            .containsExactly("Skip Coffee pods in the Nov 8 delivery? You won't need it until about Dec 15.")
    }

    @Test
    fun forTodayTestShortfall() {
        val item = item(28).copy(
            subscription = "2 months",
            activeStart = LocalDate.of(2026, 10, 20),
            lastDelivery = LocalDate.of(2026, 10, 8),
        )

        assertThat(Alerts.forToday(listOf(item), SETTINGS, TODAY)).containsExactly(
            "Add Coffee pods to the Nov 8 delivery? You'll run out around Nov 17, and its next delivery is Dec 8.",
        )
    }

    @Test
    fun forTodayTestOrderDayOutsideHeadsUp() {
        val item = item(28).copy(
            subscription = "2 months",
            activeStart = LocalDate.of(2026, 10, 20),
            lastDelivery = LocalDate.of(2026, 10, 8),
        )

        assertThat(Alerts.forToday(listOf(item), SETTINGS.copy(orderDay = 1), TODAY)).isEmpty()
    }

    @Test
    fun forTodayTestDeliveryMatchesUsage() {
        val item = item(28).copy(
            subscription = "1 month",
            activeStart = LocalDate.of(2026, 10, 15),
            lastDelivery = LocalDate.of(2026, 10, 8),
        )

        assertThat(Alerts.forToday(listOf(item), SETTINGS, TODAY)).isEmpty()
    }

    @Test
    fun forTodayTestRanOut() {
        val item = item(28).copy(activeStart = LocalDate.of(2026, 9, 1))

        assertThat(Alerts.forToday(listOf(item), SETTINGS, TODAY))
            .containsExactly("Did you run out of Coffee pods? It was expected around Sep 29.")
    }

    @Test
    fun forTodayTestRanOutWithinSlack() {
        val item = item(28).copy(activeStart = LocalDate.of(2026, 10, 1))

        assertThat(Alerts.forToday(listOf(item), SETTINGS, TODAY)).isEmpty()
    }

    @Test
    fun forTodayTestTurnedOff() {
        val ranOut = item(28).copy(activeStart = LocalDate.of(2026, 9, 1))
        val surplus = item(56).copy(
            subscription = "1 month",
            activeStart = LocalDate.of(2026, 10, 20),
            lastDelivery = LocalDate.of(2026, 10, 8),
        )
        val settings = SETTINGS.copy(deliveryAlerts = false, runOutAlerts = false)

        assertThat(Alerts.forToday(listOf(ranOut, surplus), settings, TODAY)).isEmpty()
    }

    @Test
    fun forTodayTestNoHistory() {
        val item = Item(
            name = "Soap",
            subscription = "1 month",
            activeStart = LocalDate.of(2026, 9, 1),
            lastDelivery = LocalDate.of(2026, 10, 8),
        )

        assertThat(Alerts.forToday(listOf(item), SETTINGS, TODAY)).isEmpty()
    }

    private fun item(days: Long) = Item(
        name = "Coffee pods",
        history = listOf(UsageEntry(start = PAST_USE_END.minusDays(days), end = PAST_USE_END)),
    )
}
