package t.l.budget.loop.domain.budget

import kotlin.math.max
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate

class BudgetEngine {
    fun calculateDailyRemaining(
        dailyBudgetMinor: Long,
        carryMinor: Long,
        spentTodayMinor: Long,
    ): Long {
        return dailyBudgetMinor + carryMinor - spentTodayMinor
    }

    fun monthEndSaved(carryMinor: Long): Long {
        return max(carryMinor, 0L)
    }

    fun isLastDayOfMonth(date: LocalDate): Boolean {
        val firstOfMonth = LocalDate(date.year, date.monthNumber, 1)
        val firstOfNextMonth = firstOfMonth.plus(DatePeriod(months = 1))
        val lastOfMonth = firstOfNextMonth.minus(DatePeriod(days = 1))
        return date == lastOfMonth
    }
}
