package t.l.budget.loop.domain.transactions

import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.data.db.TransactionEntity
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode

class TransactionService(
    private val transactionDao: TransactionDao,
) {
    suspend fun addExpense(
        amount: Money,
        amountDefault: Money,
        date: Instant,
        note: String?,
        category: String?,
    ): Long {
        val transaction = TransactionEntity(
            amountMinor = amount.minor,
            amountDefaultMinor = amountDefault.minor,
            currency = normalizeCurrencyCode(amount.currency),
            date = date,
            note = note,
            category = category,
        )
        return transactionDao.insert(transaction)
    }

    suspend fun deleteExpense(transactionId: Long) {
        val transaction = transactionDao.getById(transactionId) ?: return
        transactionDao.delete(transaction)
    }
}