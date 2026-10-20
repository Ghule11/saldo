package com.example.finance

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class TransactionCategory(val label: String) {
    Income("Einnahme"),
    Parents("Eltern"),
    Housing("Wohnen"),
    Groceries("Lebensmittel"),
    Drugstore("Drogerie"),
    Mobility("Mobilität"),
    Subscriptions("Abos"),
    Shopping("Shopping"),
    Health("Gesundheit"),
    Leisure("Freizeit"),
    Other("Sonstiges")
}

object FinanceCategories {
    private val groceryPattern = Regex("(?iu)(?<!\\p{L})(?:interspar|spar|billa|penny|hofer|lidl|aldi|netto)(?!\\p{L})")
    private val drugstorePattern = Regex("(?iu)(?<!\\p{L})(?:bipa|dm)(?!\\p{L})|drogerie|drogeriemarkt")
    private val monthlySubscriptionPattern = Regex(
        "(?iu)spotify|netflix|disney\\+|disney plus|youtube premium|apple music|google one|" +
            "icloud\\+?|prime video|amazon prime|microsoft 365|office 365|chatgpt plus"
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
            transaction.category == "Transport" || listOf("wiener linien", "öbb", "dsb", "parking", "tankstelle", "uber", "bolt")
                .any { it in text } -> TransactionCategory.Mobility
            monthlySubscriptionPattern.containsMatchIn(text) ||
                transaction.merchant.equals("TV", ignoreCase = true) -> TransactionCategory.Subscriptions
            transaction.category == "Shopping" || listOf("lookfantastic", "viori", "amazon", "zalando")
                .any { it in text } -> TransactionCategory.Shopping
            listOf("optik", "apotheke", "pharmacy", "arzt", "doctor", "dentist")
                .any { it in text } -> TransactionCategory.Health
            transaction.category == "Freizeit" || listOf("restaurant", "café", "kino", "cinema", "burger king", "mcdonald")
                .any { it in text } -> TransactionCategory.Leisure
            else -> TransactionCategory.Other
        }
    }

    fun senderKey(merchant: String): String = merchant.lowercase(Locale.ROOT)
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
            Regex("(?iu)(?<!\\p{L})bipa(?!\\p{L})").containsMatchIn(value) -> "BIPA"
            Regex("(?iu)(?<!\\p{L})dm(?!\\p{L})").containsMatchIn(value) -> "dm"
            Regex("(?iu)(?<!\\p{L})billa(?!\\p{L})").containsMatchIn(value) -> "BILLA"
            Regex("(?iu)(?<!\\p{L})interspar(?!\\p{L})").containsMatchIn(value) -> "INTERSPAR"
            Regex("(?iu)(?<!\\p{L})spar(?!\\p{L})").containsMatchIn(value) -> "SPAR"
            Regex("(?iu)(?<!\\p{L})penny(?!\\p{L})").containsMatchIn(value) -> "PENNY"
            else -> merchant
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

        val recurringSubscriptionIds = categorized
            .filter { it.amountCents < 0 && it.category in setOf(TransactionCategory.Other.label, TransactionCategory.Shopping.label) }
            .groupBy { recurringMerchantKey(it.merchant) }
            .filterKeys { it.length >= 3 }
            .values
            .flatMap { sameMerchant ->
                sameMerchant.groupBy { -it.amountCents }.values
                    .filter(::occursMonthly)
                    .flatten()
                    .map { it.identity }
            }
            .toSet()

        return overview.copy(transactions = categorized.map { transaction ->
            if (transaction.identity in recurringSubscriptionIds && transaction.identity !in overrides) {
                transaction.copy(category = TransactionCategory.Subscriptions.label)
            } else {
                transaction
            }
        })
    }

    private fun occursMonthly(transactions: List<FinanceTransaction>): Boolean {
        val dates = transactions.mapNotNull { transaction ->
            transaction.bookingDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        }.sorted()
        if (dates.map { YearMonth.from(it) }.distinct().size < 2) return false
        return dates.zipWithNext().any { (first, second) -> ChronoUnit.DAYS.between(first, second) in 24..38 }
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

    fun loadParentSenders(): Set<String> = parentPreferences.all.keys

    fun saveParentSender(merchant: String, enabled: Boolean) {
        val key = FinanceCategories.senderKey(merchant)
        if (key.isBlank()) return
        parentPreferences.edit().apply {
            if (enabled) putBoolean(key, true) else remove(key)
        }.apply()
    }
}
