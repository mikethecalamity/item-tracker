package com.itemtracker.app

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Compute averages, rounded labels, order dates and sorting */
object Calc {
    private const val AVERAGE_MONTH_DAYS = 30.44
    /** Slack allowed on month rungs, because calendar months are 28 to 31 days long */
    private const val MONTH_SLACK_DAYS = 1.5
    // The app text is English, so month names are too
    private val SHORT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val LONG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    /** Unit a subscription frequency counts in */
    enum class StepUnit(val singular: String) { WEEK("week"), MONTH("month") }

    /**
     * A subscription frequency, matching the rounded average labels
     *
     * @property label the label, such as "2 months"
     * @property count how many units between deliveries
     * @property unit the [StepUnit] counted
     */
    data class Frequency(val label: String, val count: Int, val unit: StepUnit) {
        val days: Double get() = if (unit == StepUnit.WEEK) count * 7.0 else count * AVERAGE_MONTH_DAYS

        /**
         * Add this frequency to a date a number of times
         *
         * @param date the start date
         * @param times how many deliveries to step forward
         * @return the later date
         */
        fun addTo(date: LocalDate, times: Int = 1): LocalDate {
            val steps = count.toLong() * times
            return if (unit == StepUnit.WEEK) date.plusWeeks(steps) else date.plusMonths(steps)
        }

        /**
         * Step one unit back from a date
         *
         * @param date the date
         * @return one week or one month earlier
         */
        fun oneUnitBefore(date: LocalDate): LocalDate =
            if (unit == StepUnit.WEEK) date.minusWeeks(1) else date.minusMonths(1)
    }

    /** The frequencies a subscription can be set to, in order */
    val FREQUENCIES: List<Frequency> = listOf(
        Frequency("1 week", 1, StepUnit.WEEK),
        Frequency("2 weeks", 2, StepUnit.WEEK),
        Frequency("3 weeks", 3, StepUnit.WEEK),
        Frequency("1 month", 1, StepUnit.MONTH),
        Frequency("5 weeks", 5, StepUnit.WEEK),
        Frequency("6 weeks", 6, StepUnit.WEEK),
        Frequency("7 weeks", 7, StepUnit.WEEK),
    ) + (2..12).map { months -> Frequency("$months months", months, StepUnit.MONTH) }

    /**
     * Estimated dates for an item in use
     *
     * @property unitRunOut when the unit in use runs out
     * @property runOut when all units on hand run out
     * @property orderDate when to order, based on [runOut]
     */
    data class Estimate(
        val unitRunOut: LocalDate,
        val runOut: LocalDate,
        val orderDate: LocalDate,
        val daysUntilOrder: Long,
    )

    /** The default size unit, a plain count; the main screen never shows it */
    const val ITEM_UNIT = "item"

    /** Units offered in the size picker; anything else is a custom unit */
    val SIZE_UNITS: List<String> = listOf("lb", "oz", "fl oz", ITEM_UNIT)

    /**
     * Format an amount and unit, such as "7.5 lb" or "5 lb"
     *
     * @param amount the amount
     * @param unit the unit, or null
     * @return the label
     */
    fun sizeLabel(amount: Double, unit: String?): String {
        val number = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
        val shownUnit = if (unit == ITEM_UNIT && amount != 1.0) "items" else unit
        return listOfNotNull(number, shownUnit?.takeIf(String::isNotBlank)).joinToString(" ")
    }

    /**
     * Round days up to whole days, ignoring floating-point noise such as 20.000000000000004
     *
     * @param days the days
     * @return the whole days
     */
    fun wholeDays(days: Double): Long = ceil(days - 1e-9).toLong()

    /**
     * Get how a unit of a given amount compares to the item's usual size
     *
     * @param item the [Item] to use
     * @param amount the amount of the unit, or null for the usual size
     * @return the ratio, such as 0.67 for a 5 lb tub of a 7.5 lb item; 1 when sizes are not tracked
     */
    fun sizeFactor(item: Item, amount: Double?): Double {
        val usual = item.sizeAmount ?: return 1.0
        if (amount == null || amount <= 0 || usual <= 0) return 1.0
        return amount / usual
    }

    /**
     * Count the days between two dates, where a same-day start and stop counts as one day
     *
     * @param start the first day
     * @param end the last day
     * @return the number of days, at least 1
     */
    fun daysBetween(start: LocalDate, end: LocalDate): Long = max(1L, ChronoUnit.DAYS.between(start, end))

