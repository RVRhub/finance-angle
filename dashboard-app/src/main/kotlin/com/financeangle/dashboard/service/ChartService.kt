package com.financeangle.dashboard.service

import com.financeangle.dashboard.model.AccountBalanceType
import com.financeangle.dashboard.model.SnapshotRecord
import org.slf4j.LoggerFactory
import org.jetbrains.letsPlot.Stat
import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomBar
import org.jetbrains.letsPlot.geom.geomLine
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.label.labs
import org.jetbrains.letsPlot.pos.positionDodge
import org.jetbrains.letsPlot.pos.positionStack
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.nio.file.Files

@Service
class ChartService(
    private val transactionService: TransactionService
) {

    private val logger = LoggerFactory.getLogger(ChartService::class.java)

    fun spendingChartSvg(options: ChartRenderOptions = ChartRenderOptions(), stacked: Boolean = true): String {
        val summary = transactionService.monthlyCategorySummary()
        if (summary.isEmpty()) return emptySvg("No spending data yet", options)
        val data = mapOf(
            "month" to summary.map { it.month.toString() },
            "category" to summary.map { it.category ?: "Uncategorised" },
            "total" to summary.map { it.total.toDouble() }
        )
        data.forEach { (key, value) ->
            logger.info("$key: $value")
        }
        val position = if (stacked) positionStack() else positionDodge()
        val plot = letsPlot(data) { x = "month"; y = "total"; fill = "category" } +
            geomBar(stat = Stat.identity, position = position) +
            labs(title = "Monthly spending by category", x = "Month", y = "Amount")
        return renderSvg(plot, options)
    }

    fun balanceChartSvg(options: ChartRenderOptions = ChartRenderOptions()): String {
        val snapshots = transactionService.listSnapshots()
        if (snapshots.isEmpty()) {
            logger.info("No balance snapshots available, returning empty chart")
            return emptySvg("No balance snapshots yet", options)
        }
        val netPositions = snapshots
            .groupBy { it.date }
            .map { (date, daily) ->
                val total = daily.fold(BigDecimal.ZERO) { acc, snapshot -> acc + snapshot.signedAmount() }
                date to total
            }
            .sortedBy { it.first }
        logger.info("Rendering net position chart for {} dates", netPositions.size)
        val data = mapOf(
            "date" to netPositions.map { it.first.toString() },
            "balance" to netPositions.map { it.second.toDouble() }
        )
        logger.debug("Net position points={}", data["balance"]?.size)
        val plot = letsPlot(data) { x = "date"; y = "balance" } +
            geomLine() +
            labs(title = "Net position over time", x = "Date", y = "Net position")
        return renderSvg(plot, options)
    }

    fun balanceByAccountTypeChartSvg(options: ChartRenderOptions = ChartRenderOptions()): String {
        val snapshots = transactionService.listSnapshots()
        if (snapshots.isEmpty()) {
            logger.info("No balance snapshots available, returning empty account-type chart")
            return emptySvg("No balance snapshots yet", options)
        }
        logger.info("Rendering account-type balance chart for {} snapshots", snapshots.size)
        val accountTypeLabel: (SnapshotRecord) -> String = { snapshot ->
            val accountName = snapshot.account ?: "Unassigned"
            "$accountName (${snapshot.type.name.lowercase()})"
        }
        val data = mapOf(
            "date" to snapshots.map { it.date.toString() },
            "balance" to snapshots.map { it.signedAmount().toDouble() },
            "accountType" to snapshots.map(accountTypeLabel)
        )
        logger.debug(
            "Account-type chart data points={} series={}",
            data["balance"]?.size,
            data["accountType"]?.distinct()?.size
        )
        val plot = letsPlot(data) { x = "date"; y = "balance"; color = "accountType" } +
            geomLine() +
            labs(title = "Balance by account and type", x = "Date", y = "Balance")
        return renderSvg(plot, options)
    }

    private fun renderSvg(plot: Plot, options: ChartRenderOptions): String {
        val tmp = Files.createTempFile("plot-", ".svg")
        ggsave(
            plot = plot + ggsize(options.width, options.height),
            filename = tmp.toString(),
            dpi = options.dpi
        )
        val svg = Files.readString(tmp)
        Files.deleteIfExists(tmp)
        return svg
    }

    fun SnapshotRecord.signedAmount(): BigDecimal =
        balance.amount.multiply(type.sign)

    private fun emptySvg(message: String, options: ChartRenderOptions) = """
        <svg xmlns="http://www.w3.org/2000/svg" width="${options.width}" height="${options.height}" viewBox="0 0 ${options.width} ${options.height}">
          <rect width="${options.width}" height="${options.height}" fill="#f8fafc" />
          <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="Arial" font-size="18" fill="#334155">$message</text>
        </svg>
    """.trimIndent()
}

data class ChartRenderOptions(
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    val dpi: Int = DEFAULT_DPI
) {
    companion object {
        const val DEFAULT_WIDTH = 1200
        const val DEFAULT_HEIGHT = 500
        const val DEFAULT_DPI = 144
        private const val MIN_DIMENSION = 320
        private const val MAX_DIMENSION = 2400
        private const val MIN_DPI = 72
        private const val MAX_DPI = 300

        fun from(width: Int?, height: Int?, dpi: Int?): ChartRenderOptions = ChartRenderOptions(
            width = width?.coerceIn(MIN_DIMENSION, MAX_DIMENSION) ?: DEFAULT_WIDTH,
            height = height?.coerceIn(MIN_DIMENSION, MAX_DIMENSION) ?: DEFAULT_HEIGHT,
            dpi = dpi?.coerceIn(MIN_DPI, MAX_DPI) ?: DEFAULT_DPI
        )
    }
}
