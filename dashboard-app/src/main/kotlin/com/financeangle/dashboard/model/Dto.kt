package com.financeangle.dashboard.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class TransactionRequest(
    @field:NotNull
    val date: LocalDate,
    @field:NotBlank
    @field:Size(max = 512)
    val description: String,
    val category: String? = null,
    @field:NotNull
    val amount: BigDecimal,
    val accountNumber: String? = null,
    val accountState: BigDecimal? = null
)

/**
 * Register or upsert an account. Supply `accountId` when you want a stable external identifier; if omitted,
 * the service will generate and return its internal id as a string. `accountNumber` is optional and never
 * exposed as a primary key.
 */
data class AccountRequest(
    @field:NotBlank
    @field:Size(max = 128)
    val name: String,
    @field:Size(max = 64)
    val accountNumber: String,
    @field:Size(max = 128)
    val accountId: String? = null,
    @field:Size(max = 128)
    val provider: String? = null,
    @field:Size(max = 3)
    val currency: String = "EUR"
)

data class AccountBalanceSnapshotRequest(
    @field:NotNull
    val date: LocalDate,
    @field:NotNull
    val type: AccountBalanceType,
    val kind: AccountKind,
    @field:NotNull
    val account: String? = null,
    @field:NotNull
    val original: MoneyAmount,
    val fxToEur: FxRate? = null,
    val note: String? = null
)

data class AccountSnapshotRequest(
    @field:NotBlank
    val accountId: String,
    val debit: MoneyAmount? = null,
    val credit: MoneyAmount? = null,
    val loans: MoneyAmount? = null,
    val sharedDebitWithWife: MoneyAmount? = null
)

data class MonthlyAccountPositionRequest(
    val savingsBudget: MoneyAmount? = null
)

data class FxRate(
    val fromCurrency: String,
    val toCurrency: String? = "EUR",
    val rate: BigDecimal,
    val rateDate: LocalDate?,
    val source: String? = null
)

enum class AccountBalanceType(val sign: BigDecimal) {
    DEBIT(BigDecimal.ONE),
    INVESTMENT(BigDecimal.ONE),
    CREDIT(BigDecimal.ONE.negate()),
    LOAN(BigDecimal.ONE.negate())
}

enum class AccountKind(
    val defaultBalanceType: AccountBalanceType
) {
    CHECKING(AccountBalanceType.DEBIT),
    SAVINGS(AccountBalanceType.DEBIT),
    CASH(AccountBalanceType.DEBIT),
    CREDIT_CARD(AccountBalanceType.CREDIT),
    LOAN(AccountBalanceType.LOAN),
    BROKER(AccountBalanceType.INVESTMENT),
    CRYPTO_WALLET(AccountBalanceType.INVESTMENT)
}

// AccountNetPositionSnapshot
data class AccountPositionSnapshotResponse(
    val snapshotDate: LocalDate,
    val accounts: List<AccountSnapshot>,
    val savingsBudget: MoneyAmount,
    val totals: SnapshotTotals
)

data class AccountSnapshot(
    val accountId: String,            // "account1", "account2", "account3" or real UUID
    val debit: MoneyAmount? = null,   // debit state (cash/bank balance)
    val credit: MoneyAmount? = null,  // credit state (credit cards / overdraft / credit line)
    val loans: MoneyAmount? = null,   // loans state (if you want separate from credit)
    val sharedDebitWithWife: MoneyAmount? = null
)

data class SnapshotTotals(
    val totalDebit: MoneyAmount,
    val totalSharedDebit: MoneyAmount,
    val totalCredit: MoneyAmount,
    val totalLoans: MoneyAmount,
    val savingsBudget: MoneyAmount,
    val netPosition: MoneyAmount // totalDebit + totalSharedDebit + savingsBudget - totalCredit - totalLoans
)

data class MoneyAmount(
    val amount: BigDecimal,
    val currency: String = "EUR"
)

data class SummaryPoint(
    val month: YearMonth,
    val category: String?,
    val total: BigDecimal
)

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)
