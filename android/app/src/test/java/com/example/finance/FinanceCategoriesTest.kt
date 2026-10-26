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

    private fun expense(merchant: String, detail: String = "POS Zahlung") = FinanceTransaction(
        merchant = merchant,
        originalMerchant = merchant,
        detail = detail,
        dateLabel = "01.01.2026",
        amountCents = -500,
        category = "Ausgang"
    )
}
