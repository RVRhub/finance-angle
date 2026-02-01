package com.financeangle.dashboard.config

import com.financeangle.dashboard.model.AccountBalanceSnapshot
import com.financeangle.dashboard.model.AccountPositionSnapshotAccounts
import com.financeangle.dashboard.model.AccountPositionSnapshots
import com.financeangle.dashboard.model.Accounts
import com.financeangle.dashboard.model.Transactions
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

@Configuration
class DatabaseConfig(
    @Value("\${app.db-path:./data/dashboard.db}")
    private val dbPath: String
) {

    @Bean
    fun dataSource(): DataSource {
        val absolutePath = Path.of(dbPath).toAbsolutePath()
        Files.createDirectories(absolutePath.parent)
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$absolutePath"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 5
            isAutoCommit = false
        }
        return HikariDataSource(config)
    }

    @Bean
    fun database(dataSource: DataSource): Database {
        val db = Database.connect(dataSource)
        TransactionManager.manager.defaultIsolationLevel = java.sql.Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                Accounts,
                Transactions,
                AccountBalanceSnapshot,
                AccountPositionSnapshots,
                AccountPositionSnapshotAccounts
            )
        }
        return db
    }
}