    /**
     * Average the days per unit across the history of an item
     *
     * @param item the [Item] to use
     * @return the average, or null when the item has no finished uses
     */
    fun averageDaysPerUnit(item: Item): Double? {
        val entries = item.liveHistory
        if (entries.isEmpty()) return null
        // A smaller unit lasts proportionally less, so scale each use to the usual size
        return entries.map { entry -> daysBetween(entry.start, entry.end) / sizeFactor(item, entry.size) }.average()
    }

    /**
     * Average the days one order lasts (the per-unit average times the order quantity), without rounding
     *
     * @param item the [Item] to use
     * @return the average, or null when the item has no finished uses
     */
    fun averageDays(item: Item): Double? = averageDaysPerUnit(item)?.let { perUnit -> perUnit * max(1, item.quantity) }

    /**
     * Round a duration up to the next rung of 1, 2, 3 weeks, 1 month, 5, 6, 7 weeks, 2 months, 3 months and so on
     *
     * @param days the duration in days
     * @return the label, such as "3 weeks" or "2 months"
     */
    fun roundedLabel(days: Double): String {
        val wholeDays = wholeDays(days).toDouble()
        return when {
            wholeDays <= 7 -> "1 week"
            wholeDays <= 14 -> "2 weeks"
            wholeDays <= 21 -> "3 weeks"
            wholeDays <= AVERAGE_MONTH_DAYS + 1 -> "1 month"
            wholeDays <= 35 -> "5 weeks"
            wholeDays <= 42 -> "6 weeks"
            wholeDays <= 49 -> "7 weeks"
            else -> "%d months".format(max(2, ceil((wholeDays - MONTH_SLACK_DAYS) / AVERAGE_MONTH_DAYS).toInt()))
        }
    }

    /**
     * Label the rounded average of an item
     *
     * @param item the [Item] to use
     * @return the label, or a dash when the item has no finished uses
     */
    fun averageLabel(item: Item): String = averageDays(item)?.let(::roundedLabel) ?: "—"

    /**
     * Pick the order date for a run-out date
     *
     * Takes the first order day on or after the run-out date. If that means going without the item for more than
     * [graceDays], the order day one month earlier is used instead.
     *
     * @param runOut the estimated run-out date
     * @param orderDay the day of the month orders happen on
     * @param graceDays how many days the user can go without the item
     * @return the order date
     */
    fun orderDateFor(runOut: LocalDate, orderDay: Int, graceDays: Int): LocalDate {
        val runOutMonth = YearMonth.from(runOut)
        val sameMonth = orderDayIn(runOutMonth, orderDay)
        val candidate = if (sameMonth.isBefore(runOut)) orderDayIn(runOutMonth.plusMonths(1), orderDay) else sameMonth
        return if (ChronoUnit.DAYS.between(runOut, candidate) <= graceDays) candidate
        else orderDayIn(YearMonth.from(candidate).minusMonths(1), orderDay)
    }

    /**
     * Find a frequency by its label
     *
     * @param label the label, such as "2 months"
     * @return the frequency, or null when the label is null or unknown
     */
    fun frequency(label: String?): Frequency? = FREQUENCIES.firstOrNull { frequency -> frequency.label == label }

    /**
     * Find the next subscription delivery, assuming every delivery since the last delivery date arrived on schedule
     *
     * @param item the [Item] to use
     * @param today the current date
     * @return the first scheduled delivery after today, or null without a subscription and last delivery date
     */
    fun nextDelivery(item: Item, today: LocalDate = LocalDate.now()): LocalDate? {
        val frequency = frequency(item.subscription) ?: return null
        val lastDelivery = item.lastDelivery ?: return null
        var times = 1
        while (!frequency.addTo(lastDelivery, times).isAfter(today)) times++
        return frequency.addTo(lastDelivery, times)
    }

    /**
     * Find the next order day on or after a date
     *
     * @param today the current date
     * @param orderDay the day of the month orders happen on
     * @return the next order day
     */
    fun nextOrderDay(today: LocalDate, orderDay: Int): LocalDate {
        val thisMonth = orderDayIn(YearMonth.from(today), orderDay)
        return if (thisMonth.isBefore(today)) orderDayIn(YearMonth.from(today).plusMonths(1), orderDay) else thisMonth
    }

