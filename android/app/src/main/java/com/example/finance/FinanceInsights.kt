package com.example.finance

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class MonthlySnapshot(
    val key: String,
    val label: String,
    val shortLabel: String,
    val incomeCents: Long,
    val outcomeCents: Long,
    val transactions: List<FinanceTransaction>
) {
    val netCents: Long get() = incomeCents - outcomeCents
}

data class MerchantOutflow(val name: String, val amountCents: Long)
data class RecurringEntry(val name: String, val amountCents: Long, val monthCount: Int)

object FinanceInsights {
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
    private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN)

    fun months(overview: FinanceOverview): List<MonthlySnapshot> {
        if (overview.dataSource == "sample") {
            return listOf(
                snapshot("sample", overview.month, overview.month.take(3) + " " + overview.month.takeLast(2), overview.transactions)
            )
        }

        return overview.transactions
            .mapNotNull { transaction ->
                val month = try {
                    YearMonth.from(LocalDate.parse(transaction.bookingDate))
                } catch (_: Exception) {
                    null
                }
                month?.let { it to transaction }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(reverseOrder())
            .map { (month, transactions) ->
                snapshot(
                    month.toString(),
                    month.format(monthFormat).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    month.format(shortMonthFormat).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    transactions.sortedByDescending { it.bookingDate }
                )
            }
    }

    private fun snapshot(key: String, label: String, shortLabel: String, transactions: List<FinanceTransaction>): MonthlySnapshot =
        MonthlySnapshot(
            key = key,
            label = label,
            shortLabel = shortLabel,
            incomeCents = transactions.filter { it.amountCents > 0 }.sumOf { it.amountCents },
            outcomeCents = transactions.filter { it.amountCents < 0 }.sumOf { -it.amountCents },
            transactions = transactions
        )

    fun topOutflows(month: MonthlySnapshot, limit: Int = 5): List<MerchantOutflow> =
        month.transactions
            .filter { it.amountCents < 0 }
            .groupBy { it.merchant }
            .map { (name, transactions) -> MerchantOutflow(name, transactions.sumOf { -it.amountCents }) }
            .sortedByDescending { it.amountCents }
            .take(limit)

    fun expenseCategories(month: MonthlySnapshot): List<SpendingCategory> {
        val amounts = month.transactions
            .filter { it.amountCents < 0 }
            .groupBy { it.category }
            .map { (name, items) -> name to items.sumOf { -it.amountCents } }
            .sortedByDescending { it.second }
        val maximum = amounts.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
        return amounts.map { (name, amount) -> SpendingCategory(name, amount, amount.toFloat() / maximum) }
    }

    fun subscriptions(overview: FinanceOverview): List<RecurringEntry> = overview.transactions
        .filter { it.amountCents < 0 && it.category == TransactionCategory.Subscriptions.label }
        .groupBy { FinanceCategories.recurringMerchantKey(it.merchant) }
        .filterKeys { it.isNotBlank() }
        .values
        .map { entries ->
            val latest = entries.maxBy { it.bookingDate.orEmpty() }
            RecurringEntry(
                latest.merchant,
                -latest.amountCents,
                entries.mapNotNull { monthOf(it) }.distinct().size
            )
        }
        .sortedWith(compareByDescending<RecurringEntry> { it.monthCount }.thenByDescending { it.amountCents })
        .take(6)

    fun recurringIncome(overview: FinanceOverview): List<RecurringEntry> = overview.transactions
        .filter { it.amountCents > 0 }
        .groupBy { FinanceCategories.senderKey(it.merchant) }
        .filterKeys { it.length >= 3 }
        .values
        .filter { entries ->
            val dates = entries.mapNotNull { dateOf(it) }.sorted()
            dates.map { YearMonth.from(it) }.distinct().size >= 2 &&
                dates.zipWithNext().any { (first, second) -> ChronoUnit.DAYS.between(first, second) in 20..45 }
        }
        .map { entries ->
            val latest = entries.maxBy { it.bookingDate.orEmpty() }
            val name = if (entries.any { it.category == TransactionCategory.Parents.label }) {
                "Eltern · ${latest.merchant}"
            } else {
                latest.merchant
            }
            RecurringEntry(name, latest.amountCents, entries.mapNotNull { monthOf(it) }.distinct().size)
        }
        .sortedWith(compareByDescending<RecurringEntry> { it.monthCount }.thenByDescending { it.amountCents })
        .take(5)

    private fun dateOf(transaction: FinanceTransaction): LocalDate? =
        transaction.bookingDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun monthOf(transaction: FinanceTransaction): YearMonth? = dateOf(transaction)?.let { YearMonth.from(it) }
}
