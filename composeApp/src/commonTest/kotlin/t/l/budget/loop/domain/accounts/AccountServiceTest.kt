package t.l.budget.loop.domain.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.AccountType
import t.l.budget.loop.data.db.BalanceAdjustmentDao
import t.l.budget.loop.data.db.BalanceAdjustmentEntity
import t.l.budget.loop.data.db.BankAccountDao
import t.l.budget.loop.data.db.BankAccountEntity
import t.l.budget.loop.data.db.CreditCardDao
import t.l.budget.loop.data.db.CreditCardEntity
import t.l.budget.loop.money.Money

class AccountServiceTest {
    @Test
    fun recordsBankBalanceAdjustment() = runTest {
        val bankDao = FakeBankAccountDao()
        val cardDao = FakeCreditCardDao()
        val adjustmentDao = FakeBalanceAdjustmentDao()
        val service = AccountService(bankDao, cardDao, adjustmentDao)

        val accountId = service.addBankAccount("Checking", Money.of(1000L, "USD"))
        service.recordBankBalanceAdjustment(
            accountId = accountId,
            newBalance = Money.of(1500L, "USD"),
            note = "Correction",
            date = Instant.fromEpochMilliseconds(1),
        )

        val updated = bankDao.getById(accountId)
        assertEquals(1500L, updated?.balanceMinor)
        assertEquals(1, adjustmentDao.adjustments.size)
        assertEquals(AccountType.BANK, adjustmentDao.adjustments.first().accountType)
    }

    @Test
    fun recordsCardDebtAdjustment() = runTest {
        val bankDao = FakeBankAccountDao()
        val cardDao = FakeCreditCardDao()
        val adjustmentDao = FakeBalanceAdjustmentDao()
        val service = AccountService(bankDao, cardDao, adjustmentDao)

        val cardId = service.addCreditCard(
            name = "Visa",
            limit = Money.of(5000L, "USD"),
            debt = Money.of(250L, "USD"),
        )
        service.recordCardDebtAdjustment(
            accountId = cardId,
            newDebt = Money.of(400L, "USD"),
            note = "Correction",
            date = Instant.fromEpochMilliseconds(2),
        )

        val updated = cardDao.getById(cardId)
        assertEquals(400L, updated?.debtMinor)
        assertEquals(1, adjustmentDao.adjustments.size)
        assertEquals(AccountType.CARD, adjustmentDao.adjustments.first().accountType)
    }
}

private class FakeBankAccountDao : BankAccountDao {
    private val accounts = mutableMapOf<Long, BankAccountEntity>()
    private var nextId = 1L

    override suspend fun list(): List<BankAccountEntity> = accounts.values.toList()

    override suspend fun getById(id: Long): BankAccountEntity? = accounts[id]

    override suspend fun upsert(account: BankAccountEntity): Long {
        val id = if (account.id == 0L) nextId++ else account.id
        accounts[id] = account.copy(id = id)
        return id
    }

    override suspend fun delete(account: BankAccountEntity) {
        accounts.remove(account.id)
    }
}

private class FakeCreditCardDao : CreditCardDao {
    private val cards = mutableMapOf<Long, CreditCardEntity>()
    private var nextId = 1L

    override suspend fun list(): List<CreditCardEntity> = cards.values.toList()

    override suspend fun getById(id: Long): CreditCardEntity? = cards[id]

    override suspend fun upsert(card: CreditCardEntity): Long {
        val id = if (card.id == 0L) nextId++ else card.id
        cards[id] = card.copy(id = id)
        return id
    }

    override suspend fun delete(card: CreditCardEntity) {
        cards.remove(card.id)
    }
}

private class FakeBalanceAdjustmentDao : BalanceAdjustmentDao {
    val adjustments = mutableListOf<BalanceAdjustmentEntity>()

    override suspend fun listForAccount(
        accountType: AccountType,
        accountId: Long,
    ): List<BalanceAdjustmentEntity> {
        return adjustments.filter { it.accountType == accountType && it.accountId == accountId }
    }

    override suspend fun insert(adjustment: BalanceAdjustmentEntity): Long {
        adjustments.add(adjustment)
        return adjustments.size.toLong()
    }
}
