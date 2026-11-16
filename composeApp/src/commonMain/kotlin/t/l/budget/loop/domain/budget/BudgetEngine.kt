package t.l.budget.loop.domain.budget

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class BudgetCycle(
    val start: LocalDate,
    val endInclusive: LocalDate,
)

class BudgetEngine {
    fun calculateDailyRemaining(
        dailyBudgetMinor: Long,
        carryMinor: Long,
        spentTodayMinor: Long,
    ): Long {
        return dailyBudgetMinor + carryMinor - spentTodayMinor
    }

    fun monthEndSaved(carryMinor: Long): Long {
        return carryMinor
    }

    fun isLastDayOfMonth(date: LocalDate, resetDay: Int = 1): Boolean {
        return date == currentCycle(date, resetDay).endInclusive
    }

    fun lastDayOfMonth(date: LocalDate): LocalDate {
        val firstOfMonth = LocalDate(date.year, date.month, 1)
        val firstOfNextMonth = firstOfMonth.plus(DatePeriod(months = 1))
        return firstOfNextMonth.minus(DatePeriod(days = 1))
    }

    fun daysInMonth(date: LocalDate): Int {
        val firstOfMonth = LocalDate(date.year, date.month, 1)
        val firstOfNextMonth = firstOfMonth.plus(DatePeriod(months = 1))
        return firstOfMonth.daysUntil(firstOfNextMonth)
    }

    /**
     * Resolves the active budget cycle `[start, endInclusive]` containing [date] when
     * cycles reset on [resetDay] (`1..31`, clamped per calendar month for short months).
     */
    fun currentCycle(date: LocalDate, resetDay: Int = 1): BudgetCycle {
        val thisMonthStart = cycleStartForCalendarMonth(date.year, date.month, resetDay)
        val cycleStart = if (date >= thisMonthStart) {
            thisMonthStart
        } else {
            val prevMonthAnchor = LocalDate(date.year, date.month, 1).minus(DatePeriod(months = 1))
            cycleStartForCalendarMonth(prevMonthAnchor.year, prevMonthAnchor.month, resetDay)
        }
        val nextMonthAnchor = LocalDate(cycleStart.year, cycleStart.month, 1).plus(DatePeriod(months = 1))
        val nextCycleStart = cycleStartForCalendarMonth(nextMonthAnchor.year, nextMonthAnchor.month, resetDay)
        val cycleEnd = nextCycleStart.minus(DatePeriod(days = 1))
        return BudgetCycle(start = cycleStart, endInclusive = cycleEnd)
    }

    fun daysInCycle(date: LocalDate, resetDay: Int = 1): Int {
        val cycle = currentCycle(date, resetDay)
        return cycle.start.daysUntil(cycle.endInclusive.plus(DatePeriod(days = 1)))
    }

    fun effectiveMonthlyCapMinor(
        dailyBudgetMinor: Long,
        customCapMinor: Long?,
        date: LocalDate,
        resetDay: Int = 1,
    ): Long {
        return customCapMinor?.takeIf { it > 0L } ?: (dailyBudgetMinor * daysInCycle(date, resetDay))
    }

    private fun cycleStartForCalendarMonth(year: Int, month: Month, resetDay: Int): LocalDate {
        val first = LocalDate(year, month, 1)
        val clampedDay = resetDay.coerceIn(1, daysInMonth(first))
        return LocalDate(year, month, clampedDay)
    }
}
