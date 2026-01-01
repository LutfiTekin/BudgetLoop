package t.l.budget.loop.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
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
)

@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val balanceMinor: Long,
    val currency: String,
)

@Entity(tableName = "credit_cards")
data class CreditCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val limitMinor: Long,
    val debtMinor: Long,
    val currency: String,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["date"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amountMinor: Long,
    val amountDefaultMinor: Long,
    val currency: String,
    val cardId: Long,
    val date: Instant,
    val note: String?,
    val category: String?,
)

@Entity(
    tableName = "balance_adjustments",
    indices = [
        Index(value = ["accountType"]),
        Index(value = ["accountId"]),
        Index(value = ["date"]),
    ],
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountType: AccountType,
    val accountId: Long,
    val oldBalanceMinor: Long,
    val newBalanceMinor: Long,
    val date: Instant,
    val note: String?,
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
)
