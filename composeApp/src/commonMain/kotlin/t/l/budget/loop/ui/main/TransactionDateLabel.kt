package t.l.budget.loop.ui.main

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

internal fun formatTransactionDateLabel(
    instant: Instant,
    today: LocalDate,
    timeZone: TimeZone,
): String {
    val date = instant.toLocalDateTime(timeZone).date
    return when (date) {
        today -> "today"
        today.minus(DatePeriod(days = 1)) -> "yesterday"
        else -> formatDayMonthYear(date)
    }
}

private fun formatDayMonthYear(date: LocalDate): String {
    val day = date.dayOfMonth.toString().padStart(2, '0')
    val month = date.monthNumber.toString().padStart(2, '0')
    return "$day.$month.${date.year}"
}
