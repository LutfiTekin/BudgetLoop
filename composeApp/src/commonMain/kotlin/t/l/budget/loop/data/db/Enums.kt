package t.l.budget.loop.data.db

enum class SavingsSource {
    MONTH_END,
    CARRY_OVER,
    MANUAL,
}

enum class FxRateSource {
    MANUAL,
    DEFAULT,
}

enum class GoalKind {
    SAVINGS,
    DEBT_PAYDOWN,
}
