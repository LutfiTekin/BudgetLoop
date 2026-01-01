package t.l.budget.loop.domain.transactions

import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.CreditCardDao
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.data.db.TransactionEntity
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode

class TransactionService(
    private val transactionDao: TransactionDao,
    private val creditCardDao: CreditCardDao,
) {
    suspend fun addExpense(
        cardId: Long,
        amount: Money,
        amountDefault: Money,
        date: Instant,
        note: String?,
        category: String?,
    ): Long {
        val card = creditCardDao.getById(cardId) ?: error("Credit card not found.")
        val cardCurrency = normalizeCurrencyCode(card.currency)
        require(normalizeCurrencyCode(amount.currency) == cardCurrency) {
            "Transaction currency must match the card currency."
        }

        val updatedCard = card.copy(debtMinor = card.debtMinor + amount.minor)
        creditCardDao.upsert(updatedCard)

        val transaction = TransactionEntity(
            amountMinor = amount.minor,
            amountDefaultMinor = amountDefault.minor,
            currency = cardCurrency,
            cardId = cardId,
            date = date,
            note = note,
            category = category,
        )
        return transactionDao.insert(transaction)
    }

    suspend fun deleteExpense(transactionId: Long) {
        val transaction = transactionDao.getById(transactionId) ?: return
        val card = creditCardDao.getById(transaction.cardId) ?: return
        val updatedCard = card.copy(debtMinor = card.debtMinor - transaction.amountMinor)
        creditCardDao.upsert(updatedCard)
        transactionDao.delete(transaction)
    }
}
