package com.example.finance

import android.content.Context
import java.util.Locale

enum class TransactionCategory(val label: String) {
    Income("Einnahme"),
    Parents("Eltern"),
    Housing("Wohnen"),
    Groceries("Lebensmittel"),
    Drugstore("Drogerie"),
    Mobility("Mobilität"),
    Subscriptions("Abos"),
    Restaurants("Restaurants"),
    Shopping("Shopping"),
    Health("Gesundheit"),
    Leisure("Freizeit"),
    Cash("Bargeld"),
    Taxes("Steuern & Gebühren"),
    Other("Sonstiges")
}

object FinanceCategories {
    private val groceryPattern = Regex("(?iu)(?<!\\p{L})(?:interspar|intersp\\.?|spar|billa|penny|hofer|lidl|aldi|netto)(?!\\p{L})")
    private val drugstorePattern = Regex("(?iu)(?<!\\p{L})(?:bipa|dm)(?!\\p{L})|drogerie|drogeriemarkt")
    private val restaurantPattern = Regex(
        "(?iu)mcdonald|burger king|(?<!\\p{L})kfc(?!\\p{L})|kebap|kebab|" +
            "restaurant|pizzeria|pizza|sushi|lieferando|cafe|café|coffee|caterei|bortolotti|bongiorno"
    )
    private val monthlySubscriptionPattern = Regex(
        "(?iu)spotify|netflix|disney\\+|disney plus|youtube premium|apple music|google one|" +
            "icloud\\+?|prime video|amazon prime|microsoft|msbill\\.info|office 365|chatgpt plus"
    )

    fun suggest(transaction: FinanceTransaction): TransactionCategory {
        if (transaction.amountCents > 0) return TransactionCategory.Income
        val text = "${transaction.merchant} ${transaction.detail}".lowercase(Locale.ROOT)

        return when {
            transaction.category == "Wohnen" || listOf("husleje", "miete", "wohnung", "electricity", "strom")
                .any { it in text } || transaction.merchant.equals("El", ignoreCase = true) -> TransactionCategory.Housing
            groceryPattern.containsMatchIn(text) || transaction.category == "Lebensmittel" ||
                listOf("føtex", "rema 1000", "sunu.dk", "supermarkt", "supermarket", "bageriet")
                    .any { it in text } || transaction.merchant.equals("Mad", ignoreCase = true) -> TransactionCategory.Groceries
            drugstorePattern.containsMatchIn(text) -> TransactionCategory.Drugstore
            transaction.category == "Transport" || listOf("wiener linien", "öbb", "oebb", "dsb", "parking", "tankstelle", "uber", "bolt")
                .any { it in text } -> TransactionCategory.Mobility
            monthlySubscriptionPattern.containsMatchIn(text) ||
                transaction.merchant.equals("TV", ignoreCase = true) -> TransactionCategory.Subscriptions
            restaurantPattern.containsMatchIn(text) -> TransactionCategory.Restaurants
            transaction.category == "Shopping" || listOf("lookfantastic", "viori", "amazon", "zalando", "aliexpress", "thalia", "trafik", "m & s")
                .any { it in text } -> TransactionCategory.Shopping
            listOf("optik", "apotheke", "pharmacy", "arzt", "doctor", "dentist")
                .any { it in text } -> TransactionCategory.Health
            transaction.category == "Freizeit" || listOf("kino", "cinema", "steamgames", "steam purchase", "g2a.com")
                .any { it in text } -> TransactionCategory.Leisure
            listOf("bargeldbehebung", "bankomat", "atm ").any { it in text } -> TransactionCategory.Cash
            "kest" in text -> TransactionCategory.Taxes
            else -> TransactionCategory.Other
        }
    }

    fun displaySender(merchant: String): String = merchant
        .replace(Regex("(?iu)^(?:echtzeitgutschrift|gutschrift)\\s+a(?:/|\\s)+"), "")
        .replace(Regex("(?iu)\\s+(?:AT\\d{18}|ref:).*$"), "")
        .trim()
        .ifBlank { merchant }

