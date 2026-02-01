package com.financeangle.dashboard.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date
import java.math.BigDecimal
import java.time.YearMonth

object Accounts : IntIdTable(name = "accounts") {
    val name = varchar("name", 128)
    val accountNumber = varchar("account_number", 64)
    val provider = varchar("provider", 128).nullable()
    val currency = varchar("currency", 3).default("EUR")
    val externalId = varchar("external_id", 128).nullable()
}

object Transactions : IntIdTable(name = "transactions") {
    val accountId = reference("account_id", Accounts).nullable()
    val date = date("date")
    val description = varchar("description", 512)
    val category = varchar("category", 128).nullable()
    val amount = decimal("amount", 12, 2)
    val origin = varchar("origin", 32)
}

object AccountBalanceSnapshot : IntIdTable(name = "snapshots") {
    val accountId = reference("account_id", Accounts).nullable()
    val type = enumerationByName("type", 32, AccountBalanceType::class).default(AccountBalanceType.DEBIT)
    val kind = enumerationByName("kind", 32, AccountKind::class).default(AccountKind.CHECKING)
    val date = date("date")
    val balance = decimal("balance", 14, 2)
    val balanceCurrency = varchar("balance_currency", 3).default("EUR")
    val originalAmount = decimal("original_amount", 14, 2).nullable()
    val originalCurrency = varchar("original_currency", 3).nullable()
    val fxRate = decimal("fx_rate", 18, 8).nullable()
    val fxFromCurrency = varchar("fx_from_currency", 3).nullable()
    val fxToCurrency = varchar("fx_to_currency", 3).nullable()
    val fxRateDate = date("fx_rate_date").nullable()
    val fxSource = varchar("fx_source", 128).nullable()
    val note = varchar("note", 256).nullable()
}

object AccountPositionSnapshots : IntIdTable(name = "account_position_snapshots") {
    val snapshotMonth = date("snapshot_month")
    val savingsBudget = decimal("savings_budget", 14, 2).default(BigDecimal.ZERO)
    val savingsCurrency = varchar("savings_currency", 3).default("EUR")
}

object AccountPositionSnapshotAccounts : IntIdTable(name = "account_position_snapshot_accounts") {
    val snapshotId = reference("snapshot_id", AccountPositionSnapshots)
    val accountId = reference("account_id", Accounts).nullable()
    val accountName = varchar("account_name", 128)
    val debitAmount = decimal("debit_amount", 14, 2).nullable()
    val debitCurrency = varchar("debit_currency", 3).nullable()
    val creditAmount = decimal("credit_amount", 14, 2).nullable()
    val creditCurrency = varchar("credit_currency", 3).nullable()
    val loansAmount = decimal("loans_amount", 14, 2).nullable()
    val loansCurrency = varchar("loans_currency", 3).nullable()
    val sharedDebitAmount = decimal("shared_debit_amount", 14, 2).nullable()
    val sharedDebitCurrency = varchar("shared_debit_currency", 3).nullable()
}

data class TransactionRecord(
    val id: Int,
    val date: java.time.LocalDate,
    val description: String,
    val category: String?,
    val amount: BigDecimal,
    val account: String?,
    val origin: String
)

data class SnapshotRecord(
    val id: Int,
    val date: java.time.LocalDate,
    val balance: MoneyAmount,
    val original: MoneyAmount,
    val fxToEur: FxRate?,
    val type: AccountBalanceType,
    val kind: AccountKind,
    val account: String?,
    val note: String?
)

/**
 * Represents an account persisted in the database. The `accountId` is the stable identifier
 * returned to API consumers: if an external id was provided we echo that, otherwise we fall back
 * to the internal numeric primary key as a string.
 */
data class AccountRecord(
    val accountId: String,
    val name: String,
    val accountNumber: String?,
    val provider: String?,
    val currency: String
)

data class AccountPositionSnapshotRecord(
    val id: Int,
    val month: YearMonth,
    val accounts: List<AccountSnapshot>,
    val savingsBudget: MoneyAmount,
    val totals: SnapshotTotals
)
