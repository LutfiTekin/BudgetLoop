package t.l.budget.loop.domain.savings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import t.l.budget.loop.data.db.GoalKind
import t.l.budget.loop.data.db.SavingsGoalDao
import t.l.budget.loop.data.db.SavingsGoalEntity

class SavingsGoalServiceTest {
    @Test
    fun createsReordersAndAllocatesGoalsInPriorityOrder() = runTest {
        val dao = InMemorySavingsGoalDao()
        val service = SavingsGoalService(dao)

        val firstId = service.createGoal(
            name = "Revolving Debt",
            targetMinor = 10_000L,
            initialAllocatedMinor = 2_000L,
            kind = GoalKind.DEBT_PAYDOWN,
            targetDate = LocalDate(2027, 5, 1),
        )
        val secondId = service.createGoal(
            name = "Reserve Fund",
            targetMinor = 50_000L,
            initialAllocatedMinor = 0L,
            kind = GoalKind.SAVINGS,
        )

        assertEquals(firstId, service.activePriorityGoal()?.id)

        service.moveGoal(secondId, -1)
        val reordered = service.listActiveGoals()
        assertEquals(listOf("Reserve Fund", "Revolving Debt"), reordered.map { it.name })

        service.moveGoal(secondId, +1)
        val creditedId = service.allocateSurplus(12_000L)
        assertEquals(firstId, creditedId)
        assertEquals(10_000L, dao.getById(firstId)?.allocatedMinor)
        assertEquals(4_000L, dao.getById(secondId)?.allocatedMinor)

        service.reverseContribution(secondId, 1_500L)
        assertEquals(2_500L, dao.getById(secondId)?.allocatedMinor)
    }
}

private class InMemorySavingsGoalDao : SavingsGoalDao {
    private val items = mutableListOf<SavingsGoalEntity>()

    override suspend fun listActive(): List<SavingsGoalEntity> =
        items.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override suspend fun listAll(): List<SavingsGoalEntity> =
        items.sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.id }))

    override suspend fun getById(id: Long): SavingsGoalEntity? = items.firstOrNull { it.id == id }

    override suspend fun nextSortOrder(): Int = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1

    override suspend fun insert(goal: SavingsGoalEntity): Long {
        val id = (items.maxOfOrNull { it.id } ?: 0L) + 1L
        items.add(goal.copy(id = id))
        return id
    }

    override suspend fun update(goal: SavingsGoalEntity) {
        val idx = items.indexOfFirst { it.id == goal.id }
        if (idx >= 0) items[idx] = goal
    }

    override suspend fun delete(goal: SavingsGoalEntity) {
        items.removeAll { it.id == goal.id }
    }
}
