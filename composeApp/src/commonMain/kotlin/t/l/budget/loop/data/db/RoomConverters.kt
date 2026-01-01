package t.l.budget.loop.data.db

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class RoomConverters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? {
        return value?.toEpochMilliseconds()
    }

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Int? {
        return value?.toEpochDays()?.toInt()
    }

    @TypeConverter
    fun epochDayToLocalDate(value: Int?): LocalDate? {
        return value?.let { LocalDate.fromEpochDays(it) }
    }

    @TypeConverter
    fun accountTypeToString(value: AccountType?): String? {
        return value?.name
    }

    @TypeConverter
    fun stringToAccountType(value: String?): AccountType? {
        return value?.let { AccountType.valueOf(it) }
    }

    @TypeConverter
    fun savingsSourceToString(value: SavingsSource?): String? {
        return value?.name
    }

    @TypeConverter
    fun stringToSavingsSource(value: String?): SavingsSource? {
        return value?.let { SavingsSource.valueOf(it) }
    }

    @TypeConverter
    fun fxRateSourceToString(value: FxRateSource?): String? {
        return value?.name
    }

    @TypeConverter
    fun stringToFxRateSource(value: String?): FxRateSource? {
        return value?.let { FxRateSource.valueOf(it) }
    }
}
