package com.financeangle.dashboard.service

import com.financeangle.dashboard.model.Accounts
import com.financeangle.dashboard.model.ImportResult
import com.financeangle.dashboard.model.SnapshotRecord
import com.financeangle.dashboard.model.SnapshotRequest
import com.financeangle.dashboard.model.Snapshots
import com.financeangle.dashboard.model.SummaryPoint
import com.financeangle.dashboard.model.TransactionRecord
import com.financeangle.dashboard.model.TransactionRequest
import com.financeangle.dashboard.model.Transactions
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.springframework.stereotype.Service
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.into
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
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class TransactionService(
    private val database: Database
) {

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
        asTransactionRecord(Transactions.select { Transactions.id eq id }.single())
    }

    fun listTransactions(): List<TransactionRecord> = transaction(database) {
        Transactions.selectAll().orderBy(Transactions.date to SortOrder.ASC).map { asTransactionRecord(it) }
    }

    fun addSnapshot(request: SnapshotRequest): SnapshotRecord = transaction(database) {
        val accountId = resolveAccount(request.account)
        val id = Snapshots.insertAndGetId { row ->
            row[Snapshots.accountId] = accountId
            row[Snapshots.date] = request.date
            row[Snapshots.balance] = request.balance
            row[Snapshots.note] = request.note
        }
        asSnapshotRecord(Snapshots.select { Snapshots.id eq id }.single())
    }

    fun listSnapshots(): List<SnapshotRecord> = transaction(database) {
        Snapshots.selectAll().orderBy(Snapshots.date to SortOrder.ASC).map { asSnapshotRecord(it) }
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
        descriptionColumn: String = "Verwendungszweck",
        amountColumn: String = "Betrag",
        categoryColumn: String = "Kategorie",
        accountColumn: String = "Konto"
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
                val description = row[descriptionColumn] ?: ""
                val category = row[categoryColumn]
                val account = row[accountColumn]
                addTransaction(
                    TransactionRequest(
                        date = date,
                        description = description,
                        category = category,
                        amount = amount,
                        account = account
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
        val existing = Accounts.select { Accounts.name eq cleaned }.firstOrNull()
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
            Accounts.select { Accounts.id eq id }.firstOrNull()?.get(Accounts.name)
        },
        origin = row[Transactions.origin]
    )

    private fun asSnapshotRecord(row: ResultRow) = SnapshotRecord(
        id = row[Snapshots.id].value,
        date = row[Snapshots.date],
        balance = row[Snapshots.balance],
        account = row[Snapshots.accountId]?.let { id ->
            Accounts.select { Accounts.id eq id }.firstOrNull()?.get(Accounts.name)
        },
        note = row[Snapshots.note]
    )
}
