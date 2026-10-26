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
    Gifts("Geschenke"),
    Travel("Urlaub"),
    Cash("Bargeld"),
    Taxes("Steuern & Gebühren"),
    Other("Sonstiges")
}

enum class SubscriptionDecision { Confirmed, Rejected }

object FinanceCategories {
    private val namedGroceries = Regex("(?iu)(?<!\\p{L})(?:etsan|b(?:a|ä|ae)ckerei|ankerbrot)(?!\\p{L})")
    private val namedDrugstores = Regex("(?iu)(?<!\\p{L})(?:mueller|müller|parfuemerie douglas|parfümerie douglas)(?!\\p{L})")
    private val namedRestaurants = Regex(
        "(?iu)(?<!\\p{L})(?:yummy noodle|noodle king|soya noodles|fenomen|berliner d[oö]ner|" +
            "vapiano|shodai|sukiyaki|nordsee|subway|chicken plus|modern asia|ferhat d[oö]ner|" +
            "fedis dner|fedis doener|karakulah gastronomie|flos imbiss|cinnamood)(?!\\p{L})"
    )
    private val namedShopping = Regex(
        "(?iu)(?<!\\p{L})(?:libro|new\\s*yorker|pull&bear|bershka|hm at\\d+|c&a store|action|" +
            "media markt|mediam|smyths toys|ikea)(?!\\p{L})"
    )
    private val namedMobility = Regex("(?iu)(?<!\\p{L})(?:omv|wrlinien)(?!\\p{L})")
    private val namedLeisure = Regex("(?iu)(?<!\\p{L})(?:bad hietzing|gaensehaeufe|gänsehäufel|ma44 huet[t]?el|wiener eislauf verein)(?!\\p{L})")
    private val namedHealth = Regex("(?iu)(?<!\\p{L})margareten\\s*-?\\s*apo\\.?(?!\\p{L})")
    private val namedCash = Regex("(?iu)^(?:barauszahlung filiale|abhebung automat)")
    private val groceryPattern = Regex("(?iu)(?<!\\p{L})(?:interspar|intersp\\.?|spar|billa|penny|hofer|lidl|aldi|netto)(?!\\p{L})")
    private val drugstorePattern = Regex("(?iu)(?<!\\p{L})(?:bipa|dm)(?!\\p{L})|drogerie|drogeriemarkt")
    private val restaurantPattern = Regex(
        "(?iu)mcdonald|burger king|(?<!\\p{L})kfc(?!\\p{L})|kebap|kebab|" +
            "restaurant|pizzeria|pizza|sushi|lieferando|cafe|café|coffee|caterei|bortolotti|bongiorno"
    )
    private val monthlySubscriptionPattern = Regex(
        "(?iu)spotify|netflix|disney\\+|disney plus|youtube premium|apple music|google one|discord\\*? nitromonthly|" +
            "icloud\\+?|prime video|amazon prime|microsoft|msbill\\.info|office 365|chatgpt plus"
    )

