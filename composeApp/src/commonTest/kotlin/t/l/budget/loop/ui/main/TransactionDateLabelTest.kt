package t.l.budget.loop.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

class TransactionDateLabelTest {
    @Test
    fun labelsToday() {
        val timeZone = TimeZone.UTC
        val today = LocalDate(2026, 1, 2)
        val instant = today.atStartOfDayIn(timeZone)

        val label = formatTransactionDateLabel(instant, today, timeZone)

        assertEquals("today", label)
    }

    @Test
    fun labelsYesterday() {
        val timeZone = TimeZone.UTC
        val today = LocalDate(2026, 1, 2)
        val yesterday = today.minus(DatePeriod(days = 1))
        val instant = yesterday.atStartOfDayIn(timeZone)

        val label = formatTransactionDateLabel(instant, today, timeZone)

        assertEquals("yesterday", label)
    }

    @Test
    fun formatsOlderDates() {
        val timeZone = TimeZone.UTC
        val today = LocalDate(2026, 1, 5)
        val olderDate = LocalDate(2026, 1, 2)
        val instant = olderDate.atStartOfDayIn(timeZone)

        val label = formatTransactionDateLabel(instant, today, timeZone)

        assertEquals("02.01.2026", label)
    }
}
