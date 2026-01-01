package t.l.budget.loop.domain.savings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.money.Money

class SavingsServiceTest {
    @Test
    fun addsUpdatesAndDeletesManualEntry() = runTest {
        val dao = FakeSavingsEntryDao()
        val service = SavingsService(dao)

        val entryId = service.addManualEntry(
            amount = Money.of(500L, "USD"),
            date = LocalDate(2025, 1, 10),
            note = "Manual save",
        )
        assertEquals(1, dao.entries.size)

        val updated = dao.entries.first().copy(
            id = entryId,
            amountMinor = 750L,
            note = "Updated",
        )
        service.updateEntry(updated)
        assertEquals(750L, dao.entries.first().amountMinor)

        service.deleteEntry(updated)
        assertEquals(0, dao.entries.size)
    }
}

private class FakeSavingsEntryDao : SavingsEntryDao {
    val entries = mutableListOf<SavingsEntryEntity>()
    private var nextId = 1L

    override suspend fun list(): List<SavingsEntryEntity> = entries.toList()

    override suspend fun insert(entry: SavingsEntryEntity): Long {
        val stored = entry.copy(id = nextId++)
        entries.add(stored)
        return stored.id
    }

    override suspend fun update(entry: SavingsEntryEntity) {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            entries[index] = entry
        }
    }

    override suspend fun delete(entry: SavingsEntryEntity) {
        entries.removeIf { it.id == entry.id }
    }
}
