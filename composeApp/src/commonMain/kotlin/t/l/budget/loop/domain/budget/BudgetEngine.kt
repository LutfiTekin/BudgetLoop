package t.l.budget.loop.domain.budget

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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

    fun isLastDayOfMonth(date: LocalDate): Boolean {
        return date == lastDayOfMonth(date)
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

    fun effectiveMonthlyCapMinor(
        dailyBudgetMinor: Long,
        customCapMinor: Long?,
        date: LocalDate,
    ): Long {
        return customCapMinor?.takeIf { it > 0L } ?: (dailyBudgetMinor * daysInMonth(date))
    }
}
