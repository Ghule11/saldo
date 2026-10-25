package com.example.finance

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

enum class ForecastSignal { InLimit, Tight, Over }
enum class SeasonalSignal { Save, Neutral, Room }

data class UpcomingFixedCost(val name: String, val amountCents: Long)

data class MonthlyForecast(
    val spentCents: Long,
    val upcomingFixedCents: Long,
    val fixedCosts: List<UpcomingFixedCost>,
    val variableDailyCents: Long?,
    val remainingDays: Int,
    val incomeBasisCents: Long?,
    val projectedCents: Long?,
    val differenceCents: Long?,
    val dailyRoomCents: Long?,
    val signal: ForecastSignal?,
    val historyMonths: Int,
    val reservesIncluded: Boolean,
    val reserveDeductionCents: Long
)

data class ReserveEntry(
    val key: String,
    val name: String,
    val period: String,
    val yearlyCents: Long,
    val monthlyCents: Long,
    val remainingThisMonthCents: Long
)

data class ReserveSummary(
    val entries: List<ReserveEntry>,
    val monthlyCents: Long,
    val remainingThisMonthCents: Long
)

data class SeasonalDriver(
    val category: String,
    val deviationPercent: Int,
    val differenceCents: Long,
    val averageCents: Long,
    val baselineCents: Long
)
data class SeasonalMonth(
    val month: Int,
    val deviationPercent: Int,
    val signal: SeasonalSignal?,
    val drivers: List<SeasonalDriver>
)

data class SeasonalitySummary(
    val historyMonths: Int,
    val months: List<SeasonalMonth>,
    val canClassify: Boolean = false,
    val otherSharePercent: Int = 0
) {
    val hasEnoughHistory: Boolean get() = historyMonths >= 12
}

data class FinanceAnalysisReport(
    val forecast: MonthlyForecast,
    val reserves: ReserveSummary,
    val seasonality: SeasonalitySummary
)

object FinanceAnalysis {
    private data class Dated(val transaction: FinanceTransaction, val date: LocalDate) {
        val month: YearMonth get() = YearMonth.from(date)
    }

    private val transferPattern = Regex(
        "(?iu)(?<!\\p{L})(?:umbuchung|eigenübertrag|eigenuebertrag|kontoübertrag|" +
            "kontouebertrag|übertrag eigenes konto|uebertrag eigenes konto)(?!\\p{L})"
    )
    private val cashDepositPattern = Regex("(?iu)bareinzahlung|einzahlung automat")
    private val seasonalCategories = setOf(TransactionCategory.Gifts.label, TransactionCategory.Travel.label)

    fun calculate(
        overview: FinanceOverview,
        subscriptionDecisions: Map<String, SubscriptionDecision>,
        excludedIdentities: Set<String>,
        includeReserves: Boolean,
        today: LocalDate,
        hiddenIncomeKeys: Set<String> = emptySet()
    ): FinanceAnalysisReport {
        val currentMonth = YearMonth.from(today)
        val dated = overview.transactions.mapNotNull { transaction ->
            val date = transaction.bookingDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            date?.takeIf { YearMonth.from(it) <= currentMonth }?.let { Dated(transaction, it) }
        }.filterNot { it.transaction.identity in excludedIdentities || isOwnTransfer(it.transaction) }
        val confirmed = subscriptionDecisions.filterValues { it == SubscriptionDecision.Confirmed }.keys
        val regularIncome = FinanceInsights.recurringIncome(
            overview.copy(transactions = dated.map { it.transaction })
        ).filterNot { it.key in hiddenIncomeKeys }.sumOf { it.amountCents }
        val reserves = reserves(dated, confirmed, YearMonth.from(today))
        return FinanceAnalysisReport(
            forecast(dated, confirmed, reserves, includeReserves, regularIncome, today),
            reserves,
            seasonality(dated, confirmed, YearMonth.from(today))
        )
    }

