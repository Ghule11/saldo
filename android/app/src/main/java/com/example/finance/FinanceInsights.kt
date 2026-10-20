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
    val transactions: List<FinanceTransaction>
) {
    val netCents: Long get() = incomeCents - outcomeCents
}

data class MerchantOutflow(val name: String, val amountCents: Long)

object FinanceInsights {
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
    private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN)

    fun months(overview: FinanceOverview): List<MonthlySnapshot> {
        if (overview.dataSource != "banking_sandbox") {
            return listOf(
                MonthlySnapshot(
                    key = "sample",
                    label = overview.month,
                    shortLabel = "Okt 26",
                    incomeCents = overview.incomeCents,
                    outcomeCents = overview.expensesCents,
                    transactions = overview.transactions
                )
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
                MonthlySnapshot(
                    key = month.toString(),
                    label = month.format(monthFormat).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    shortLabel = month.format(shortMonthFormat).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    incomeCents = transactions.filter { it.amountCents > 0 }.sumOf { it.amountCents },
                    outcomeCents = transactions.filter { it.amountCents < 0 }.sumOf { -it.amountCents },
                    transactions = transactions.sortedByDescending { it.bookingDate }
                )
            }
    }

    fun topOutflows(month: MonthlySnapshot, limit: Int = 5): List<MerchantOutflow> =
        month.transactions
            .filter { it.amountCents < 0 }
            .groupBy { it.merchant }
            .map { (name, transactions) -> MerchantOutflow(name, transactions.sumOf { -it.amountCents }) }
            .sortedByDescending { it.amountCents }
            .take(limit)
}
