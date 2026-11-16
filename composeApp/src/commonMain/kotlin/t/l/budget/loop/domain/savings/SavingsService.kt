package t.l.budget.loop.domain.savings

import kotlinx.datetime.LocalDate
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.data.db.SavingsGoalDao
import t.l.budget.loop.data.db.SavingsSource
import t.l.budget.loop.money.Money

class SavingsService(
    private val savingsEntryDao: SavingsEntryDao,
    private val savingsGoalDao: SavingsGoalDao? = null,
) {
    private val goalService: SavingsGoalService? = savingsGoalDao?.let(::SavingsGoalService)

    suspend fun listEntries(): List<SavingsEntryEntity> = savingsEntryDao.list()

    suspend fun addManualEntry(
        amount: Money,
        date: LocalDate,
        note: String?,
        goalId: Long? = null,
    ): Long {
        val creditedGoalId = if (amount.minor > 0L) {
            goalService?.allocateSurplus(amount.minor, preferredGoalId = goalId)
        } else {
            null
        }
        val entry = SavingsEntryEntity(
            amountMinor = amount.minor,
            date = date,
            source = SavingsSource.MANUAL,
            note = note,
            goalId = creditedGoalId,
        )
        return savingsEntryDao.insert(entry)
    }

    suspend fun addMonthEndEntry(
        amount: Money,
        date: LocalDate,
    ): Long {
        val entry = SavingsEntryEntity(
            amountMinor = amount.minor,
            date = date,
            source = SavingsSource.MONTH_END,
            note = null,
        )
        return savingsEntryDao.insert(entry)
    }

    suspend fun updateEntry(entry: SavingsEntryEntity) {
        savingsEntryDao.update(entry)
    }

    suspend fun deleteEntry(entry: SavingsEntryEntity) {
        savingsEntryDao.delete(entry)
        goalService?.reverseContribution(entry.goalId, entry.amountMinor)
    }
}