    private fun forecast(
        dated: List<Dated>,
        confirmed: Set<String>,
        reserves: ReserveSummary,
        includeReserves: Boolean,
        regularIncomeCents: Long,
        today: LocalDate
    ): MonthlyForecast {
        val current = YearMonth.from(today)
        val spent = dated.filter { it.month == current && it.transaction.amountCents < 0 }
            .sumOf { -it.transaction.amountCents }
        val fixed = confirmed.mapNotNull { key ->
            val entries = dated.filter {
                it.transaction.amountCents < 0 && FinanceCategories.merchantKey(it.transaction.merchant) == key
            }
            val latest = entries.maxByOrNull { it.date } ?: return@mapNotNull null
            if (entries.any { it.month == current } ||
                ChronoUnit.DAYS.between(latest.date, today) > 62) return@mapNotNull null
            UpcomingFixedCost(latest.transaction.merchant, -latest.transaction.amountCents)
        }.sortedBy { it.name }
        val upcomingFixed = fixed.sumOf { it.amountCents }
        val historicalMonths = mutableListOf<YearMonth>()
        var candidate = current.minusMonths(1)
        while (historicalMonths.size < 6 && dated.any { it.month == candidate }) {
            historicalMonths += candidate
            candidate = candidate.minusMonths(1)
        }
        val firstHistoricalDay = historicalMonths.lastOrNull()?.atDay(1)
        val variableEntries = dated.filter {
            firstHistoricalDay != null && !it.date.isBefore(firstHistoricalDay) &&
                it.date.isBefore(current.atDay(1)) && it.transaction.amountCents < 0 &&
                FinanceCategories.merchantKey(it.transaction.merchant) !in confirmed
        }
        val dailyRate = if (historicalMonths.size >= 3 && variableEntries.size >= 10) {
            val byDay = variableEntries.groupBy { it.date }
                .mapValues { (_, entries) -> entries.sumOf { -it.transaction.amountCents } }
            val days = generateSequence(firstHistoricalDay) { it.plusDays(1) }
                .takeWhile { it.isBefore(current.atDay(1)) }
                .map { byDay[it] ?: 0L }.toList()
            val weekly = days.chunked(7).filter { it.size == 7 }.map { it.sum() }
            if (weekly.count { it > 0 } < 4) null
            else {
                val cap = percentile95(weekly)
                if (cap == 0L) null
                else (weekly.sumOf { it.coerceAtMost(cap) }.toDouble() / weekly.size / 7).roundToLong()
            }
        } else null
        val actualIncome = dated.filter { it.month == current && it.transaction.amountCents > 0 &&
            !isCashDeposit(it.transaction) }.sumOf { it.transaction.amountCents }
        val incomeBasis = maxOf(actualIncome, regularIncomeCents).takeIf { it > 0 }
        val remainingDays = current.lengthOfMonth() - today.dayOfMonth
        val projected = dailyRate?.let { spent + upcomingFixed + it * remainingDays }
        val difference = if (projected != null && incomeBasis != null) incomeBasis - projected else null
        val reserveDeduction = if (includeReserves) reserves.remainingThisMonthCents else 0L
        val dailyRoom = if (dailyRate != null && incomeBasis != null && remainingDays > 0)
            (incomeBasis - spent - upcomingFixed - reserveDeduction) / remainingDays else null
        val signal = if (difference == null || incomeBasis == null) null
            else when {
                difference < 0 -> ForecastSignal.Over
                difference < maxOf(2_000L, incomeBasis / 10) -> ForecastSignal.Tight
                else -> ForecastSignal.InLimit
            }
        return MonthlyForecast(
            spent, upcomingFixed, fixed, dailyRate, remainingDays, incomeBasis,
            projected, difference, dailyRoom, signal, historicalMonths.size,
            includeReserves, reserveDeduction
        )
    }

