package com.financeangle.dashboard.service

import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomBar
import org.jetbrains.letsPlot.geom.geomLine
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.label.labs
import org.jetbrains.letsPlot.Stat
import org.jetbrains.letsPlot.pos.positionStack
import org.springframework.stereotype.Service
import java.nio.file.Files

@Service
class ChartService(
    private val transactionService: TransactionService
) {

    fun spendingChartSvg(): String {
        val summary = transactionService.monthlyCategorySummary()
        if (summary.isEmpty()) return emptySvg("No spending data yet")
        val data = mapOf(
            "month" to summary.map { it.month.toString() },
            "category" to summary.map { it.category ?: "Uncategorised" },
            "total" to summary.map { it.total.toDouble() }
        )
        val plot = letsPlot(data) { x = "month"; y = "total"; fill = "category" } +
            geomBar(stat = Stat.identity, position = positionStack()) +
            labs(title = "Monthly spending by category", x = "Month", y = "Amount")
        return renderSvg(plot)
    }

    fun balanceChartSvg(): String {
        val snapshots = transactionService.listSnapshots()
        if (snapshots.isEmpty()) return emptySvg("No balance snapshots yet")
        val data = mapOf(
            "date" to snapshots.map { it.date.toString() },
            "balance" to snapshots.map { it.balance.toDouble() },
            "account" to snapshots.map { it.account ?: "Default" }
        )
        val plot = letsPlot(data) { x = "date"; y = "balance"; color = "account" } +
            geomLine() +
            labs(title = "Balance over time", x = "Date", y = "Balance")
        return renderSvg(plot)
    }

    private fun renderSvg(plot: Plot): String {
        val tmp = Files.createTempFile("plot-", ".svg")
        ggsave(plot = plot, filename = tmp.toString())
        val svg = Files.readString(tmp)
        Files.deleteIfExists(tmp)
        return svg
    }

    private fun emptySvg(message: String) = """
        <svg xmlns="http://www.w3.org/2000/svg" width="900" height="200" viewBox="0 0 900 200">
          <rect width="900" height="200" fill="#f8fafc" />
          <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="Arial" font-size="18" fill="#334155">$message</text>
        </svg>
    """.trimIndent()
}
