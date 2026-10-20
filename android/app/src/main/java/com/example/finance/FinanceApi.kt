package com.example.finance

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
    val transactions: List<FinanceTransaction>
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
        val connection = URL("${BuildConfig.FINANCE_API_BASE_URL}/api/overview")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Das Backend antwortet mit HTTP $status.")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).toFinanceOverview()
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
            transactions = transactions
        )
    }
}
