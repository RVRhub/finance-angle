package com.financeangle.app.service

import com.financeangle.app.domain.TransactionEntity
import com.financeangle.app.repository.TransactionRepository
import com.financeangle.app.service.model.SummaryPeriod
import com.financeangle.app.service.model.TransactionCommand
import com.financeangle.app.service.model.TransactionSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(TransactionService::class.java)

    @Transactional
    fun recordTransaction(command: TransactionCommand): TransactionEntity {
        logger.info(
            "Recording transaction amount={} category={} source={}",
            command.amount,
            command.category,
            command.sourceType
        )
        val entity = TransactionEntity(
            amount = command.amount,
            category = command.category,
            occurredAt = command.occurredAt,
            notes = command.notes,
            sourceType = command.sourceType,
            receiptReference = command.receiptReference
        )
        val saved = transactionRepository.save(entity)
        logger.debug("Recorded transaction id={} receiptReference={}", saved.id, saved.receiptReference)
        return saved
    }

    @Transactional(readOnly = true)
    fun generateSummary(period: SummaryPeriod, reference: Instant = Instant.now()): TransactionSummary {
        logger.info("Generating {} transaction summary", period)
        val (start, end) = resolveRange(period, reference)
        logger.debug("Summary window start={} end={}", start, end)
        val transactions = transactionRepository.findAllByOccurredAtBetween(start, end)
        val totalAmount = transactions.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
        val totalsByCategory = transactions
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount } }
        return TransactionSummary(start, end, totalAmount, totalsByCategory)
    }

    private fun resolveRange(period: SummaryPeriod, reference: Instant): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val zonedReference = reference.atZone(zone)
        val (start, endExclusive) = when (period) {
            SummaryPeriod.WEEK -> {
                val startOfWeek = zonedReference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .truncatedTo(ChronoUnit.DAYS)
                val endOfWeekExclusive = startOfWeek.plusWeeks(1)
                startOfWeek to endOfWeekExclusive
            }
            SummaryPeriod.MONTH -> {
                val startOfMonth = zonedReference.with(TemporalAdjusters.firstDayOfMonth())
                    .truncatedTo(ChronoUnit.DAYS)
                val endOfMonthExclusive = startOfMonth.plusMonths(1)
                startOfMonth to endOfMonthExclusive
            }
            SummaryPeriod.QUARTER -> {
                val currentMonth = zonedReference.month
                val firstMonthOfQuarter = Month.of(((currentMonth.value - 1) / 3) * 3 + 1)
                val startOfQuarter = zonedReference
                    .withMonth(firstMonthOfQuarter.value)
                    .with(TemporalAdjusters.firstDayOfMonth())
                    .truncatedTo(ChronoUnit.DAYS)
                val endOfQuarterExclusive = startOfQuarter.plusMonths(3)
                startOfQuarter to endOfQuarterExclusive
            }
            SummaryPeriod.YEAR -> {
                val startOfYear = zonedReference.with(TemporalAdjusters.firstDayOfYear())
                    .truncatedTo(ChronoUnit.DAYS)
                val endOfYearExclusive = startOfYear.plusYears(1)
                startOfYear to endOfYearExclusive
            }
        }
        return start.toInstant() to endExclusive.minusNanos(1).toInstant()
    }
}
