package com.example.finance

import android.content.Context
import java.util.Locale

enum class TransactionCategory(val label: String) {
    Income("Einnahme"),
    Transfer("Umbuchung"),
    Housing("Wohnen"),
    Groceries("Lebensmittel"),
    Mobility("Mobilität"),
    Subscriptions("Abos"),
    Shopping("Shopping"),
    Health("Gesundheit"),
    Leisure("Freizeit"),
    Other("Sonstiges")
}

object FinanceCategories {
    fun suggest(transaction: FinanceTransaction): TransactionCategory {
        val text = "${transaction.merchant} ${transaction.detail}".lowercase(Locale.ROOT)
        if (listOf("opsparing", "savings", "sparkonto", "eigenübertrag", "eigenes konto")
                .any { it in text }) return TransactionCategory.Transfer
        if (transaction.amountCents > 0) return TransactionCategory.Income

        return when {
            transaction.category == "Wohnen" || listOf("husleje", "miete", "wohnung", "electricity", "strom")
                .any { it in text } || transaction.merchant.equals("El", ignoreCase = true) -> TransactionCategory.Housing
            transaction.category == "Lebensmittel" || listOf("billa", "hofer", "lidl", "aldi", "netto", "føtex", "rema 1000", "sunu.dk", "supermarkt", "supermarket", "bageriet")
                .any { it in text } || transaction.merchant.equals("Mad", ignoreCase = true) -> TransactionCategory.Groceries
            transaction.category == "Transport" || listOf("wiener linien", "öbb", "dsb", "parking", "tankstelle", "uber", "bolt")
                .any { it in text } -> TransactionCategory.Mobility
            listOf("spotify", "netflix", "telmore", "disney+", "youtube premium", "apple music")
                .any { it in text } || transaction.merchant.equals("TV", ignoreCase = true) -> TransactionCategory.Subscriptions
            transaction.category == "Shopping" || listOf("lookfantastic", "dm drogerie", "viori", "amazon", "zalando")
                .any { it in text } -> TransactionCategory.Shopping
            listOf("optik", "apotheke", "pharmacy", "arzt", "doctor", "dentist")
                .any { it in text } -> TransactionCategory.Health
            transaction.category == "Freizeit" || listOf("restaurant", "café", "kino", "cinema")
                .any { it in text } -> TransactionCategory.Leisure
            else -> TransactionCategory.Other
        }
    }

    fun apply(overview: FinanceOverview, overrides: Map<String, TransactionCategory>): FinanceOverview =
        overview.copy(transactions = overview.transactions.map { transaction ->
            transaction.copy(category = (overrides[transaction.identity] ?: suggest(transaction)).label)
        })

    fun choices(transaction: FinanceTransaction): List<TransactionCategory> =
        if (transaction.amountCents > 0) listOf(TransactionCategory.Income, TransactionCategory.Transfer)
        else TransactionCategory.entries.filter { it != TransactionCategory.Income }
}

class CategoryOverrideStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_categories_v1", Context.MODE_PRIVATE)

    fun load(): Map<String, TransactionCategory> = preferences.all.mapNotNull { (key, value) ->
        val category = TransactionCategory.entries.firstOrNull { it.name == value }
        category?.let { key to it }
    }.toMap()

    fun save(identity: String, category: TransactionCategory?) {
        if (identity.isBlank()) return
        preferences.edit().apply {
            if (category == null) remove(identity) else putString(identity, category.name)
        }.apply()
    }
}