    fun senderKey(merchant: String): String = displaySender(merchant).lowercase(Locale.ROOT)
        .replace(Regex("\\s+at\\s+ref.*$"), "")
        .replace(Regex("\\d+"), " ")
        .replace(Regex("[^\\p{L}]+"), " ")
        .trim()

    fun displayMerchant(merchant: String): String {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            "google one" in value -> "Google One"
            "microsoft" in value -> "Microsoft"
            "spotify" in value -> "Spotify"
            "netflix" in value -> "Netflix"
            "steam" in value -> "Steam"
            "g2a.com" in value -> "G2A"
            "mcdonald" in value -> "McDonald's"
            "burger king" in value -> "Burger King"
            Regex("(?iu)(?<!\\p{L})kfc(?!\\p{L})").containsMatchIn(value) -> "KFC"
            "lieferando" in value -> "Lieferando"
            "mevlana kebap" in value -> "Mevlana Kebap"
            "cafe+co" in value -> "Cafe+Co"
            "oebb" in value || "öbb" in value -> "ÖBB"
            "aliexpress" in value -> "AliExpress"
            "thalia" in value -> "Thalia"
            "hofer" in value -> "HOFER"
            Regex("(?iu)(?<!\\p{L})bipa(?!\\p{L})").containsMatchIn(value) -> "BIPA"
            Regex("(?iu)(?<!\\p{L})dm(?!\\p{L})").containsMatchIn(value) -> "dm"
            Regex("(?iu)(?<!\\p{L})billa(?!\\p{L})").containsMatchIn(value) -> "BILLA"
            Regex("(?iu)(?<!\\p{L})(?:interspar|intersp\\.?)(?!\\p{L})").containsMatchIn(value) -> "INTERSPAR"
            Regex("(?iu)(?<!\\p{L})spar(?!\\p{L})").containsMatchIn(value) -> "SPAR"
            Regex("(?iu)(?<!\\p{L})penny(?!\\p{L})").containsMatchIn(value) -> "PENNY"
            "bargeldbehebung" in value -> "Bargeldbehebung"
            else -> merchant.take(48).trim()
        }
    }

    fun recurringMerchantKey(merchant: String): String {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            "google one" in value -> "google one"
            "microsoft" in value -> "microsoft"
            else -> senderKey(merchant)
        }
    }

    fun apply(
        overview: FinanceOverview,
        overrides: Map<String, TransactionCategory>,
        parentSenders: Set<String> = emptySet()
    ): FinanceOverview {
        val categorized = overview.transactions.map { transaction ->
            val category = overrides[transaction.identity]
                ?: if (transaction.amountCents > 0 && senderKey(transaction.merchant) in parentSenders) {
                    TransactionCategory.Parents
                } else {
                    suggest(transaction)
                }
            transaction.copy(category = category.label)
        }

        return overview.copy(transactions = categorized)
    }

    fun choices(transaction: FinanceTransaction): List<TransactionCategory> =
        if (transaction.amountCents > 0) listOf(TransactionCategory.Income, TransactionCategory.Parents)
        else TransactionCategory.entries.filter { it !in setOf(TransactionCategory.Income, TransactionCategory.Parents) }
}

class CategoryOverrideStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_categories_v1", Context.MODE_PRIVATE)
    private val parentPreferences = context.getSharedPreferences("saldo_parent_senders_v1", Context.MODE_PRIVATE)

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

    fun loadParentSenders(): Set<String> = parentPreferences.all.keys.map(FinanceCategories::senderKey).toSet()

    fun saveParentSender(merchant: String, enabled: Boolean) {
        val key = FinanceCategories.senderKey(merchant)
        if (key.isBlank()) return
        parentPreferences.edit().apply {
            parentPreferences.all.keys.filter { FinanceCategories.senderKey(it) == key }.forEach { remove(it) }
            if (enabled) putBoolean(key, true) else remove(key)
        }.apply()
    }
}

class RecurringVisibilityStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_recurring_hidden_v1", Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.all.keys

    fun hide(key: String) {
        preferences.edit().putBoolean(key, true).apply()
    }

    fun restore(prefix: String) {
        preferences.edit().apply {
            preferences.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
    }
}
