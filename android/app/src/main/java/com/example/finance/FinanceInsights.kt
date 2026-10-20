package com.example.finance

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MonthlySnapshot(
    val key: String,
    val label: String,
    val shortLabel: String,
    val incomeCents: Long,
    val outcomeCents: Long,
    val transferInCents: Long,
    val transferOutCents: Long,
    val transactions: List<FinanceTransaction>
) {
    val netCents: Long get() = incomeCents - outcomeCents
}

data class MerchantOutflow(val name: String, val amountCents: Long)
data class RecurringExpense(val name: String, val amountCents: Long, val monthCount: Int)

object FinanceInsights {
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
    private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN)

    fun months(overview: FinanceOverview): List<MonthlySnapshot> {
        if (!overview.dataSource.startsWith("banking_")) {
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
            incomeCents = transactions.filter { it.amountCents > 0 && it.category != TransactionCategory.Transfer.label }.sumOf { it.amountCents },
            outcomeCents = transactions.filter { it.amountCents < 0 && it.category != TransactionCategory.Transfer.label }.sumOf { -it.amountCents },
            transferInCents = transactions.filter { it.amountCents > 0 && it.category == TransactionCategory.Transfer.label }.sumOf { it.amountCents },
            transferOutCents = transactions.filter { it.amountCents < 0 && it.category == TransactionCategory.Transfer.label }.sumOf { -it.amountCents },
            transactions = transactions
        )

    fun topOutflows(month: MonthlySnapshot, limit: Int = 5): List<MerchantOutflow> =
        month.transactions
            .filter { it.amountCents < 0 && it.category != TransactionCategory.Transfer.label }
            .groupBy { it.merchant }
            .map { (name, transactions) -> MerchantOutflow(name, transactions.sumOf { -it.amountCents }) }
            .sortedByDescending { it.amountCents }
            .take(limit)

    fun expenseCategories(month: MonthlySnapshot): List<SpendingCategory> {
        val amounts = month.transactions
            .filter { it.amountCents < 0 && it.category != TransactionCategory.Transfer.label }
            .groupBy { it.category }
            .map { (name, items) -> name to items.sumOf { -it.amountCents } }
            .sortedByDescending { it.second }
        val maximum = amounts.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
        return amounts.map { (name, amount) -> SpendingCategory(name, amount, amount.toFloat() / maximum) }
    }

    fun recurringExpenses(overview: FinanceOverview): List<RecurringExpense> = overview.transactions
        .filter { it.amountCents < 0 && it.category in setOf(TransactionCategory.Housing.label, TransactionCategory.Subscriptions.label) }
        .mapNotNull { transaction ->
            val month = try { YearMonth.from(LocalDate.parse(transaction.bookingDate)) } catch (_: Exception) { null }
            month?.let { Triple(transaction.merchant.trim().lowercase(Locale.ROOT), it, transaction) }
        }
        .groupBy { it.first }
        .values
        .mapNotNull { entries ->
            val months = entries.map { it.second }.distinct()
            if (months.size < 3) null else {
                val latest = entries.maxBy { it.second }
                RecurringExpense(latest.third.merchant, -latest.third.amountCents, months.size)
            }
        }
        .sortedWith(compareByDescending<RecurringExpense> { it.monthCount }.thenByDescending { it.amountCents })
        .take(5)
}
