package com.financeangle.dashboard.service

import com.financeangle.dashboard.model.DashboardBalanceData
import com.financeangle.dashboard.model.DashboardBalanceSeries
import com.financeangle.dashboard.model.DashboardData
import com.financeangle.dashboard.model.SnapshotRecord
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class DashboardDataService(
    private val transactionService: TransactionService
) {

    fun getDashboardData(months: Int = DEFAULT_MONTHS): DashboardData {
        require(months in 1..MAX_MONTHS) { "months must be between 1 and $MAX_MONTHS" }
        return DashboardData(
            monthlyPositions = transactionService.compareMonthlyAccountPositions(),
            spending = transactionService.monthlyCategorySummary(months),
            balances = buildBalanceData(transactionService.listSnapshots())
        )
    }

    internal fun buildBalanceData(snapshots: List<SnapshotRecord>): DashboardBalanceData {
        if (snapshots.isEmpty()) return DashboardBalanceData(dates = emptyList(), netPosition = emptyList(), series = emptyList())

        val dates = snapshots.map { it.date }.distinct().sorted()
        val grouped = snapshots
            .sortedWith(compareBy<SnapshotRecord> { it.date }.thenBy { it.id })
            .groupBy { SeriesKey(it.account ?: "Unassigned", it.type.name) }

        val series = grouped.entries
            .sortedWith(compareBy({ it.key.account.lowercase() }, { it.key.type }))
            .map { (key, accountSnapshots) ->
                DashboardBalanceSeries(
                    key = "${key.account}|${key.type}",
                    label = "${key.account} (${key.type.lowercase()})",
                    type = accountSnapshots.first().type,
                    values = dates.map { date ->
                        accountSnapshots.lastOrNull { it.date <= date }?.signedAmount()
                    }
                )
            }

        val netPosition = dates.indices.map { index ->
            series.fold(BigDecimal.ZERO) { total, accountSeries ->
                total + (accountSeries.values[index] ?: BigDecimal.ZERO)
            }
        }
        return DashboardBalanceData(dates = dates, netPosition = netPosition, series = series)
    }

    private fun SnapshotRecord.signedAmount(): BigDecimal = balance.amount.multiply(type.sign)

    private data class SeriesKey(val account: String, val type: String)

    companion object {
        const val DEFAULT_MONTHS = 12
        const val MAX_MONTHS = 120
    }
}
