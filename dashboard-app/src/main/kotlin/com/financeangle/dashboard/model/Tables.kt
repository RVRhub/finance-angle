package com.financeangle.dashboard.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date
import java.math.BigDecimal

object Accounts : IntIdTable(name = "accounts") {
    val name = varchar("name", 128)
    val provider = varchar("provider", 128).nullable()
    val currency = varchar("currency", 3).default("EUR")
}

object Transactions : IntIdTable(name = "transactions") {
    val accountId = reference("account_id", Accounts).nullable()
    val date = date("date")
    val description = varchar("description", 512)
    val category = varchar("category", 128).nullable()
    val amount = decimal("amount", 12, 2)
    val origin = varchar("origin", 32)
}

object Snapshots : IntIdTable(name = "snapshots") {
    val accountId = reference("account_id", Accounts).nullable()
    val date = date("date")
    val balance = decimal("balance", 12, 2)
    val note = varchar("note", 256).nullable()
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
    val balance: BigDecimal,
    val account: String?,
    val note: String?
)
