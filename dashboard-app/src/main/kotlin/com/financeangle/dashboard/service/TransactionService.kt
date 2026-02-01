package com.financeangle.dashboard.service

import com.financeangle.dashboard.model.AccountBalanceSnapshot
import com.financeangle.dashboard.model.AccountBalanceSnapshotRequest
import com.financeangle.dashboard.model.AccountKind
import com.financeangle.dashboard.model.AccountRecord
import com.financeangle.dashboard.model.AccountRequest
import com.financeangle.dashboard.model.Accounts
import com.financeangle.dashboard.model.FxRate
import com.financeangle.dashboard.model.ImportResult
import com.financeangle.dashboard.model.MoneyAmount
import com.financeangle.dashboard.model.SnapshotRecord
import com.financeangle.dashboard.model.SummaryPoint
import com.financeangle.dashboard.model.TransactionRecord
import com.financeangle.dashboard.model.TransactionRequest
import com.financeangle.dashboard.model.Transactions
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.springframework.stereotype.Service
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.isEmpty
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.sortBy
import org.jetbrains.kotlinx.dataframe.api.sum
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class TransactionService(
    private val database: Database
) {

    fun addAccount(request: AccountRequest): AccountRecord = transaction(database) {
        val existing = findAccount(request.accountId, request.name)
        val id = if (existing != null) {
            Accounts.update({ Accounts.id eq existing[Accounts.id].value }) { row ->
                row[Accounts.name] = request.name.trim()
                row[Accounts.provider] = request.provider
                row[Accounts.currency] = request.currency
                row[Accounts.externalId] = request.accountId
                row[Accounts.accountNumber] = request.accountNumber
            }
            existing[Accounts.id]
        } else {
            Accounts.insertAndGetId { row ->
                row[Accounts.name] = request.name.trim()
                row[Accounts.provider] = request.provider
                row[Accounts.currency] = request.currency
                row[Accounts.externalId] = request.accountId
                row[Accounts.accountNumber] = request.accountNumber
            }
        }
        asAccountRecord(Accounts.selectAll().where { Accounts.id eq id }.single())
    }

    fun listAccounts(): List<AccountRecord> = transaction(database) {
        Accounts.selectAll().orderBy(Accounts.name to SortOrder.ASC).map { asAccountRecord(it) }
    }

    fun addTransaction(request: TransactionRequest): TransactionRecord = transaction(database) {
        val accountId = resolveAccount(request.account)
        val id = Transactions.insertAndGetId { row ->
            row[Transactions.accountId] = accountId
            row[Transactions.date] = request.date
            row[Transactions.description] = request.description
            row[Transactions.category] = request.category
            row[Transactions.amount] = request.amount
            row[Transactions.origin] = "manual"
        }
        asTransactionRecord(Transactions.selectAll().where { Transactions.id eq id }.single())
    }

    fun listTransactions(): List<TransactionRecord> = transaction(database) {
        Transactions.selectAll().orderBy(Transactions.date to SortOrder.ASC).map { asTransactionRecord(it) }
    }

    fun addSnapshot(request: AccountBalanceSnapshotRequest): SnapshotRecord = transaction(database) {
        val accountId = resolveAccount(request.account)
        val balance = balanceInEur(request.original, request.fxToEur)
        val id = AccountBalanceSnapshot.insertAndGetId { row ->
            row[AccountBalanceSnapshot.accountId] = accountId
            row[AccountBalanceSnapshot.type] = request.type
            row[AccountBalanceSnapshot.kind] = request.kind
            row[AccountBalanceSnapshot.date] = request.date
            row[AccountBalanceSnapshot.balance] = balance
            row[AccountBalanceSnapshot.balanceCurrency] = "EUR"
            row[AccountBalanceSnapshot.originalAmount] = request.original.amount
            row[AccountBalanceSnapshot.originalCurrency] = request.original.currency
            row[AccountBalanceSnapshot.fxRate] = request.fxToEur?.rate
            row[AccountBalanceSnapshot.fxFromCurrency] = request.fxToEur?.fromCurrency
            row[AccountBalanceSnapshot.fxToCurrency] = request.fxToEur?.toCurrency
            row[AccountBalanceSnapshot.fxRateDate] = request.fxToEur?.rateDate
            row[AccountBalanceSnapshot.fxSource] = request.fxToEur?.source
            row[AccountBalanceSnapshot.note] = request.note
        }
        asSnapshotRecord(AccountBalanceSnapshot.selectAll().where { AccountBalanceSnapshot.id eq id }.single())
    }

    fun deleteSnapshot(id: String): SnapshotRecord = transaction(database) {
        val snapshotId = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid snapshot id")
        val existing = AccountBalanceSnapshot.selectAll().where { AccountBalanceSnapshot.id eq snapshotId }.firstOrNull()
            ?: throw IllegalArgumentException("Snapshot not found")
        val record = asSnapshotRecord(existing)
        AccountBalanceSnapshot.deleteWhere { AccountBalanceSnapshot.id eq snapshotId }
        record
    }

    fun listSnapshots(): List<SnapshotRecord> = transaction(database) {
        AccountBalanceSnapshot.selectAll().orderBy(AccountBalanceSnapshot.date to SortOrder.ASC).map { asSnapshotRecord(it) }
    }

    fun monthlyCategorySummary(): List<SummaryPoint> {
        val df = listTransactions().toDataFrame()
        if (df.isEmpty()) return emptyList()

        val summary = df
            .add("month") { YearMonth.from(it["date"] as LocalDate) }
            .add("categorySafe") { (it["category"] as String?) ?: "Uncategorised" }
            .add("amountDouble") { (it["amount"] as BigDecimal).toDouble() }
            .groupBy("month", "categorySafe")
            .aggregate {
                sum("amountDouble") into "total"
            }
            .sortBy("month", "categorySafe")

        return summary.rows().map { row ->
            val totalValue = when (val value = row["total"]) {
                is Double -> value
                is Number -> value.toDouble()
                else -> 0.0
            }
            SummaryPoint(
                month = row["month"] as YearMonth,
                category = row["categorySafe"] as String,
                total = BigDecimal.valueOf(totalValue)
            )
        }
    }

    fun importFinanzguru(
        bytes: ByteArray,
        delimiter: Char = ';',
        decimalComma: Boolean = true,
        datePattern: String = "dd.MM.yyyy",
        dateColumn: String = "Buchungstag",
        accountColumn: String = "Konto",
        accountStateColumn: String = "Kontostand",
        amountColumn: String = "Betrag",
        descriptionColumn: String = "Verwendungszweck",
        categoryColumn: String = "Kategorie"
    ): ImportResult {
        val errors = mutableListOf<String>()
        var imported = 0
        val reader = csvReader {
            this.delimiter = delimiter
            skipEmptyLine = true
        }
        val rows = reader.readAllWithHeader(bytes.inputStream())
        rows.forEachIndexed { index, row ->
            try {
                val date = parseDate(row[dateColumn], datePattern)
                val amount = parseAmount(row[amountColumn], decimalComma)
                val accountState = parseAmount(row[accountStateColumn], decimalComma)
                val description = row[descriptionColumn] ?: ""
                val category = row[categoryColumn]
                val account = row[accountColumn]
                addTransaction(
                    TransactionRequest(
                        date = date,
                        description = description,
                        category = category,
                        amount = amount,
                        account = account,
                        accountState = accountState
                    )
                )
                imported += 1
            } catch (ex: Exception) {
                errors += "Row ${index + 1}: ${ex.message}"
            }
        }
        return ImportResult(imported = imported, skipped = rows.size - imported, errors = errors)
    }

    private fun resolveAccount(name: String?): EntityID<Int>? {
        if (name.isNullOrBlank()) return null
        val cleaned = name.trim()
        val existing = findAccountByName(cleaned)
        return if (existing != null) {
            existing[Accounts.id]
        } else {
            Accounts.insertAndGetId { row ->
                row[Accounts.name] = cleaned
                row[Accounts.provider] = null
            }
        }
    }

    private fun parseDate(raw: String?, pattern: String): LocalDate {
        if (raw.isNullOrBlank()) throw IllegalArgumentException("Missing date")
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return try {
            LocalDate.parse(raw.trim(), formatter)
        } catch (ex: DateTimeParseException) {
            LocalDate.parse(raw.trim())
        }
    }

    private fun parseAmount(raw: String?, decimalComma: Boolean): BigDecimal {
        if (raw.isNullOrBlank()) throw IllegalArgumentException("Missing amount")
        val stripped = raw.replace("€", "").replace(" ", "").trim()
        val normalized = if (decimalComma) {
            stripped.replace(".", "").replace(",", ".")
        } else {
            stripped.replace(",", "")
        }
        return normalized.toBigDecimal()
    }

    private fun asTransactionRecord(row: ResultRow) = TransactionRecord(
        id = row[Transactions.id].value,
        date = row[Transactions.date],
        description = row[Transactions.description],
        category = row[Transactions.category],
        amount = row[Transactions.amount],
        account = row[Transactions.accountId]?.let { id ->
            Accounts.selectAll().where { Accounts.id eq id }.firstOrNull()?.get(Accounts.name)
        },
        origin = row[Transactions.origin]
    )

    private fun asSnapshotRecord(row: ResultRow): SnapshotRecord {
        val balanceAmount = row[AccountBalanceSnapshot.balance]
        val balanceCurrency = row[AccountBalanceSnapshot.balanceCurrency]
        val originalAmount = row[AccountBalanceSnapshot.originalAmount] ?: balanceAmount
        val originalCurrency = row[AccountBalanceSnapshot.originalCurrency] ?: balanceCurrency
        val fxRate = row[AccountBalanceSnapshot.fxRate]?.let { rate ->
            FxRate(
                fromCurrency = row[AccountBalanceSnapshot.fxFromCurrency] ?: originalCurrency,
                toCurrency = row[AccountBalanceSnapshot.fxToCurrency] ?: balanceCurrency,
                rate = rate,
                rateDate = row[AccountBalanceSnapshot.fxRateDate] ?: row[AccountBalanceSnapshot.date],
                source = row[AccountBalanceSnapshot.fxSource]
            )
        }
        return SnapshotRecord(
            id = row[AccountBalanceSnapshot.id].value,
            date = row[AccountBalanceSnapshot.date],
            balance = MoneyAmount(balanceAmount, balanceCurrency),
            original = MoneyAmount(originalAmount, originalCurrency),
            fxToEur = fxRate,
            type = row[AccountBalanceSnapshot.type],
            kind = row[AccountBalanceSnapshot.kind],
            account = row[AccountBalanceSnapshot.accountId]?.let { id ->
                Accounts.selectAll().where { Accounts.id eq id }.firstOrNull()?.get(Accounts.name)
            },
            note = row[AccountBalanceSnapshot.note]
        )
    }

    private fun asAccountRecord(row: ResultRow) = AccountRecord(
        accountId = resolvedAccountId(row),
        name = row[Accounts.name],
        accountNumber = row[Accounts.accountNumber],
        provider = row[Accounts.provider],
        currency = row[Accounts.currency]
    )

    private fun findAccount(accountId: String?, name: String): ResultRow? {
        val cleanedName = name.trim()
        return when {
            !accountId.isNullOrBlank() ->
                Accounts.selectAll().where { Accounts.externalId eq accountId }.firstOrNull()
                    ?: accountId.toIntOrNull()?.let { id -> Accounts.selectAll().where { Accounts.id eq id }.firstOrNull() }
                    ?: Accounts.selectAll().where { Accounts.name eq cleanedName }.firstOrNull()
            else -> Accounts.selectAll().where { Accounts.name eq cleanedName }.firstOrNull()
        }
    }

    private fun findAccountByName(name: String): ResultRow? =
        Accounts.selectAll().where { Accounts.name eq name }.firstOrNull()

    private fun resolvedAccountId(row: ResultRow): String =
        row[Accounts.externalId] ?: row[Accounts.id].value.toString()

    private fun balanceInEur(original: MoneyAmount, fxRate: FxRate?): BigDecimal {
        val isEuro = original.currency.equals("EUR", ignoreCase = true)
        if (isEuro) {
            if (fxRate != null) {
                require(fxRate.fromCurrency.equals(original.currency, ignoreCase = true)) {
                    "FX rate currency ${'$'}{fxRate.fromCurrency} does not match original currency ${'$'}{original.currency}"
                }
                require(fxRate.toCurrency.equals("EUR", ignoreCase = true)) {
                    "FX rate must convert to EUR"
                }
            }
            return original.amount.setScale(2, RoundingMode.HALF_UP)
        }
        val rate = fxRate ?: throw IllegalArgumentException("FX rate is required for non-EUR balances")
        require(rate.fromCurrency.equals(original.currency, ignoreCase = true)) {
            "FX rate currency ${'$'}{rate.fromCurrency} does not match original currency ${'$'}{original.currency}"
        }
        require(rate.toCurrency.equals("EUR", ignoreCase = true)) {
            "FX rate must convert to EUR"
        }
        return original.amount.multiply(rate.rate).setScale(5, RoundingMode.DOWN)
    }

    fun AccountKind.description(): String = when (this) {
        AccountKind.CHECKING -> "Primary bank account used for daily income and expenses."
        AccountKind.SAVINGS -> "Bank account used to store money for future use with low risk."
        AccountKind.CASH -> "Physical cash or cash equivalents you have immediate access to."
        AccountKind.CREDIT_CARD -> "Revolving credit account where the balance represents money owed."
        AccountKind.LOAN -> "Borrowed money that must be repaid over time with interest."
        AccountKind.BROKER -> "Investment account holding stocks, ETFs, or other securities."
        AccountKind.CRYPTO_WALLET -> "Wallet holding cryptocurrencies valued at current market prices."
    }
}
