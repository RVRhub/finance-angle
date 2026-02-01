package com.financeangle.dashboard.service

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
import java.nio.file.Files

@Service
class ChartService(
    private val transactionService: TransactionService
) {

    fun spendingChartSvg(options: ChartRenderOptions = ChartRenderOptions(), stacked: Boolean = true): String {
        val summary = transactionService.monthlyCategorySummary()
        if (summary.isEmpty()) return emptySvg("No spending data yet", options)
        val data = mapOf(
            "month" to summary.map { it.month.toString() },
            "category" to summary.map { it.category ?: "Uncategorised" },
            "total" to summary.map { it.total.toDouble() }
        )
        val position = if (stacked) positionStack() else positionDodge()
        val plot = letsPlot(data) { x = "month"; y = "total"; fill = "category" } +
            geomBar(stat = Stat.identity, position = position) +
            labs(title = "Monthly spending by category", x = "Month", y = "Amount")
        return renderSvg(plot, options)
    }

    fun balanceChartSvg(options: ChartRenderOptions = ChartRenderOptions()): String {
        val snapshots = transactionService.listSnapshots()
        if (snapshots.isEmpty()) return emptySvg("No balance snapshots yet", options)
        val data = mapOf(
            "date" to snapshots.map { it.date.toString() },
            "balance" to snapshots.map { it.balance.toDouble() },
            "account" to snapshots.map { it.account ?: "Default" }
        )
        val plot = letsPlot(data) { x = "date"; y = "balance"; color = "account" } +
            geomLine() +
            labs(title = "Balance over time", x = "Date", y = "Balance")
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
