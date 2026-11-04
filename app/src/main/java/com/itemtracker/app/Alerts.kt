package com.itemtracker.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Decide what the daily summary notification should say */
object Alerts {
    /** Days past the estimated run-out before asking whether an item ran out */
    private const val RUN_OUT_SLACK_DAYS = 7L
    /** An item delivery this close to the order day counts as part of that delivery */
    private const val SAME_DELIVERY_DAYS = 3L

    /**
     * Collect the alerts for today; only items with finished uses are considered
     *
     * Delivery alerts look at the next order day when it is within the heads-up days: a surplus when the item ships
     * in that delivery more than one week or month (its subscription unit) before it is needed, and a shortfall when
     * it does not ship then but runs out (beyond the grace days) before its own next delivery. The run-out alert asks
     * about items still in use a week past their estimated run-out.
     *
     * @param items the items to check
     * @param settings the [Settings] to use
     * @param today the current date
     * @return one line per alert, empty when there is nothing to report
     */
    fun forToday(items: List<Item>, settings: Settings, today: LocalDate = LocalDate.now()): List<String> {
        val live = items.filterNot(Item::deleted).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Item::name))
        val deliveryAlerts = live.takeIf { settings.deliveryAlerts }.orEmpty()
            .mapNotNull { item -> deliveryAlert(item, settings, today) }
        val runOutAlerts = live.takeIf { settings.runOutAlerts }.orEmpty()
            .mapNotNull { item -> runOutAlert(item, settings, today) }
        return deliveryAlerts + runOutAlerts
    }

    private fun deliveryAlert(item: Item, settings: Settings, today: LocalDate): String? {
        val frequency = Calc.frequency(item.subscription) ?: return null
        val start = item.activeStart ?: return null
        val average = Calc.averageDays(item) ?: return null
        val next = Calc.nextDelivery(item, today) ?: return null
        val orderDay = Calc.nextOrderDay(today, settings.orderDay)
        if (ChronoUnit.DAYS.between(today, orderDay) > settings.headsUpDays) return null
        val runOut = start.plusDays(ceil(average).toLong())
        val inThisDelivery = Math.abs(ChronoUnit.DAYS.between(orderDay, next)) <= SAME_DELIVERY_DAYS
        val delivery = Calc.shortDate(orderDay, today)
        return when {
            inThisDelivery && frequency.oneUnitBefore(runOut).isAfter(orderDay) ->
                "Skip ${item.name} in the $delivery delivery? You won't need it until about " +
                    "${Calc.shortDate(runOut, today)}."
            !inThisDelivery && next.isAfter(orderDay) && next.isAfter(runOut.plusDays(settings.graceDays.toLong())) ->
                "Add ${item.name} to the $delivery delivery? You'll run out around " +
                    "${Calc.shortDate(runOut, today)}, and its next delivery is ${Calc.shortDate(next, today)}."
            else -> null
        }
    }

    private fun runOutAlert(item: Item, settings: Settings, today: LocalDate): String? {
        val estimate = Calc.estimate(item, settings, today) ?: return null
        if (!today.isAfter(estimate.runOut.plusDays(RUN_OUT_SLACK_DAYS))) return null
        return "Did you run out of ${item.name}? It was expected around ${Calc.shortDate(estimate.runOut, today)}."
    }
}
