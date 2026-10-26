package com.example.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCategoriesTest {
    @Test
    fun knownPayeesFromStatementAreCategorized() {
        val cases = mapOf(
            "ETSAN 2362" to TransactionCategory.Groceries,
            "BAECKEREI 3156" to TransactionCategory.Groceries,
            "MUELLER WIEN 1-2 ECC" to TransactionCategory.Drugstore,
            "YUMMY NOODLE" to TransactionCategory.Restaurants,
            "FENOMEN 2371" to TransactionCategory.Restaurants,
            "LIBRO FIL.406" to TransactionCategory.Shopping,
            "PULL&BEAR 10983" to TransactionCategory.Shopping,
            "OMV 7547" to TransactionCategory.Mobility,
            "BAD HIETZING 3147" to TransactionCategory.Leisure,
            "MARGARETEN - APO. OG" to TransactionCategory.Health,
            "Barauszahlung Filiale Beim Karlsplatz" to TransactionCategory.Cash
        )
        cases.forEach { (merchant, expected) ->
            assertEquals(merchant, expected, FinanceCategories.suggest(expense(merchant)))
        }
    }

    @Test
    fun unknownPayeesAndTransferNotesStayUnclassified() {
        assertEquals(TransactionCategory.Other, FinanceCategories.suggest(expense("SUMUP")))
        assertEquals(
            TransactionCategory.Leisure,
            FinanceCategories.suggest(expense("SUMUP", "POS Zahlung SUMUP *FRIENDLY FIRE WIEN"))
        )
        assertEquals(TransactionCategory.Other, FinanceCategories.suggest(expense("AUTOMAT 00029423")))
        assertEquals(
            TransactionCategory.Other,
            FinanceCategories.suggest(expense("Privatperson", "Echtzeitüberweisung zG/Privatperson · BILLA"))
        )
    }

    @Test
    fun cashDepositIsNotMonthlyIncome() {
        val deposit = FinanceTransaction(
            merchant = "Bareinzahlung",
            detail = "Einzahlung Automat",
            dateLabel = "01.01.2026",
            bookingDate = "2026-01-01",
            amountCents = 2_500,
            category = "Eingang"
        )
        val income = deposit.copy(merchant = "Gutschrift", detail = "Überweisung", amountCents = 5_000)
        val overview = FinanceOverview(
            dataSource = "local_statement",
            month = "Januar 2026",
            accountInstitution = "Bank Austria",
            accountName = "Konto",
            currency = "EUR",
            balanceCents = 7_500,
            incomeCents = 0,
            expensesCents = 0,
            spending = emptyList(),
            transactions = listOf(deposit, income)
        )
        val categorized = FinanceCategories.apply(overview, emptyMap())

        assertEquals(TransactionCategory.Cash.label, categorized.transactions.first().category)
        assertEquals(5_000L, FinanceInsights.months(categorized).single().incomeCents)
    }

    @Test
    fun cardNarrativeShowsPayeeWithoutLosingOriginalText() {
        assertEquals("7154 WIEN", FinanceCategories.displayMerchant(
            "POS 2,93 AT D21 24.10. 17:52 O 7154 WIEN WIEN 1070"
        ))
        assertEquals("7154 WIEN", FinanceCategories.displayMerchant(
            "POS 2,93 AT D21 24.10. 17:52 O 7154 WIEN WIEN"
        ))
        assertEquals("PENNY", FinanceCategories.displayMerchant(
            "POS 2,48 AT D21 20.10. 10:41 O PENNY DANKT 7800565 WIEN 1050"
        ))
    }

    private fun expense(merchant: String, detail: String = "POS Zahlung") = FinanceTransaction(
        merchant = merchant,
        originalMerchant = merchant,
        detail = detail,
        dateLabel = "01.01.2026",
        amountCents = -500,
        category = "Ausgang"
    )
}
