package t.l.budget.loop.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Long = 1L,
    val defaultCurrency: String,
    val timezone: String,
    val dailyBudgetMinor: Long,
    val reportTimeMinutes: Int,
    val monthlyCapMinor: Long? = null,
    val loopStartDate: LocalDate? = null,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amountMinor: Long,
    val amountDefaultMinor: Long,
    val currency: String,
    val date: Instant,
    val note: String?,
    val category: String?,
)

@Entity(
    tableName = "fx_rates",
    indices = [
        Index(
            value = ["fromCurrency", "toCurrency", "source"],
            unique = true,
        ),
    ],
)
data class FxRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val asOf: Instant,
    val source: FxRateSource,
)

@Entity(tableName = "daily_budget_state")
data class DailyBudgetStateEntity(
    @PrimaryKey val date: LocalDate,
    val dailyBudgetMinor: Long,
    val carryMinor: Long,
    val remainingMinor: Long,
    val monthlySavedMinor: Long,
)

@Entity(
    tableName = "savings_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["source"]),
    ],
)
data class SavingsEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amountMinor: Long,
    val date: LocalDate,
    val source: SavingsSource,
    val note: String?,
    val goalId: Long? = null,
)

@Entity(
    tableName = "savings_goals",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["archived"]),
    ],
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val targetMinor: Long,
    val allocatedMinor: Long = 0L,
    val kind: GoalKind = GoalKind.SAVINGS,
    val targetDate: LocalDate? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)