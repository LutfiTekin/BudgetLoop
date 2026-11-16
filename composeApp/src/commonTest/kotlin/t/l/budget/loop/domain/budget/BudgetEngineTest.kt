package t.l.budget.loop.domain.budget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class BudgetEngineTest {
    private val engine = BudgetEngine()

    @Test
    fun calculateDailyRemainingUsesCarryAndSpend() {
        val remaining = engine.calculateDailyRemaining(
            dailyBudgetMinor = 1000L,
            carryMinor = -100L,
            spentTodayMinor = 200L,
        )
        assertEquals(700L, remaining)
    }

    @Test
    fun monthEndTracksSurplusAndDeficit() {
        assertEquals(250L, engine.monthEndSaved(250L))
        assertEquals(-50L, engine.monthEndSaved(-50L))
    }

    @Test
    fun computesDaysInMonthAndMonthlyCap() {
        assertEquals(29, engine.daysInMonth(LocalDate(2024, 2, 10)))
        assertEquals(28, engine.daysInMonth(LocalDate(2025, 2, 10)))
        assertEquals(30, engine.daysInMonth(LocalDate(2026, 11, 16)))
        assertEquals(54_000L, engine.effectiveMonthlyCapMinor(1800L, null, LocalDate(2026, 11, 16)))
        assertEquals(54_800L, engine.effectiveMonthlyCapMinor(1800L, 54_800L, LocalDate(2026, 11, 16)))
    }

    @Test
    fun detectsLastDayOfMonth() {
        assertTrue(engine.isLastDayOfMonth(LocalDate(2025, 1, 31)))
        assertTrue(engine.isLastDayOfMonth(LocalDate(2024, 2, 29)))
        assertFalse(engine.isLastDayOfMonth(LocalDate(2025, 2, 27)))
    }
}
