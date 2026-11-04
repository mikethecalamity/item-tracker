package com.itemtracker.app

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private val TODAY = LocalDate.of(2026, 11, 3)
private val SETTINGS = Settings(orderDay = 1, graceDays = 7, dueSoonDays = 7)

/** Tests for [Calc] */
internal class CalcTest {

    @Nested
    inner class DaysBetween {
        @Test
        fun daysBetweenTest() {
            assertThat(Calc.daysBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22))).isEqualTo(21)
        }

        @Test
        fun daysBetweenTestSameDay() {
            assertThat(Calc.daysBetween(TODAY, TODAY)).isEqualTo(1)
        }
    }

    @Nested
    inner class Averages {
        @Test
        fun averageDaysPerUnitTest() {
            val item = item(entry(14, 1), entry(28, 2), entry(21, 1))

            assertThat(Calc.averageDaysPerUnit(item)).isEqualTo(49.0 / 3)
        }

        @Test
        fun averageDaysPerUnitTestIgnoresDeleted() {
            val item = item(entry(14, 1), entry(100, 1).copy(deleted = true))

            assertThat(Calc.averageDaysPerUnit(item)).isEqualTo(14.0)
        }

        @Test
        fun averageDaysPerUnitTestNoHistory() {
            assertThat(Calc.averageDaysPerUnit(item())).isNull()
        }

        @Test
        fun averageDaysTestScalesWithQuantity() {
            val item = item(entry(30, 3)).copy(quantity = 2)

            assertThat(Calc.averageDays(item)).isEqualTo(20.0)
        }

        @Test
        fun averageDaysTestNoHistory() {
            assertThat(Calc.averageDays(item())).isNull()
        }
    }

    @Nested
    inner class Labels {
        @ParameterizedTest(name = "{0} days -> {1}")
        @CsvSource(
            "1, 1 week", "7, 1 week", "7.2, 2 weeks", "14, 2 weeks", "21, 3 weeks", "22, 1 month", "31, 1 month",
            "32, 5 weeks", "35, 5 weeks", "42, 6 weeks", "49, 7 weeks", "50, 2 months", "61, 2 months",
            "62, 2 months", "92, 3 months", "93, 4 months", "365, 12 months",
        )
        fun roundedLabelTest(days: Double, label: String) {
            assertThat(Calc.roundedLabel(days)).isEqualTo(label)
        }

        @Test
        fun averageLabelTest() {
            assertThat(Calc.averageLabel(item(entry(18, 1)))).isEqualTo("3 weeks")
        }

        @Test
        fun averageLabelTestNoHistory() {
            assertThat(Calc.averageLabel(item())).isEqualTo("—")
        }
    }

    @Nested
    inner class OrderDates {
        @ParameterizedTest(name = "run-out {0}, day {1}, grace {2} -> {3}")
        @CsvSource(
            "2026-11-25, 1, 7, 2026-12-01",
            "2026-11-24, 1, 7, 2026-12-01",
            "2026-11-23, 1, 7, 2026-11-01",
            "2026-12-01, 1, 7, 2026-12-01",
            "2026-11-10, 15, 7, 2026-11-15",
            "2026-11-20, 15, 0, 2026-11-15",
            "2026-02-20, 31, 7, 2026-01-31",
            "2026-02-22, 31, 7, 2026-02-28",
        )
        fun orderDateForTest(runOut: LocalDate, orderDay: Int, graceDays: Int, expected: LocalDate) {
            assertThat(Calc.orderDateFor(runOut, orderDay, graceDays)).isEqualTo(expected)
        }

        @Test
        fun estimateTest() {
            val item = item(entry(28, 1)).copy(activeStart = LocalDate.of(2026, 10, 28))

            assertThat(Calc.estimate(item, SETTINGS, TODAY)).isEqualTo(
                Calc.Estimate(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 1), 28),
            )
        }

        @Test
        fun estimateTestNotInUse() {
            assertThat(Calc.estimate(item(entry(28, 1)), SETTINGS, TODAY)).isNull()
        }

        @Test
        fun estimateTestNoHistory() {
            assertThat(Calc.estimate(item().copy(activeStart = TODAY), SETTINGS, TODAY)).isNull()
        }

        @Test
        fun isDueSoonTest() {
            val item = item(entry(28, 1)).copy(activeStart = LocalDate.of(2026, 10, 10))

            assertThat(Calc.isDueSoon(item, SETTINGS, TODAY)).isTrue()
        }

        @Test
        fun isDueSoonTestOutsideWindow() {
            val item = item(entry(28, 1)).copy(activeStart = LocalDate.of(2026, 10, 28))

            assertThat(Calc.isDueSoon(item, SETTINGS, TODAY)).isFalse()
        }

        @Test
        fun isDueSoonTestNotInUse() {
            assertThat(Calc.isDueSoon(item(entry(28, 1)), SETTINGS, TODAY)).isFalse()
        }
    }

    @Nested
    inner class Subscriptions {
        @Test
        fun frequencyTest() {
            assertThat(Calc.frequency("5 weeks")).isEqualTo(Calc.Frequency("5 weeks", 5, Calc.StepUnit.WEEK))
        }

        @Test
        fun frequencyTestUnknown() {
            assertThat(Calc.frequency("every day")).isNull()
        }

        @Test
        fun frequencyTestMatchesRoundedLabels() {
            assertThat(Calc.FREQUENCIES.map(Calc.Frequency::label))
                .containsAll(listOf(7.0, 14.0, 21.0, 30.0, 35.0, 42.0, 49.0, 60.0, 365.0).map(Calc::roundedLabel))
        }

        @Test
        fun nextDeliveryTestRollsForward() {
            val item = item().copy(subscription = "1 month", lastDelivery = LocalDate.of(2026, 8, 15))

            assertThat(Calc.nextDelivery(item, TODAY)).isEqualTo(LocalDate.of(2026, 11, 15))
        }

        @Test
        fun nextDeliveryTestDeliveredToday() {
            val item = item().copy(subscription = "2 weeks", lastDelivery = TODAY)

            assertThat(Calc.nextDelivery(item, TODAY)).isEqualTo(TODAY.plusWeeks(2))
        }

        @Test
        fun nextDeliveryTestNoLastDelivery() {
            assertThat(Calc.nextDelivery(item().copy(subscription = "1 month"), TODAY)).isNull()
        }

        @Test
        fun estimateTestRoughFromSubscriptionWhileInUse() {
            val item = item().copy(subscription = "1 month", activeStart = LocalDate.of(2026, 10, 28))

            assertThat(Calc.estimate(item, SETTINGS, TODAY)).isEqualTo(
                Calc.Estimate(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 1), 28, rough = true),
            )
        }

        @Test
        fun estimateTestRoughFromNextDelivery() {
            val item = item().copy(subscription = "1 month", lastDelivery = LocalDate.of(2026, 10, 20))

            assertThat(requireNotNull(Calc.estimate(item, SETTINGS, TODAY)))
                .returns(LocalDate.of(2026, 11, 20), Calc.Estimate::runOut)
                .returns(true, Calc.Estimate::rough)
        }

        @Test
        fun estimateTestAverageWinsOverSubscription() {
            val item = item(entry(28, 1)).copy(subscription = "3 months", activeStart = LocalDate.of(2026, 10, 28))

            assertThat(requireNotNull(Calc.estimate(item, SETTINGS, TODAY)))
                .returns(LocalDate.of(2026, 11, 25), Calc.Estimate::runOut)
                .returns(false, Calc.Estimate::rough)
        }

        @Test
        fun warningsTestAllGood() {
            val item = item(entry(28, 1)).copy(
                subscription = "1 month",
                activeStart = LocalDate.of(2026, 10, 28),
                lastDelivery = LocalDate.of(2026, 10, 25),
            )

            assertThat(Calc.warnings(item, SETTINGS, TODAY)).isEmpty()
        }

        @Test
        fun warningsTestFrequencyMismatch() {
            val item = item(entry(28, 1)).copy(subscription = "2 months")

            assertThat(Calc.warnings(item, SETTINGS, TODAY))
                .containsExactly("Your subscription is every 2 months, but you use one every 1 month.")
        }

        @Test
        fun warningsTestDeliveryTooLate() {
            val item = item(entry(28, 1)).copy(
                subscription = "1 month",
                activeStart = LocalDate.of(2026, 10, 10),
                lastDelivery = LocalDate.of(2026, 10, 25),
            )

            assertThat(Calc.warnings(item, SETTINGS, TODAY))
                .containsExactly("The next delivery (Nov 25) comes after you run out (Nov 7).")
        }

        @Test
        fun warningsTestDeliveryTooEarly() {
            val item = item(entry(49, 1)).copy(
                subscription = "7 weeks",
                activeStart = LocalDate.of(2026, 11, 1),
                lastDelivery = LocalDate.of(2026, 9, 20),
            )

            assertThat(Calc.warnings(item, SETTINGS, TODAY))
                .containsExactly("The next delivery (Nov 8) comes more than a week before you need it (Dec 20).")
        }

        @Test
        fun warningsTestNoHistory() {
            val item = item().copy(subscription = "2 months", activeStart = TODAY, lastDelivery = TODAY)

            assertThat(Calc.warnings(item, SETTINGS, TODAY)).isEmpty()
        }
    }

    @Test
    fun sortedTest() {
        val dueLater = item(entry(28, 1)).copy(name = "zinc", activeStart = LocalDate.of(2026, 10, 10))
        val dueSooner = item(entry(14, 1)).copy(name = "Yogurt", activeStart = LocalDate.of(2026, 10, 10))
        val apples = item().copy(name = "apples")
        val bread = item().copy(name = "Bread")

        val sorted = Calc.sorted(listOf(bread, dueLater, apples, dueSooner), SETTINGS, TODAY)

        assertThat(sorted.map(Item::name)).containsExactly("Yogurt", "zinc", "apples", "Bread")
    }

    @Test
    fun shortDateTest() {
        assertThat(Calc.shortDate(LocalDate.of(2026, 10, 12), TODAY)).isEqualTo("Oct 12")
    }

    @Test
    fun shortDateTestOtherYear() {
        assertThat(Calc.shortDate(LocalDate.of(2025, 10, 12), TODAY)).isEqualTo("Oct 12, 2025")
    }

    @Test
    fun longDateTest() {
        assertThat(Calc.longDate(LocalDate.of(2026, 10, 12))).isEqualTo("Oct 12, 2026")
    }

    private fun item(vararg entries: UsageEntry) = Item(name = "Coffee pods", history = entries.toList())

    private fun entry(days: Long, quantity: Int) =
        UsageEntry(start = TODAY.minusDays(days + 100), end = TODAY.minusDays(100), quantity = quantity)
}