    fun suggest(transaction: FinanceTransaction, allowSubscriptionSuggestion: Boolean = true): TransactionCategory {
        if (transaction.amountCents > 0) return TransactionCategory.Income
        val text = "${transaction.merchant} ${transaction.detail}".lowercase(Locale.ROOT)
        val payee = "${transaction.merchant} ${transaction.originalMerchant}"

        // The statement only names the payment processor; these submerchants are explicit.
        if (payee.trim().equals("SUMUP SUMUP", ignoreCase = true)) {
            when {
                Regex("(?iu)sumup\\s+\\*friendly fire\\b").containsMatchIn(transaction.detail) ->
                    return TransactionCategory.Leisure
                Regex("(?iu)sumup\\s+\\*loris parfum\\b").containsMatchIn(transaction.detail) ->
                    return TransactionCategory.Drugstore
                Regex("(?iu)sumup\\s+\\*churros und co\\b").containsMatchIn(transaction.detail) ->
                    return TransactionCategory.Restaurants
            }
        }

        when {
            namedGroceries.containsMatchIn(payee) -> return TransactionCategory.Groceries
            namedDrugstores.containsMatchIn(payee) -> return TransactionCategory.Drugstore
            namedRestaurants.containsMatchIn(payee) -> return TransactionCategory.Restaurants
            namedShopping.containsMatchIn(payee) -> return TransactionCategory.Shopping
            namedMobility.containsMatchIn(payee) -> return TransactionCategory.Mobility
            namedLeisure.containsMatchIn(payee) -> return TransactionCategory.Leisure
            namedHealth.containsMatchIn(payee) -> return TransactionCategory.Health
            namedCash.containsMatchIn(payee) -> return TransactionCategory.Cash
        }

        // A private transfer's free-text purpose is not a reliable merchant signal.
        if (Regex("(?iu)^\\s*(?:echtzeitüberweisung|online überweisung|überweisung)\\b")
                .containsMatchIn(transaction.detail)
        ) return TransactionCategory.Other

        return when {
            transaction.category == "Wohnen" || listOf("husleje", "miete", "wohnung", "electricity", "strom")
                .any { it in text } || transaction.merchant.equals("El", ignoreCase = true) -> TransactionCategory.Housing
            groceryPattern.containsMatchIn(text) || transaction.category == "Lebensmittel" ||
                listOf("føtex", "rema 1000", "sunu.dk", "supermarkt", "supermarket", "bageriet")
                    .any { it in text } || transaction.merchant.equals("Mad", ignoreCase = true) -> TransactionCategory.Groceries
            drugstorePattern.containsMatchIn(text) -> TransactionCategory.Drugstore
            transaction.category == "Transport" || listOf("wiener linien", "öbb", "oebb", "dsb", "parking", "tankstelle", "uber", "bolt")
                .any { it in text } -> TransactionCategory.Mobility
            allowSubscriptionSuggestion && (monthlySubscriptionPattern.containsMatchIn(text) ||
                transaction.merchant.equals("TV", ignoreCase = true)) -> TransactionCategory.Subscriptions
            restaurantPattern.containsMatchIn(text) -> TransactionCategory.Restaurants
            transaction.category == "Shopping" || listOf("lookfantastic", "viori", "amazon", "zalando", "aliexpress", "thalia", "trafik", "m & s")
                .any { it in text } -> TransactionCategory.Shopping
            listOf("booking.com", "airbnb", "hotel", "flugreise", "flugticket").any { it in text } ->
                TransactionCategory.Travel
            "geschenk" in text -> TransactionCategory.Gifts
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

    fun merchantKey(merchant: String): String {
        val key = recurringMerchantKey(merchant)
        return key.takeUnless { it.length < 3 || it in setOf("umsatz", "pos", "kartenzahlung", "e comm") }.orEmpty()
    }

    fun apply(
        overview: FinanceOverview,
        overrides: Map<String, TransactionCategory>,
        parentSenders: Set<String> = emptySet(),
        merchantRules: Map<String, TransactionCategory> = emptyMap(),
        subscriptionDecisions: Map<String, SubscriptionDecision> = emptyMap()
    ): FinanceOverview {
        val categorized = overview.transactions.map { transaction ->
            val merchantKey = merchantKey(transaction.merchant)
            val decision = subscriptionDecisions[merchantKey]
            val individualOverride = overrides[transaction.identity].takeUnless {
                it == TransactionCategory.Subscriptions && decision == SubscriptionDecision.Rejected
            }
            val category = individualOverride
                ?: if (transaction.amountCents > 0 && senderKey(transaction.merchant) in parentSenders) {
                    TransactionCategory.Parents
                } else if (transaction.amountCents < 0) {
                    merchantRules[merchantKey] ?: when (decision) {
                        SubscriptionDecision.Confirmed -> TransactionCategory.Subscriptions
                        SubscriptionDecision.Rejected -> suggest(transaction, allowSubscriptionSuggestion = false)
                        null -> suggest(transaction)
                    }
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
    private val merchantPreferences = context.getSharedPreferences("saldo_merchant_rules_v1", Context.MODE_PRIVATE)

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

    fun loadMerchantRules(): Map<String, TransactionCategory> = merchantPreferences.all.mapNotNull { (key, value) ->
        val category = TransactionCategory.entries.firstOrNull { it.name == value }
        category?.let { key to it }
    }.toMap()

    fun saveMerchantRule(merchant: String, category: TransactionCategory?) {
        val key = FinanceCategories.merchantKey(merchant)
        if (key.isBlank()) return
        merchantPreferences.edit().apply {
            if (category == null) remove(key) else putString(key, category.name)
        }.apply()
    }
}

class SubscriptionDecisionStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_subscription_decisions_v1", Context.MODE_PRIVATE)

    fun load(): Map<String, SubscriptionDecision> = preferences.all.mapNotNull { (key, value) ->
        val decision = SubscriptionDecision.entries.firstOrNull { it.name == value }
        decision?.let { key to it }
    }.toMap()

    fun save(merchant: String, decision: SubscriptionDecision?) {
        val key = FinanceCategories.merchantKey(merchant)
        if (key.isBlank()) return
        preferences.edit().apply {
            if (decision == null) remove(key) else putString(key, decision.name)
        }.apply()
    }
}

class RecurringVisibilityStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_recurring_hidden_v1", Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.all.keys

    fun hide(key: String) {
        preferences.edit().putBoolean(key, true).apply()
    }

    fun show(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun restore(prefix: String) {
        preferences.edit().apply {
            preferences.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
    }
}
