package t.l.budget.loop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform