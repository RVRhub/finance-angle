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
import com.financeangle.dashboard.model.TransactionRequest
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
import java.util.Comparator

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

    @Test
    fun `should prepare aligned balance series and carry forward unchanged accounts`() {
        snapshot("2026-01-31", "Main", AccountBalanceType.DEBIT, "3000")
        snapshot("2026-01-31", "Loan", AccountBalanceType.LOAN, "10000")
        snapshot("2026-02-28", "Main", AccountBalanceType.DEBIT, "3500")

        val balances = DashboardDataService(service).buildBalanceData(service.listSnapshots())
        val numericBigDecimalComparator = Comparator<BigDecimal> { left, right -> left.compareTo(right) }

        assertThat(balances.dates).containsExactly(
            LocalDate.parse("2026-01-31"),
            LocalDate.parse("2026-02-28")
        )
        assertThat(balances.netPosition)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(
                BigDecimal("-7000"),
                BigDecimal("-6500")
            )
        assertThat(balances.series.single { it.label == "Loan (loan)" }.values)
            .usingElementComparator(Comparator.nullsFirst(Comparator.naturalOrder()))
            .containsExactly(
                BigDecimal("-10000"),
                BigDecimal("-10000")
            )
    }

    @Test
    fun `should summarize spending as positive expense totals by category`() {
        transaction("2026-01-05", "Groceries", "-12.34")
        transaction("2026-01-12", "Groceries", "-7.66")
        transaction("2026-01-20", "Groceries", "100.00")
        transaction("2026-01-25", null, "-5.25")
        transaction("2026-02-01", "Groceries", "-3.10")

        val summary = service.monthlyCategorySummary()

        assertThat(summary).hasSize(3)
        assertThat(summary[0].month).isEqualTo(YearMonth.parse("2026-01"))
        assertThat(summary[0].category).isEqualTo("Groceries")
        assertThat(summary[0].total).isEqualByComparingTo("20.00")
        assertThat(summary[1].month).isEqualTo(YearMonth.parse("2026-01"))
        assertThat(summary[1].category).isEqualTo("Uncategorised")
        assertThat(summary[1].total).isEqualByComparingTo("5.25")
        assertThat(summary[2].month).isEqualTo(YearMonth.parse("2026-02"))
        assertThat(summary[2].category).isEqualTo("Groceries")
        assertThat(summary[2].total).isEqualByComparingTo("3.10")
    }

    @Test
    fun `should prefer categorized copy when an import was duplicated without categories`() {
        repeat(2) { transaction("2026-01-05", null, "-12.34") }
        repeat(2) { transaction("2026-01-05", "Groceries", "-12.34") }

        val summary = service.monthlyCategorySummary()

        assertThat(summary).hasSize(1)
        assertThat(summary.single().category).isEqualTo("Groceries")
        assertThat(summary.single().total).isEqualByComparingTo("12.34")
    }

    @Test
    fun `should import the category from current Finanzguru headers`() {
        val csv = """
            Buchungstag;Referenzkonto;Kontostand;Betrag;Verwendungszweck;Analyse-Hauptkategorie;Analyse-Unterkategorie
            05.01.2026;main;1,000.00;-12.34;Supermarket;Lebenshaltung;Lebensmittel
        """.trimIndent()

        val result = service.importFinanzguru(csv.toByteArray())
        val importedTransaction = service.listTransactions().single()

        assertThat(result.imported).isEqualTo(1)
        assertThat(result.errors).isEmpty()
        assertThat(importedTransaction.category).isEqualTo("Lebenshaltung")
        assertThat(importedTransaction.account).isEqualTo("main")
    }

    @Test
    fun `should identify realistic monthly reductions from expense history`() {
        (1..9).forEach { month ->
            transaction("2025-${month.toString().padStart(2, '0')}-05", "Restaurants", "-100.00")
            transaction("2025-${month.toString().padStart(2, '0')}-06", "Groceries", "-300.00")
        }
        (10..12).forEach { month ->
            transaction("2025-$month-05", "Restaurants", "-160.00")
            transaction("2025-$month-06", "Groceries", "-300.00")
        }

        val analysis = service.analyzeExpenseReduction(12, LocalDate.parse("2025-12-31"))

        assertThat(analysis.monthsWithData).isEqualTo(12)
        assertThat(analysis.averageMonthlyExpenses).isEqualByComparingTo("415.00")
        assertThat(analysis.recentMonthlyExpenses).isEqualByComparingTo("460.00")
        assertThat(analysis.potentialMonthlySavings).isEqualByComparingTo("39.00")
        assertThat(analysis.opportunities.map { it.category }).containsExactly("Restaurants", "Groceries")
        assertThat(analysis.opportunities[0].historicalMonthlyAverage).isEqualByComparingTo("100.00")
        assertThat(analysis.opportunities[0].recentMonthlyAverage).isEqualByComparingTo("160.00")
        assertThat(analysis.opportunities[0].suggestedMonthlyReduction).isEqualByComparingTo("24.00")
        assertThat(analysis.opportunities[0].changePercent).isEqualByComparingTo("60.0")
    }

    @Test
    fun `should require between twelve and twenty four months for expense analysis`() {
        org.assertj.core.api.Assertions.assertThatThrownBy { service.analyzeExpenseReduction(6) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("months must be between 12 and 24")
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

    private fun transaction(date: String, category: String?, amount: String) {
        service.addTransaction(
            TransactionRequest(
                date = LocalDate.parse(date),
                description = "Test transaction",
                category = category,
                amount = BigDecimal(amount)
            )
        )
    }

    private fun monthlyRequest(month: String, savings: String) = MonthlyAccountPositionRequest(
        month = YearMonth.parse(month),
        savingsBudget = MoneyAmount(BigDecimal(savings), "EUR")
    )
}
