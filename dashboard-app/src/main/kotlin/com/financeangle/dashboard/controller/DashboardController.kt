package com.financeangle.dashboard.controller

import com.financeangle.dashboard.model.AccountBalanceSnapshotRequest
import com.financeangle.dashboard.model.AccountRecord
import com.financeangle.dashboard.model.AccountRequest
import com.financeangle.dashboard.model.ImportResult
import com.financeangle.dashboard.model.MonthlyAccountPositionRequest
import com.financeangle.dashboard.model.SnapshotRecord
import com.financeangle.dashboard.model.SummaryPoint
import com.financeangle.dashboard.model.TransactionRecord
import com.financeangle.dashboard.model.TransactionRequest
import com.financeangle.dashboard.model.AccountPositionSnapshotResponse
import com.financeangle.dashboard.service.ChartRenderOptions
import com.financeangle.dashboard.service.ChartService
import com.financeangle.dashboard.service.TransactionService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
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

    @PostMapping("/accounts")
    fun addAccount(@Valid @RequestBody request: AccountRequest): AccountRecord = transactionService.addAccount(request)

    @PutMapping("/accounts/{id}")
    fun updateAccount(
        @PathVariable id: String,
        @Valid @RequestBody request: AccountRequest
    ): AccountRecord = transactionService.updateAccount(id, request)

    @DeleteMapping("/accounts/{id}")
    fun deleteAccount(@PathVariable id: String): AccountRecord = transactionService.deleteAccount(id)

    @GetMapping("/accounts")
    fun listAccounts(): List<AccountRecord> = transactionService.listAccounts()

    @PostMapping("/transactions")
    fun addTransaction(@Valid @RequestBody request: TransactionRequest): TransactionRecord =
        transactionService.addTransaction(request)

    @GetMapping("/transactions")
    fun listTransactions(): List<TransactionRecord> = transactionService.listTransactions()

    @DeleteMapping("/transactions/reset")
    fun resetTransactions(@RequestParam(defaultValue = "false") confirm: Boolean): ResponseEntity<Map<String, Int>> {
        if (!confirm) {
            return ResponseEntity.badRequest().body(mapOf("deleted" to 0))
        }
        val deleted = transactionService.resetTransactions()
        return ResponseEntity.ok(mapOf("deleted" to deleted))
    }

    @PostMapping("/snapshots")
    fun addSnapshot(@Valid @RequestBody request: AccountBalanceSnapshotRequest): SnapshotRecord = transactionService.addSnapshot(request)

    @DeleteMapping("/snapshots")
    fun deleteSnapshot(@RequestParam id: String): SnapshotRecord = transactionService.deleteSnapshot(id)

    @GetMapping("/snapshots")
    fun listSnapshots(): List<SnapshotRecord> = transactionService.listSnapshots()

    @PostMapping("/account-positions/monthly")
    fun addMonthlyAccountPosition(
        @Valid @RequestBody request: MonthlyAccountPositionRequest
    ): AccountPositionSnapshotResponse = transactionService
        .addMonthlyAccountPosition(request)
        .toResponse()

    @GetMapping("/account-positions")
    fun listMonthlyAccountPositions(): List<AccountPositionSnapshotResponse> = transactionService
        .listMonthlyAccountPositions()
        .map { it.toResponse() }

    @GetMapping("/summary/spending")
    fun summary(): List<SummaryPoint> = transactionService.monthlyCategorySummary()

    @PostMapping("/import/finanzguru", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFinanzguru(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(defaultValue = ";") delimiter: String,
        @RequestParam(defaultValue = "false") decimalComma: Boolean,
        @RequestParam(defaultValue = "dd.MM.yyyy") datePattern: String,
        @RequestParam(defaultValue = "Buchungstag") dateColumn: String,
        @RequestParam(defaultValue = "Konto") accountColumn: String,
        @RequestParam(defaultValue = "Betrag") amountColumn: String,
        @RequestParam(defaultValue = "Kontostand") accountStateColumn: String,
        @RequestParam(defaultValue = "Verwendungszweck") descriptionColumn: String,
        @RequestParam(defaultValue = "Kategorie") categoryColumn: String
    ): ImportResult {
        return transactionService.importFinanzguru(
            bytes = file.bytes,
            delimiter = delimiter.firstOrNull() ?: ';',
            decimalComma = decimalComma,
            datePattern = datePattern,
            dateColumn = dateColumn,
            accountStateColumn = accountStateColumn,
            descriptionColumn = descriptionColumn,
            amountColumn = amountColumn,
            categoryColumn = categoryColumn,
            accountColumn = accountColumn
        )
    }

    private val svgMediaType = MediaType.parseMediaType("image/svg+xml")

    @GetMapping("/charts/spending.svg", produces = ["image/svg+xml"])
    fun spendingChart(
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) dpi: Int?,
        @RequestParam(defaultValue = "true") stacked: Boolean,
        @RequestParam(required = false) months: Int?
    ): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(svgMediaType)
            .body(chartService.spendingChartSvg(chartOptions(width, height, dpi), stacked, months))

    @GetMapping("/charts/balance.svg", produces = ["image/svg+xml"])
    fun balanceChart(
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) dpi: Int?
    ): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(svgMediaType)
            .body(chartService.balanceChartSvg(chartOptions(width, height, dpi)))

    @GetMapping("/charts/balance-by-account.svg", produces = ["image/svg+xml"])
    fun balanceByAccountTypeChart(
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) dpi: Int?
    ): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(svgMediaType)
            .body(chartService.balanceByAccountTypeChartSvg(chartOptions(width, height, dpi)))

    private fun chartOptions(width: Int?, height: Int?, dpi: Int?) =
        ChartRenderOptions.from(width, height, dpi)

    private fun com.financeangle.dashboard.model.AccountPositionSnapshotRecord.toResponse() =
        AccountPositionSnapshotResponse(
            snapshotDate = month.atDay(1),
            accounts = accounts,
            savingsBudget = savingsBudget,
            totals = totals
        )
}
