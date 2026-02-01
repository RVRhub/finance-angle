package com.financeangle.dashboard.service

import com.financeangle.dashboard.model.AccountBalanceSnapshot
import com.financeangle.dashboard.model.AccountBalanceSnapshotRequest
import com.financeangle.dashboard.model.AccountKind
import com.financeangle.dashboard.model.AccountPositionSnapshotAccounts
import com.financeangle.dashboard.model.AccountPositionSnapshotRecord
import com.financeangle.dashboard.model.AccountPositionSnapshots
import com.financeangle.dashboard.model.AccountRecord
import com.financeangle.dashboard.model.AccountRequest
import com.financeangle.dashboard.model.AccountSnapshot
import com.financeangle.dashboard.model.Accounts
import com.financeangle.dashboard.model.FxRate
import com.financeangle.dashboard.model.ImportResult
import com.financeangle.dashboard.model.MoneyAmount
import com.financeangle.dashboard.model.MonthlyAccountPositionRequest
import com.financeangle.dashboard.model.SnapshotRecord
import com.financeangle.dashboard.model.SnapshotTotals
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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(TransactionService::class.java)

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

    fun updateAccount(identifier: String, request: AccountRequest): AccountRecord = transaction(database) {
        val existing = findAccountByIdentifier(identifier)
            ?: throw IllegalArgumentException("Account not found")
        val accountId = existing[Accounts.id]
        Accounts.update({ Accounts.id eq accountId }) { row ->
            row[Accounts.name] = request.name.trim()
            row[Accounts.provider] = request.provider
            row[Accounts.currency] = request.currency
            row[Accounts.externalId] = request.accountId
            row[Accounts.accountNumber] = request.accountNumber
        }
        asAccountRecord(Accounts.selectAll().where { Accounts.id eq accountId }.single())
    }

    fun deleteAccount(identifier: String): AccountRecord = transaction(database) {
        val existing = findAccountByIdentifier(identifier)
            ?: throw IllegalArgumentException("Account not found")
        val accountId = existing[Accounts.id]
        val record = asAccountRecord(existing)

        Transactions.update({ Transactions.accountId eq accountId }) { row -> row[Transactions.accountId] = null }
        AccountBalanceSnapshot.update({ AccountBalanceSnapshot.accountId eq accountId }) { row ->
            row[AccountBalanceSnapshot.accountId] = null
        }
        AccountPositionSnapshotAccounts.update({ AccountPositionSnapshotAccounts.accountId eq accountId }) { row ->
            row[AccountPositionSnapshotAccounts.accountId] = null
        }

        Accounts.deleteWhere { Accounts.id eq accountId }
        record
    }

    fun addTransaction(request: TransactionRequest): TransactionRecord = transaction(database) {
        logger.info("Adding transaction request: $request")
        val accountId = resolveAccountByAccountNumber(request.accountNumber)
        logger.info("accountId = $accountId")
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

    fun resetTransactions(): Int = transaction(database) {
        Transactions.deleteAll()
    }

    fun addSnapshot(request: AccountBalanceSnapshotRequest): SnapshotRecord = transaction(database) {
        val accountId = resolveAccountByName(request.account)
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

    fun addMonthlyAccountPosition(request: MonthlyAccountPositionRequest): AccountPositionSnapshotRecord = transaction(database) {
        val targetMonth = YearMonth.now()
        val savingsBudget = request.savingsBudget ?: MoneyAmount(BigDecimal.ZERO, "EUR")
        val monthStart = targetMonth.atDay(1)
        val monthEnd = targetMonth.atEndOfMonth()

        // replace existing snapshot for the month to keep a single source of truth
        AccountPositionSnapshots.deleteWhere { AccountPositionSnapshots.snapshotMonth eq monthStart }

        val accountsTable = Accounts.selectAll().associateBy { it[Accounts.id].value }
        val snapshots = AccountBalanceSnapshot.selectAll()
            .where { AccountBalanceSnapshot.date lessEq monthEnd }
            .orderBy(AccountBalanceSnapshot.date to SortOrder.DESC)
            .toList()

        data class AccountAccumulator(
            val accountId: String,
            var debit: MoneyAmount? = null,
            var credit: MoneyAmount? = null,
            var loans: MoneyAmount? = null
        )

        val accounts = mutableMapOf<String, AccountAccumulator>()
        snapshots.forEach { row ->
            val accountRef = row[AccountBalanceSnapshot.accountId]
            val accountRow = accountRef?.let { accountsTable[it.value] }
            val accountKey = accountRow?.let { resolvedAccountId(it) } ?: accountRef?.value?.toString() ?: "unassigned"
            val accumulator = accounts.getOrPut(accountKey) { AccountAccumulator(accountId = accountKey) }
            val money = MoneyAmount(row[AccountBalanceSnapshot.balance], row[AccountBalanceSnapshot.balanceCurrency])
            when (row[AccountBalanceSnapshot.type]) {
                com.financeangle.dashboard.model.AccountBalanceType.DEBIT,
                com.financeangle.dashboard.model.AccountBalanceType.INVESTMENT -> if (accumulator.debit == null) accumulator.debit = money
                com.financeangle.dashboard.model.AccountBalanceType.CREDIT -> if (accumulator.credit == null) accumulator.credit = money
                com.financeangle.dashboard.model.AccountBalanceType.LOAN -> if (accumulator.loans == null) accumulator.loans = money
            }
        }

        val snapshotId = AccountPositionSnapshots.insertAndGetId { row ->
            row[AccountPositionSnapshots.snapshotMonth] = monthStart
            row[AccountPositionSnapshots.savingsBudget] = savingsBudget.amount
            row[AccountPositionSnapshots.savingsCurrency] = savingsBudget.currency
        }

        accounts.values.forEach { account ->
            val accountRow = accountsTable.values.firstOrNull { resolvedAccountId(it) == account.accountId }
            val accountRef = accountRow?.get(Accounts.id)
            AccountPositionSnapshotAccounts.insertAndGetId { row ->
                row[AccountPositionSnapshotAccounts.snapshotId] = snapshotId
                row[AccountPositionSnapshotAccounts.accountId] = accountRef
                row[AccountPositionSnapshotAccounts.accountName] = account.accountId
                row[AccountPositionSnapshotAccounts.debitAmount] = account.debit?.amount
                row[AccountPositionSnapshotAccounts.debitCurrency] = account.debit?.currency
                row[AccountPositionSnapshotAccounts.creditAmount] = account.credit?.amount
                row[AccountPositionSnapshotAccounts.creditCurrency] = account.credit?.currency
                row[AccountPositionSnapshotAccounts.loansAmount] = account.loans?.amount
                row[AccountPositionSnapshotAccounts.loansCurrency] = account.loans?.currency
                row[AccountPositionSnapshotAccounts.sharedDebitAmount] = null
                row[AccountPositionSnapshotAccounts.sharedDebitCurrency] = null
            }
        }

        val snapshotRow = AccountPositionSnapshots.selectAll().where { AccountPositionSnapshots.id eq snapshotId }.single()
        val accountRecords = AccountPositionSnapshotAccounts.selectAll()
            .where { AccountPositionSnapshotAccounts.snapshotId eq snapshotId }
            .map { asAccountSnapshot(it) }
        asAccountPositionSnapshotRecord(snapshotRow, accountRecords)
    }

    fun listMonthlyAccountPositions(): List<AccountPositionSnapshotRecord> = transaction(database) {
        val snapshots = AccountPositionSnapshots.selectAll()
            .orderBy(AccountPositionSnapshots.snapshotMonth to SortOrder.DESC)
            .toList()
        if (snapshots.isEmpty()) return@transaction emptyList()
        val accountsBySnapshot = AccountPositionSnapshotAccounts.selectAll()
            .groupBy { it[AccountPositionSnapshotAccounts.snapshotId].value }
        snapshots.map { snapshot ->
            val accounts = accountsBySnapshot[snapshot[AccountPositionSnapshots.id].value]
                ?.map { asAccountSnapshot(it) }
                .orEmpty()
            asAccountPositionSnapshotRecord(snapshot, accounts)
        }
    }

    fun monthlyCategorySummary(monthsBack: Int? = null): List<SummaryPoint> {
        val transactions = transaction(database) {
            val excludedDescriptionPatterns = listOf("Sent from N26", "Roman", "Family")
            val query = Transactions.selectAll()
            if (monthsBack != null) {
                require(monthsBack > 0) { "monthsBack must be positive" }
                val endDate = LocalDate.now()
                val startDate = endDate.minusMonths(monthsBack.toLong() - 1).withDayOfMonth(1)
                query.andWhere { Transactions.date.between(startDate, endDate) }
            }
            excludedDescriptionPatterns.forEach { pattern ->
                query.andWhere { Transactions.description notLike "%$pattern%" }
            }
            query.orderBy(Transactions.date to SortOrder.ASC).map { asTransactionRecord(it) }
        }

        val df = transactions.toDataFrame()
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
        decimalComma: Boolean = false,
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
                        accountNumber = account,
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

    private fun resolveAccountByName(name: String?): EntityID<Int>? {
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

    private fun resolveAccountByAccountNumber(accountNumber: String?): EntityID<Int>? {
        if (accountNumber.isNullOrBlank()) return null
        val cleaned = accountNumber.trim()
        val existing = findAccountByAccountNumber(cleaned)
        return if (existing != null) {
            existing[Accounts.id]
        } else {
            Accounts.insertAndGetId { row ->
                row[Accounts.name] = cleaned
                row[Accounts.accountNumber] = cleaned
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

    private fun asAccountPositionSnapshotRecord(
        snapshotRow: ResultRow,
        accounts: List<AccountSnapshot>
    ): AccountPositionSnapshotRecord {
        val savingsBudget = MoneyAmount(
            amount = snapshotRow[AccountPositionSnapshots.savingsBudget],
            currency = snapshotRow[AccountPositionSnapshots.savingsCurrency]
        )
        val totalDebit = sumMoneyAmounts(accounts.mapNotNull { it.debit }, savingsBudget.currency)
        val totalSharedDebit = sumMoneyAmounts(accounts.mapNotNull { it.sharedDebitWithWife }, savingsBudget.currency)
        val totalCredit = sumMoneyAmounts(accounts.mapNotNull { it.credit }, savingsBudget.currency)
        val totalLoans = sumMoneyAmounts(accounts.mapNotNull { it.loans }, savingsBudget.currency)
        logger.info("totalDebit: $totalDebit, totalSharedDebit: $totalSharedDebit, savingsBudget: $savingsBudget");
        logger.info("totalCredit: $totalCredit, totalLoans: $totalLoans");

        val netPositionAmount = totalDebit.amount
            .add(totalSharedDebit.amount)
            .add(savingsBudget.amount)
            .subtract(totalCredit.amount)
            .subtract(totalLoans.amount)
        logger.info("netPositionAmount: $netPositionAmount")
        val netPosition = MoneyAmount(netPositionAmount, savingsBudget.currency)
        return AccountPositionSnapshotRecord(
            id = snapshotRow[AccountPositionSnapshots.id].value,
            month = YearMonth.from(snapshotRow[AccountPositionSnapshots.snapshotMonth]),
            accounts = accounts,
            savingsBudget = savingsBudget,
            totals = SnapshotTotals(
                totalDebit = totalDebit,
                totalSharedDebit = totalSharedDebit,
                totalCredit = totalCredit,
                totalLoans = totalLoans,
                savingsBudget = savingsBudget,
                netPosition = netPosition
            )
        )
    }

    private fun asAccountSnapshot(row: ResultRow): AccountSnapshot {
        fun money(amount: BigDecimal?, currency: String?) = amount?.let { MoneyAmount(it, currency ?: "EUR") }
        return AccountSnapshot(
            accountId = row[AccountPositionSnapshotAccounts.accountName],
            debit = money(row[AccountPositionSnapshotAccounts.debitAmount], row[AccountPositionSnapshotAccounts.debitCurrency]),
            credit = money(row[AccountPositionSnapshotAccounts.creditAmount], row[AccountPositionSnapshotAccounts.creditCurrency]),
            loans = money(row[AccountPositionSnapshotAccounts.loansAmount], row[AccountPositionSnapshotAccounts.loansCurrency]),
            sharedDebitWithWife = money(
                row[AccountPositionSnapshotAccounts.sharedDebitAmount],
                row[AccountPositionSnapshotAccounts.sharedDebitCurrency]
            )
        )
    }

    private fun asAccountRecord(row: ResultRow) = AccountRecord(
        accountId = resolvedAccountId(row),
        name = row[Accounts.name],
        accountNumber = row[Accounts.accountNumber],
        provider = row[Accounts.provider],
        currency = row[Accounts.currency]
    )

    private fun sumMoneyAmounts(values: List<MoneyAmount>, fallbackCurrency: String): MoneyAmount {
        if (values.isEmpty()) return MoneyAmount(BigDecimal.ZERO, fallbackCurrency)
        val currency = values.first().currency
        val total = values.fold(BigDecimal.ZERO) { acc, money -> acc + money.amount }
        return MoneyAmount(total, currency)
    }

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

    private fun findAccountByIdentifier(identifier: String): ResultRow? {
        val cleaned = identifier.trim()
        return Accounts.selectAll().where { Accounts.externalId eq cleaned }.firstOrNull()
            ?: cleaned.toIntOrNull()?.let { id -> Accounts.selectAll().where { Accounts.id eq id }.firstOrNull() }
            ?: Accounts.selectAll().where { Accounts.name eq cleaned }.firstOrNull()
    }

    private fun findAccountByName(name: String): ResultRow? =
        Accounts.selectAll().where { Accounts.name eq name }.firstOrNull()

    private fun findAccountByAccountNumber(accountNumber: String): ResultRow? =
        Accounts.selectAll().where { Accounts.accountNumber eq accountNumber }.firstOrNull()

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
