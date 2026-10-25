package com.example.finance

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceAnalysisTest {
    private var nextId = 0

    @Test
    fun forecastCountsConfirmedSubscriptionOnceAndCapsExceptionalWeek() {
        val history = (7..9).flatMap { month ->
            val period = YearMonth.of(2026, month)
            val groceries = (1..period.lengthOfMonth()).map { day ->
                transaction(period.atDay(day), "BILLA", -100, "Lebensmittel")
            }
            groceries + transaction(period.atDay(1), "Eltern", 5_000, "Eltern") +
                transaction(period.atDay(12), "Microsoft", -200, "Abos")
        } + transaction(LocalDate.of(2026, 8, 5), "Einmaliger Kauf", -100_000, "Shopping")
        val current = listOf(
            transaction(LocalDate.of(2026, 10, 4), "BILLA", -1_000, "Lebensmittel"),
            transaction(LocalDate.of(2026, 10, 5), "Einzahlung Automat", 10_000, "Einnahme"),
            transaction(LocalDate.of(2026, 10, 6), "Umbuchung", -5_000, "Sonstiges"),
            transaction(LocalDate.of(2026, 10, 26), "Schon gemeldet", -293, "Shopping"),
            transaction(LocalDate.of(2026, 11, 1), "Erst nächsten Monat", -5_000, "Shopping")
        )
        val report = FinanceAnalysis.calculate(
            overview(history + current),
            mapOf("microsoft" to SubscriptionDecision.Confirmed),
            emptySet(), false, LocalDate.of(2026, 10, 15)
        )

        assertEquals(1_293L, report.forecast.spentCents)
        assertEquals(200L, report.forecast.upcomingFixedCents)
        assertEquals(100L, report.forecast.variableDailyCents)
        assertEquals(5_000L, report.forecast.expectedIncomeCents)
        assertEquals(3_093L, report.forecast.projectedCents)
        assertEquals(1_907L, report.forecast.differenceCents)
        assertEquals(ForecastSignal.Tight, report.forecast.signal)
    }

    @Test
    fun exclusionsAndReservesChangeOnlyDailyRoom() {
        val coverage = (1..12).map { distance ->
            transaction(YearMonth.of(2026, 10).minusMonths(distance.toLong()).atDay(1),
                "BILLA", -100, "Lebensmittel")
        }
        val history = (7..9).flatMap { month ->
            val period = YearMonth.of(2026, month)
            (1..period.lengthOfMonth()).map { day ->
                transaction(period.atDay(day), "BILLA", -100, "Lebensmittel")
            } + transaction(period.atDay(2), "Eltern", 5_000, "Eltern")
        } + listOf(
            transaction(LocalDate.of(2024, 3, 10), "Versicherung", -12_000, "Sonstiges"),
            transaction(LocalDate.of(2025, 3, 10), "Versicherung", -12_000, "Sonstiges"),
            transaction(LocalDate.of(2026, 3, 10), "Versicherung", -12_000, "Sonstiges"),
            transaction(LocalDate.of(2025, 12, 20), "Geschenk", -6_000, "Geschenke")
        )
        val excluded = transaction(LocalDate.of(2026, 10, 3), "Sonderfall", -4_000, "Shopping")
        val current = listOf(
            excluded,
            transaction(LocalDate.of(2026, 10, 4), "BILLA", -1_000, "Lebensmittel"),
            transaction(LocalDate.of(2026, 10, 5), "Kleines Geschenk", -500, "Geschenke")
        )
        val input = overview(coverage + history + current)
        val withoutReserves = FinanceAnalysis.calculate(input, emptyMap(), setOf(excluded.identity),
            false, LocalDate.of(2026, 10, 15))
        val withReserves = FinanceAnalysis.calculate(input, emptyMap(), setOf(excluded.identity),
            true, LocalDate.of(2026, 10, 15))

        assertEquals(1_500L, withoutReserves.forecast.spentCents)
        assertEquals(1_500L, withReserves.reserves.monthlyCents)
        assertEquals(1_000L, withReserves.reserves.remainingThisMonthCents)
        assertEquals(withoutReserves.forecast.projectedCents, withReserves.forecast.projectedCents)
        assertEquals(withoutReserves.forecast.dailyRoomCents!! - 62L,
            withReserves.forecast.dailyRoomCents)
        assertTrue(withReserves.reserves.entries.any { it.name == "Geschenke" })
        assertTrue(withReserves.reserves.entries.any { it.name == "Versicherung" })
    }

    @Test
    fun seasonalityNeedsTwoCompleteYearsForLabels() {
        val full = (0 until 24).flatMap { offset ->
            val month = YearMonth.of(2026, 9).minusMonths(offset.toLong())
            val regular = transaction(month.atDay(8), "BILLA", -1_000, "Lebensmittel")
            if (month.monthValue == 12) listOf(
                regular, transaction(month.atDay(20), "Geschenk", -3_000, "Geschenke")
            ) else listOf(regular)
        }
        val today = LocalDate.of(2026, 10, 15)
        val all = FinanceAnalysis.calculate(overview(full), emptyMap(), emptySet(), false, today)
        val oneYear = FinanceAnalysis.calculate(overview(full.take(12)), emptyMap(),
            emptySet(), false, today)

        assertTrue(all.seasonality.canClassify)
        assertEquals(12, all.seasonality.months.size)
        val december = all.seasonality.months.first { it.month == 12 }
        assertEquals(SeasonalSignal.Save, december.signal)
        assertTrue(december.drivers.any { it.category == "Geschenke" })
        assertFalse(oneYear.seasonality.canClassify)
        assertNull(oneYear.seasonality.months.firstOrNull { it.month == 12 }?.signal)
    }

    @Test
    fun forecastRequiresCompleteHistoryAndHandlesLastDay() {
        val onlyCurrent = overview(listOf(
            transaction(LocalDate.of(2026, 10, 31), "BILLA", -500, "Lebensmittel")
        ))
        val forecast = FinanceAnalysis.calculate(onlyCurrent, emptyMap(), emptySet(),
            false, LocalDate.of(2026, 10, 31)).forecast
        assertEquals(0, forecast.remainingDays)
        assertNull(forecast.projectedCents)
        assertNull(forecast.dailyRoomCents)
    }

    private fun transaction(
        date: LocalDate, merchant: String, amount: Long, category: String
    ): FinanceTransaction {
        nextId++
        return FinanceTransaction(
            merchant = merchant,
            detail = "",
            dateLabel = date.toString(),
            amountCents = amount,
            category = category,
            bookingDate = date.toString(),
            identity = "test-" + nextId
        )
    }

    private fun overview(transactions: List<FinanceTransaction>) = FinanceOverview(
        dataSource = "banking_production",
        month = "Bankdaten",
        accountInstitution = "Bank Austria",
        accountName = "Mein Konto",
        currency = "EUR",
        balanceCents = 0,
        incomeCents = 0,
        expensesCents = 0,
        spending = emptyList(),
        transactions = transactions
    )
}