    /**
     * Estimate the run-out and order dates of an item from its real average; the subscription is never used
     *
     * @param item the [Item] to use
     * @param settings the [Settings] to use
     * @param today the current date
     * @return the estimate, or null when the item is not in use or has no finished uses
     */
    fun estimate(item: Item, settings: Settings, today: LocalDate = LocalDate.now()): Estimate? {
        val start = item.activeStart ?: return null
        val perUnit = averageDaysPerUnit(item) ?: return null
        val currentUnitDays = perUnit * sizeFactor(item, item.activeSize)
        val unitRunOut = start.plusDays(wholeDays(currentUnitDays))
        // Units left includes the one in use; the others are assumed to be the usual size
        val runOut = start.plusDays(wholeDays(currentUnitDays + perUnit * (max(1, item.unitsLeft) - 1)))
        val order = orderDateFor(runOut, settings.orderDay, settings.graceDays)
        return Estimate(unitRunOut, runOut, order, ChronoUnit.DAYS.between(today, order))
    }

    /**
     * Explain where the subscription disagrees with the real average; only items with finished uses are checked
     *
     * Flags a frequency that differs from the rounded average, a next delivery that arrives after the item runs out
     * (beyond the grace days), and one that arrives more than one week or month before it is needed.
     *
     * @param item the [Item] to use
     * @param settings the [Settings] to use
     * @param today the current date
     * @return the warnings, empty when everything lines up
     */
    fun warnings(item: Item, settings: Settings, today: LocalDate = LocalDate.now()): List<String> {
        val frequency = frequency(item.subscription) ?: return emptyList()
        val average = averageDays(item) ?: return emptyList()
        val usage = roundedLabel(average)
        val frequencyWarning = if (usage == frequency.label) null
        else "Your subscription is every ${frequency.label}, but you use one every $usage."
        val runOut = estimate(item, settings, today)?.runOut
        val next = nextDelivery(item, today)
        val timingWarning = if (runOut == null || next == null) null
        else {
            when {
                next.isAfter(runOut.plusDays(settings.graceDays.toLong())) ->
                    "The next delivery (${shortDate(next, today)}) comes after you run out " +
                        "(${shortDate(runOut, today)})."
                frequency.oneUnitBefore(runOut).isAfter(next) ->
                    "The next delivery (${shortDate(next, today)}) comes more than a ${frequency.unit.singular} " +
                        "before you need it (${shortDate(runOut, today)})."
                else -> null
            }
        }
        return listOfNotNull(frequencyWarning, timingWarning)
    }

    /**
     * Check whether the order date of an item is within the due-soon window
     *
     * @param item the [Item] to use
     * @param settings the [Settings] to use
     * @param today the current date
     * @return true when the item should show the due badge
     */
    fun isDueSoon(item: Item, settings: Settings, today: LocalDate = LocalDate.now()): Boolean =
        estimate(item, settings, today)?.let { estimate -> estimate.daysUntilOrder <= settings.headsUpDays } ?: false

    /**
     * Sort items by estimated run-out date, soonest first, then by name; items without an estimate come last by name
     *
     * @param items the items to sort
     * @param settings the [Settings] to use
     * @param today the current date
     * @return the sorted items
     */
    fun sorted(items: List<Item>, settings: Settings, today: LocalDate = LocalDate.now()): List<Item> {
        val runOuts = items.associate { item -> item.id to estimate(item, settings, today)?.runOut }
        return items.sortedWith(
            compareBy<Item> { item -> runOuts[item.id] ?: LocalDate.MAX }
                .thenBy(String.CASE_INSENSITIVE_ORDER, Item::name)
        )
    }

    /**
     * Format a date without the year when it is in the current year
     *
     * @param date the date to format
     * @param today the current date
     * @return the formatted date, such as "Oct 12"
     */
    fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        date.format(if (date.year == today.year) SHORT_DATE_FORMAT else LONG_DATE_FORMAT)

    /**
     * Format a date with the year
     *
     * @param date the date to format
     * @return the formatted date, such as "Oct 12, 2026"
     */
    fun longDate(date: LocalDate): String = date.format(LONG_DATE_FORMAT)

    private fun orderDayIn(month: YearMonth, day: Int): LocalDate = month.atDay(min(max(1, day), month.lengthOfMonth()))
}
