package t.l.budget.loop.domain.accounts

import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.AccountType
import t.l.budget.loop.data.db.BalanceAdjustmentDao
import t.l.budget.loop.data.db.BalanceAdjustmentEntity
import t.l.budget.loop.data.db.BankAccountDao
import t.l.budget.loop.data.db.BankAccountEntity
import t.l.budget.loop.data.db.CreditCardDao
import t.l.budget.loop.data.db.CreditCardEntity
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode

class AccountService(
    private val bankAccountDao: BankAccountDao,
    private val creditCardDao: CreditCardDao,
    private val balanceAdjustmentDao: BalanceAdjustmentDao,
) {
    suspend fun listBankAccounts(): List<BankAccountEntity> = bankAccountDao.list()

    suspend fun listCreditCards(): List<CreditCardEntity> = creditCardDao.list()

    suspend fun addBankAccount(
        name: String,
        balance: Money,
    ): Long {
        val account = BankAccountEntity(
            name = name,
            balanceMinor = balance.minor,
            currency = normalizeCurrencyCode(balance.currency),
        )
        return bankAccountDao.upsert(account)
    }

    suspend fun addCreditCard(
        name: String,
        limit: Money,
        debt: Money,
    ): Long {
        val currency = normalizeCurrencyCode(limit.currency)
        require(currency == normalizeCurrencyCode(debt.currency)) {
            "Limit and debt must use the same currency."
        }
        val card = CreditCardEntity(
            name = name,
            limitMinor = limit.minor,
            debtMinor = debt.minor,
            currency = currency,
        )
        return creditCardDao.upsert(card)
    }

    suspend fun updateBankAccount(account: BankAccountEntity): Long {
        return bankAccountDao.upsert(account)
    }

    suspend fun updateCreditCard(card: CreditCardEntity): Long {
        return creditCardDao.upsert(card)
    }

    suspend fun deleteBankAccount(account: BankAccountEntity) {
        bankAccountDao.delete(account)
    }

    suspend fun deleteCreditCard(card: CreditCardEntity) {
        creditCardDao.delete(card)
    }

    suspend fun recordBankBalanceAdjustment(
        accountId: Long,
        newBalance: Money,
        note: String?,
        date: Instant,
    ): BalanceAdjustmentEntity {
        val account = bankAccountDao.getById(accountId) ?: error("Bank account not found.")
        requireSameCurrency(account.currency, newBalance.currency)

        val adjustment = BalanceAdjustmentEntity(
            accountType = AccountType.BANK,
            accountId = accountId,
            oldBalanceMinor = account.balanceMinor,
            newBalanceMinor = newBalance.minor,
            date = date,
            note = note,
        )

        bankAccountDao.upsert(account.copy(balanceMinor = newBalance.minor))
        balanceAdjustmentDao.insert(adjustment)
        return adjustment
    }

    suspend fun recordCardDebtAdjustment(
        accountId: Long,
        newDebt: Money,
        note: String?,
        date: Instant,
    ): BalanceAdjustmentEntity {
        val card = creditCardDao.getById(accountId) ?: error("Credit card not found.")
        requireSameCurrency(card.currency, newDebt.currency)

        val adjustment = BalanceAdjustmentEntity(
            accountType = AccountType.CARD,
            accountId = accountId,
            oldBalanceMinor = card.debtMinor,
            newBalanceMinor = newDebt.minor,
            date = date,
            note = note,
        )

        creditCardDao.upsert(card.copy(debtMinor = newDebt.minor))
        balanceAdjustmentDao.insert(adjustment)
        return adjustment
    }

    private fun requireSameCurrency(left: String, right: String) {
        val normalizedLeft = normalizeCurrencyCode(left)
        val normalizedRight = normalizeCurrencyCode(right)
        require(normalizedLeft == normalizedRight) {
            "Currency mismatch: $normalizedLeft vs $normalizedRight"
        }
    }
}