    private fun reserves(dated: List<Dated>, confirmed: Set<String>, current: YearMonth): ReserveSummary {
        val expenses = dated.filter { it.transaction.amountCents < 0 }
        val merchantEntries = expenses.filter {
            it.transaction.category !in seasonalCategories &&
                it.transaction.category != TransactionCategory.Cash.label &&
                FinanceCategories.merchantKey(it.transaction.merchant) !in confirmed
        }.groupBy { FinanceCategories.merchantKey(it.transaction.merchant) }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (key, entries) ->
                val monthly = entries.groupBy { it.month }.toSortedMap()
                    .map { (month, items) -> month to items.sumOf { -it.transaction.amountCents } }
                if (monthly.size < 2) return@mapNotNull null
                val intervals = monthly.zipWithNext { a, b ->
                    ChronoUnit.MONTHS.between(a.first, b.first).toInt()
                }
                val periodMonths = when {
                    monthly.size >= 3 && intervals.all { it in 2..4 } -> 3
                    monthly.size >= 3 && intervals.all { it in 5..7 } -> 6
                    intervals.all { it in 11..13 } -> 12
                    else -> return@mapNotNull null
                }
                if (ChronoUnit.MONTHS.between(monthly.last().first, current) > periodMonths + 1)
                    return@mapNotNull null
                val amounts = monthly.map { it.second }
                val typical = median(amounts)
                if (typical <= 0 || amounts.any { it < typical / 2 || it > typical * 2 })
                    return@mapNotNull null
                val yearly = typical * (12 / periodMonths)
                if (yearly < 3_000) return@mapNotNull null
                val thisMonth = monthly.firstOrNull { it.first == current }?.second ?: 0L
                val latest = entries.maxBy { it.date }
                reserveEntry("merchant:$key", latest.transaction.merchant, periodMonths, yearly, thisMonth)
            }
        val fullHistoryMonths = (1..12).count { distance ->
            dated.any { it.month == current.minusMonths(distance.toLong()) }
        } == 12
        val seasonalEntries = seasonalCategories.mapNotNull { category ->
            if (!fullHistoryMonths) return@mapNotNull null
            val pastYear = expenses.filter {
                it.transaction.category == category && it.month < current &&
                    it.month >= current.minusMonths(12)
            }
            if (pastYear.isEmpty()) return@mapNotNull null
            val yearly = pastYear.sumOf { -it.transaction.amountCents }
            if (yearly < 3_000) return@mapNotNull null
            val thisMonth = expenses.filter { it.transaction.category == category && it.month == current }
                .sumOf { -it.transaction.amountCents }
            reserveEntry("category:$category", category, 12, yearly, thisMonth)
        }
        val all = (merchantEntries + seasonalEntries).sortedByDescending { it.monthlyCents }
        return ReserveSummary(all, all.sumOf { it.monthlyCents },
            all.sumOf { it.remainingThisMonthCents })
    }

    private fun reserveEntry(
        key: String, name: String, periodMonths: Int, yearly: Long, paidThisMonth: Long
    ): ReserveEntry {
        val monthly = (yearly + 6) / 12
        val period = when (periodMonths) {
            3 -> "Quartalsweise"
            6 -> "Halbjährlich"
            else -> "Jährlich"
        }
        return ReserveEntry(key, name, period, yearly, monthly,
            (monthly - paidThisMonth).coerceAtLeast(0))
    }

    private fun seasonality(
        dated: List<Dated>, confirmed: Set<String>, current: YearMonth
    ): SeasonalitySummary {
        val first = dated.minOfOrNull { it.month } ?: return SeasonalitySummary(0, emptyList())
        val completeMonths = generateSequence(current.minusMonths(1)) { it.minusMonths(1) }
            .takeWhile { it >= first }.take(36).toList().reversed()
        val populated = completeMonths.count { month -> dated.any { it.month == month } }
        if (completeMonths.size < 12 || populated < 12 ||
            populated.toDouble() / completeMonths.size < 0.9) {
            return SeasonalitySummary(populated, emptyList())
        }
        val variable = dated.filter {
            it.month in completeMonths && it.transaction.amountCents < 0 &&
                it.transaction.category != TransactionCategory.Cash.label &&
                FinanceCategories.merchantKey(it.transaction.merchant) !in confirmed
        }
        val byMonth = variable.groupBy { it.month }
        val categories = variable.map { it.transaction.category }.toSet()
        val categoryBaseline = categories.associateWith { category ->
            variable.filter { it.transaction.category == category }
                .sumOf { -it.transaction.amountCents }.toDouble() / completeMonths.size
        }
        val overallBaseline = categoryBaseline.values.sum()
        if (overallBaseline <= 0) return SeasonalitySummary(populated, emptyList())
        val totalVariable = variable.sumOf { -it.transaction.amountCents }
        val otherVariable = variable.filter { it.transaction.category == TransactionCategory.Other.label }
            .sumOf { -it.transaction.amountCents }
        val otherSharePercent = (otherVariable * 100 / totalVariable).toInt()
        val classifiable = populated >= 24 &&
            (1..12).all { month -> completeMonths.count { it.monthValue == month &&
                dated.any { entry -> entry.month == it } } >= 2 }
        val months = (1..12).map { calendarMonth ->
            val years = completeMonths.filter { it.monthValue == calendarMonth }
            val monthAverage = years.sumOf { month ->
                byMonth[month].orEmpty().sumOf { -it.transaction.amountCents }
            }.toDouble() / years.size
            val percent = ((monthAverage / overallBaseline - 1) * 100).roundToLong().toInt()
            val drivers = categories.mapNotNull { category ->
                val baseline = categoryBaseline[category] ?: return@mapNotNull null
                val average = years.sumOf { month ->
                    byMonth[month].orEmpty().filter { it.transaction.category == category }
                        .sumOf { -it.transaction.amountCents }
                }.toDouble() / years.size
                val difference = (average - baseline).roundToLong()
                if (abs(difference) < 500 || baseline < 100) null
                else SeasonalDriver(category, ((average / baseline - 1) * 100).roundToLong().toInt(),
                    difference, average.roundToLong(), baseline.roundToLong())
            }.sortedByDescending { abs(it.differenceCents) }
            SeasonalMonth(calendarMonth, percent,
                if (!classifiable) null else when {
                    percent > 10 -> SeasonalSignal.Save
                    percent < -10 -> SeasonalSignal.Room
                    else -> SeasonalSignal.Neutral
                }, drivers)
        }
        return SeasonalitySummary(populated, months, classifiable, otherSharePercent)
    }

    private fun isOwnTransfer(transaction: FinanceTransaction): Boolean =
        transferPattern.containsMatchIn(
            transaction.merchant + " " + transaction.detail + " " + transaction.category
        )

    private fun isCashDeposit(transaction: FinanceTransaction): Boolean =
        cashDepositPattern.containsMatchIn(transaction.merchant + " " + transaction.detail)

    private fun percentile95(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * 0.95).toInt()]
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return 0
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
            else (sorted[middle - 1].toDouble() / 2 + sorted[middle].toDouble() / 2).roundToLong()
    }
}
