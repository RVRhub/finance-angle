package com.financeangle.dashboard.controller

import com.financeangle.dashboard.model.ImportResult
import com.financeangle.dashboard.model.SnapshotRecord
import com.financeangle.dashboard.model.SnapshotRequest
import com.financeangle.dashboard.model.SummaryPoint
import com.financeangle.dashboard.model.TransactionRecord
import com.financeangle.dashboard.model.TransactionRequest
import com.financeangle.dashboard.service.ChartService
import com.financeangle.dashboard.service.TransactionService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api")
class DashboardController(
    private val transactionService: TransactionService,
    private val chartService: ChartService
) {

    @PostMapping("/transactions")
    fun addTransaction(@Valid @RequestBody request: TransactionRequest): TransactionRecord =
        transactionService.addTransaction(request)

    @GetMapping("/transactions")
    fun listTransactions(): List<TransactionRecord> = transactionService.listTransactions()

    @PostMapping("/snapshots")
    fun addSnapshot(@Valid @RequestBody request: SnapshotRequest): SnapshotRecord = transactionService.addSnapshot(request)

    @GetMapping("/snapshots")
    fun listSnapshots(): List<SnapshotRecord> = transactionService.listSnapshots()

    @GetMapping("/summary/spending")
    fun summary(): List<SummaryPoint> = transactionService.monthlyCategorySummary()

    @PostMapping("/import/finanzguru", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFinanzguru(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(defaultValue = ";") delimiter: String,
        @RequestParam(defaultValue = "true") decimalComma: Boolean,
        @RequestParam(defaultValue = "dd.MM.yyyy") datePattern: String,
        @RequestParam(defaultValue = "Buchungstag") dateColumn: String,
        @RequestParam(defaultValue = "Verwendungszweck") descriptionColumn: String,
        @RequestParam(defaultValue = "Betrag") amountColumn: String,
        @RequestParam(defaultValue = "Kategorie") categoryColumn: String,
        @RequestParam(defaultValue = "Konto") accountColumn: String
    ): ImportResult {
        return transactionService.importFinanzguru(
            bytes = file.bytes,
            delimiter = delimiter.firstOrNull() ?: ';',
            decimalComma = decimalComma,
            datePattern = datePattern,
            dateColumn = dateColumn,
            descriptionColumn = descriptionColumn,
            amountColumn = amountColumn,
            categoryColumn = categoryColumn,
            accountColumn = accountColumn
        )
    }

    private val svgMediaType = MediaType.parseMediaType("image/svg+xml")

    @GetMapping("/charts/spending.svg", produces = ["image/svg+xml"])
    fun spendingChart(): ResponseEntity<String> =
        ResponseEntity.ok().contentType(svgMediaType).body(chartService.spendingChartSvg())

    @GetMapping("/charts/balance.svg", produces = ["image/svg+xml"])
    fun balanceChart(): ResponseEntity<String> =
        ResponseEntity.ok().contentType(svgMediaType).body(chartService.balanceChartSvg())
}
