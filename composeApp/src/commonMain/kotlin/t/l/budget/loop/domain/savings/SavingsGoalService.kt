package t.l.budget.loop.domain.savings

import kotlinx.datetime.LocalDate
import t.l.budget.loop.data.db.GoalKind
import t.l.budget.loop.data.db.SavingsGoalDao
import t.l.budget.loop.data.db.SavingsGoalEntity

/**
 * Manages ordered user-defined Loop goals (savings targets and debt payoffs),
 * cascading infused carry-over surplus along the active priority queue.
 */
class SavingsGoalService(
    private val savingsGoalDao: SavingsGoalDao,
) {
    suspend fun listActiveGoals(): List<SavingsGoalEntity> = savingsGoalDao.listActive()

    suspend fun listAllGoals(): List<SavingsGoalEntity> = savingsGoalDao.listAll()

    suspend fun activePriorityGoal(): SavingsGoalEntity? {
        val active = savingsGoalDao.listActive()
        return active.firstOrNull { it.allocatedMinor < it.targetMinor } ?: active.firstOrNull()
    }

    suspend fun createGoal(
        name: String,
        targetMinor: Long,
        initialAllocatedMinor: Long = 0L,
        kind: GoalKind = GoalKind.SAVINGS,
        targetDate: LocalDate? = null,
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Goal name cannot be blank." }
        require(targetMinor > 0L) { "Target amount must be positive." }
        require(initialAllocatedMinor >= 0L) { "Initial progress cannot be negative." }

        val sortOrder = savingsGoalDao.nextSortOrder()
        return savingsGoalDao.insert(
            SavingsGoalEntity(
                name = trimmed,
                targetMinor = targetMinor,
                allocatedMinor = initialAllocatedMinor,
                kind = kind,
                targetDate = targetDate,
                sortOrder = sortOrder,
                archived = false,
            ),
        )
    }

    suspend fun updateGoal(goal: SavingsGoalEntity) {
        val trimmed = goal.name.trim()
        require(trimmed.isNotEmpty()) { "Goal name cannot be blank." }
        require(goal.targetMinor > 0L) { "Target amount must be positive." }
        require(goal.allocatedMinor >= 0L) { "Progress cannot be negative." }
        savingsGoalDao.update(goal.copy(name = trimmed))
    }

    suspend fun moveGoal(goalId: Long, delta: Int) {
        if (delta == 0) return
        val active = savingsGoalDao.listActive().toMutableList()
        val index = active.indexOfFirst { it.id == goalId }
        if (index < 0) return
        val targetIndex = (index + delta).coerceIn(0, active.lastIndex)
        if (targetIndex == index) return

        val moved = active.removeAt(index)
        active.add(targetIndex, moved)
        active.forEachIndexed { idx, item ->
            if (item.sortOrder != idx) {
                savingsGoalDao.update(item.copy(sortOrder = idx))
            }
        }
    }

    suspend fun deleteGoal(goal: SavingsGoalEntity) {
        savingsGoalDao.delete(goal)
    }

    /**
     * Distributes [deltaMinor] across incomplete active goals in priority order,
     * starting with [preferredGoalId] when specified. Overflow beyond one target's
     * remaining headroom cascades to the next active goal.
     *
     * @return the primary goal ID credited, or null if no active goals exist.
     */
    suspend fun allocateSurplus(
        deltaMinor: Long,
        preferredGoalId: Long? = null,
    ): Long? {
        if (deltaMinor <= 0L) return null
        val active = savingsGoalDao.listActive()
        if (active.isEmpty()) return null

        val incomplete = active.filter { it.allocatedMinor < it.targetMinor }
        val ordered = if (incomplete.isNotEmpty()) incomplete else listOf(active.first())
        val queue = if (preferredGoalId != null) {
            val preferred = active.firstOrNull { it.id == preferredGoalId }
            if (preferred != null) {
                listOf(preferred) + ordered.filter { it.id != preferredGoalId }
            } else {
                ordered
            }
        } else {
            ordered
        }

        var remaining = deltaMinor
        var primaryGoalId: Long? = null
        queue.forEachIndexed { idx, goal ->
            if (remaining <= 0L) return@forEachIndexed
            val isLast = idx == queue.lastIndex
            val headroom = (goal.targetMinor - goal.allocatedMinor).coerceAtLeast(0L)
            val applyMinor = when {
                isLast -> remaining
                headroom <= 0L -> 0L
                else -> minOf(remaining, headroom)
            }
            if (applyMinor > 0L) {
                if (primaryGoalId == null) primaryGoalId = goal.id
                savingsGoalDao.update(goal.copy(allocatedMinor = goal.allocatedMinor + applyMinor))
                remaining -= applyMinor
            }
        }

        return primaryGoalId
    }

    suspend fun reverseContribution(goalId: Long?, deltaMinor: Long) {
        if (goalId == null || deltaMinor <= 0L) return
        val goal = savingsGoalDao.getById(goalId) ?: return
        val updated = (goal.allocatedMinor - deltaMinor).coerceAtLeast(0L)
        if (updated != goal.allocatedMinor) {
            savingsGoalDao.update(goal.copy(allocatedMinor = updated))
        }
    }
}
