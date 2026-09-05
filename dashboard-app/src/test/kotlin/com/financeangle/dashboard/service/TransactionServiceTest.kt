package com.financeangle.dashboard.service

import com.financeangle.dashboard.model.AccountBalanceSnapshot
import com.financeangle.dashboard.model.AccountBalanceSnapshotRequest
import com.financeangle.dashboard.model.AccountBalanceType
import com.financeangle.dashboard.model.AccountKind
import com.financeangle.dashboard.model.AccountPositionSnapshotAccounts
import com.financeangle.dashboard.model.AccountPositionSnapshots
import com.financeangle.dashboard.model.AccountRequest
import com.financeangle.dashboard.model.Accounts
import com.financeangle.dashboard.model.MoneyAmount
import com.financeangle.dashboard.model.MonthlyAccountPositionRequest
import com.financeangle.dashboard.model.Transactions
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth

class TransactionServiceTest {

    private lateinit var databaseFile: Path
    private lateinit var database: Database
    private lateinit var service: TransactionService

    @BeforeEach
    fun setUp() {
        databaseFile = Files.createTempFile("finance-angle-dashboard-test-", ".db")
        database = Database.connect("jdbc:sqlite:$databaseFile", driver = "org.sqlite.JDBC")
        transaction(database) {
            SchemaUtils.create(
                Accounts,
                Transactions,
                AccountBalanceSnapshot,
                AccountPositionSnapshots,
                AccountPositionSnapshotAccounts
            )
        }
        service = TransactionService(database)
        service.addAccount(AccountRequest("Main", "main-001", "main", "Bank", "EUR"))
        service.addAccount(AccountRequest("Loan", "loan-001", "loan", "Bank", "EUR"))
    }

    @AfterEach
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                AccountPositionSnapshotAccounts,
                AccountPositionSnapshots,
                AccountBalanceSnapshot,
                Transactions,
                Accounts
            )
        }
        Files.deleteIfExists(databaseFile)
    }

    @Test
    fun `should compare assets debts savings and net position month over month`() {
        snapshot("2026-01-31", "Main", AccountBalanceType.DEBIT, "3000")
        snapshot("2026-01-31", "Loan", AccountBalanceType.LOAN, "10000")
        service.addMonthlyAccountPosition(monthlyRequest("2026-01", "500"))

        snapshot("2026-02-28", "Main", AccountBalanceType.DEBIT, "3500")
        snapshot("2026-02-28", "Loan", AccountBalanceType.LOAN, "9000")
        service.addMonthlyAccountPosition(monthlyRequest("2026-02", "700"))

        val comparisons = service.compareMonthlyAccountPositions()

        assertThat(comparisons).hasSize(2)
        assertThat(comparisons[0].netPosition.amount).isEqualByComparingTo("-6500")
        assertThat(comparisons[0].change.netPosition).isNull()
        assertThat(comparisons[1].assets.amount).isEqualByComparingTo("3500")
        assertThat(comparisons[1].debts.amount).isEqualByComparingTo("9000")
        assertThat(comparisons[1].savings.amount).isEqualByComparingTo("700")
        assertThat(comparisons[1].netPosition.amount).isEqualByComparingTo("-4800")
        assertThat(comparisons[1].change.assets?.amount).isEqualByComparingTo("500")
        assertThat(comparisons[1].change.debts?.amount).isEqualByComparingTo("-1000")
        assertThat(comparisons[1].change.savings?.amount).isEqualByComparingTo("200")
        assertThat(comparisons[1].change.netPosition?.amount).isEqualByComparingTo("1700")
    }

    @Test
    fun `should replace a monthly position and its account rows`() {
        snapshot("2026-02-28", "Main", AccountBalanceType.DEBIT, "3500")
        service.addMonthlyAccountPosition(monthlyRequest("2026-02", "500"))
        service.addMonthlyAccountPosition(monthlyRequest("2026-02", "900"))

        assertThat(service.listMonthlyAccountPositions()).hasSize(1)
        assertThat(service.listMonthlyAccountPositions().single().savingsBudget.amount)
            .isEqualByComparingTo("900")
        transaction(database) {
            assertThat(AccountPositionSnapshotAccounts.selectAll().count()).isEqualTo(1)
        }
    }

    private fun snapshot(date: String, account: String, type: AccountBalanceType, amount: String) {
        service.addSnapshot(
            AccountBalanceSnapshotRequest(
                date = LocalDate.parse(date),
                type = type,
                kind = if (type == AccountBalanceType.LOAN) AccountKind.LOAN else AccountKind.CHECKING,
                account = account,
                original = MoneyAmount(BigDecimal(amount), "EUR")
            )
        )
    }

    private fun monthlyRequest(month: String, savings: String) = MonthlyAccountPositionRequest(
        month = YearMonth.parse(month),
        savingsBudget = MoneyAmount(BigDecimal(savings), "EUR")
    )
}
