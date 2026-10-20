package com.example.finance

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FinanceOverview(
    val dataSource: String,
    val month: String,
    val accountInstitution: String,
    val accountName: String,
    val currency: String,
    val balanceCents: Long,
    val incomeCents: Long,
    val expensesCents: Long,
    val spending: List<SpendingCategory>,
    val transactions: List<FinanceTransaction>,
    val hasMoreTransactions: Boolean = false
)

data class SpendingCategory(
    val name: String,
    val amountCents: Long,
    val fraction: Float
)

data class FinanceTransaction(
    val merchant: String,
    val detail: String,
    val dateLabel: String,
    val amountCents: Long,
    val category: String
)

object FinanceApi {
    private const val TIMEOUT_MILLIS = 5_000

    fun fetchOverview(): FinanceOverview {
        return getJson("/api/overview").toFinanceOverview()
    }

    fun fetchDashboardOverview(): FinanceOverview {
        val status = getJson("/api/banking/status")
        if (status.optBoolean("isSandbox") && status.optInt("accountCount") > 0) {
            return getJson("/api/banking/data").toBankingOverview()
        }

        return fetchOverview()
    }

    private fun getJson(path: String): JSONObject {
        val connection = URL("${BuildConfig.FINANCE_API_BASE_URL}$path")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Die Finanzdaten konnten nicht geladen werden (HTTP $status).")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toFinanceOverview(): FinanceOverview {
        val spendingJson = getJSONArray("spending")
        val spending = (0 until spendingJson.length()).map { index ->
            val item = spendingJson.getJSONObject(index)
            SpendingCategory(
                name = item.getString("name"),
                amountCents = item.getLong("amountCents"),
                fraction = item.getDouble("fraction").toFloat()
            )
        }

        val transactionsJson = getJSONArray("transactions")
        val transactions = (0 until transactionsJson.length()).map { index ->
            val item = transactionsJson.getJSONObject(index)
            FinanceTransaction(
                merchant = item.getString("merchant"),
                detail = item.getString("detail"),
                dateLabel = item.getString("dateLabel"),
                amountCents = item.getLong("amountCents"),
                category = item.getString("category")
            )
        }

        return FinanceOverview(
            dataSource = getString("dataSource"),
            month = getString("month"),
            accountInstitution = getString("accountInstitution"),
            accountName = getString("accountName"),
            currency = getString("currency"),
            balanceCents = getLong("balanceCents"),
            incomeCents = getLong("incomeCents"),
            expensesCents = getLong("expensesCents"),
            spending = spending,
            transactions = transactions,
            hasMoreTransactions = false
        )
    }

    private fun JSONObject.toBankingOverview(): FinanceOverview {
        val accounts = optJSONArray("accounts")
            ?: throw IOException("Enable Banking hat keine Kontodaten zurückgegeben.")
        val account = (0 until accounts.length())
            .mapNotNull { accounts.optJSONObject(it) }
            .firstOrNull()
            ?: throw IOException("Enable Banking hat kein Konto zurückgegeben.")

        val balances = account.optJSONObject("balances")?.optJSONArray("balances")
        val balance = balances?.let { items ->
            (0 until items.length())
                .mapNotNull { items.optJSONObject(it) }
                .firstOrNull { it.optString("balance_type") in setOf("ITAV", "CLAV", "ITBD") }
                ?: (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull()
        }

        val transactionsResponse = account.optJSONObject("transactions")
        val transactionItems = transactionsResponse?.optJSONArray("transactions")
        val transactions = transactionItems?.let { items ->
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val amount = item.optJSONObject("transaction_amount")
                    ?.optString("amount")
                    ?.toMinorUnits()
                    ?: 0L
                val direction = item.optString("credit_debit_indicator")
                val signedAmount = if (direction == "CRDT") kotlin.math.abs(amount) else -kotlin.math.abs(amount)
                val remittance = item.optJSONArray("remittance_information")
                    ?.optString(0)
                    ?.takeUnless { it.isNullOrBlank() || it == "null" }
                val merchant = listOf("creditor", "debtor")
                    .firstNotNullOfOrNull { item.optJSONObject(it)?.optString("name")?.cleanValue() }
                    ?: remittance?.replace(Regex("\\s+"), " ")
                    ?: "Umsatz"
                val displayMerchant = merchant
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("[()]+$"), "")
                    .replace(Regex("\\s+\\d{4,}$"), "")
                    .trim()

                FinanceTransaction(
                    merchant = displayMerchant.ifBlank { "Umsatz" },
                    detail = remittance ?: item.optJSONObject("bank_transaction_code")?.optString("code").cleanValue().orEmpty(),
                    dateLabel = item.optString("booking_date").toGermanDate(),
                    amountCents = signedAmount,
                    category = if (direction == "CRDT") "Eingang" else "Ausgang"
                )
            }
        }.orEmpty()

        val balanceAmount = balance?.optJSONObject("balance_amount")
        val currency = balanceAmount?.optString("currency")
            ?.cleanValue()
            ?: transactionItems?.optJSONObject(0)?.optJSONObject("transaction_amount")?.optString("currency")?.cleanValue()
            ?: "DKK"
        val amountCents = balanceAmount?.optString("amount")?.toMinorUnits() ?: 0L
        val continuationKey = transactionsResponse?.optString("continuation_key")
        val year = transactionItems?.optJSONObject(0)?.optString("booking_date")?.take(4)
            ?.takeIf { it.length == 4 }

        return FinanceOverview(
            dataSource = "banking_sandbox",
            month = year?.let { "Testdaten $it" } ?: "Sandbox-Testdaten",
            accountInstitution = optString("institutionName").cleanValue().orEmpty().ifBlank { "Mock ASPSP" },
            accountName = "Sandbox-Konto",
            currency = currency,
            balanceCents = amountCents,
            incomeCents = 0,
            expensesCents = 0,
            spending = emptyList(),
            transactions = transactions,
            hasMoreTransactions = !continuationKey.isNullOrBlank() && continuationKey != "null"
        )
    }

    private fun String.toMinorUnits(): Long = BigDecimal(this)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

    private fun String?.cleanValue(): String? = this?.takeUnless { it.isBlank() || it == "null" }

    private fun String.toGermanDate(): String = try {
        LocalDate.parse(this).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY))
    } catch (_: Exception) {
        this
    }
}
