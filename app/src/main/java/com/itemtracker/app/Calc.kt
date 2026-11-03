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

    /** Estimated run-out and order dates for an item in use */
    data class Estimate(val runOut: LocalDate, val orderDate: LocalDate, val daysUntilOrder: Long)

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
        return entries.map { entry -> daysBetween(entry.start, entry.end).toDouble() / max(1, entry.quantity) }
            .average()
    }

    /**
     * Average the days for the current quantity of an item, without rounding
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
        val wholeDays = ceil(days)
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
     * Estimate the run-out and order dates of an item
     *
     * @param item the [Item] to use
     * @param settings the [Settings] to use
     * @param today the current date
     * @return the estimate, or null when the item is not in use or has no finished uses
     */
    fun estimate(item: Item, settings: Settings, today: LocalDate = LocalDate.now()): Estimate? {
        val start = item.activeStart ?: return null
        val average = averageDays(item) ?: return null
        val runOut = start.plusDays(ceil(average).toLong())
        val order = orderDateFor(runOut, settings.orderDay, settings.graceDays)
        return Estimate(runOut, order, ChronoUnit.DAYS.between(today, order))
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
        estimate(item, settings, today)?.let { estimate -> estimate.daysUntilOrder <= settings.dueSoonDays } ?: false

    /**
     * Sort items with due-soon items first (soonest first), then alphabetically
     *
     * @param items the items to sort
     * @param settings the [Settings] to use
     * @param today the current date
     * @return the sorted items
     */
    fun sorted(items: List<Item>, settings: Settings, today: LocalDate = LocalDate.now()): List<Item> {
        val (dueItems, otherItems) = items.partition { item -> isDueSoon(item, settings, today) }
        val dueSorted = dueItems.sortedWith(
            compareBy<Item> { item -> estimate(item, settings, today)?.daysUntilOrder ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER, Item::name)
        )
        return dueSorted + otherItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Item::name))
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
